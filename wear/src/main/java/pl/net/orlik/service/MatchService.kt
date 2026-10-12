package pl.net.orlik.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.net.orlik.R
import pl.net.orlik.data.ActiveMatch
import pl.net.orlik.data.matchRepository
import pl.net.orlik.presentation.MainActivity
import pl.net.orlik.shared.playTimeMillis
import pl.net.orlik.timer.ShiftTimerState

/** Alarm repeats every 5 s until the substitution is confirmed (docs section 8). */
private const val ALARM_REPEAT_MILLIS = 5_000L

/** Two short pulses: a single one is easy to miss. */
private val ALARM_PATTERN = longArrayOf(0, 300, 150, 300)

/** Safety net: the wake lock is released even if the service is never told the match ended. */
private const val MAX_WAKE_LOCK_MILLIS = 4 * 60 * 60_000L

/**
 * Foreground service running for the whole match. Its notification is a Wear OS Ongoing Activity,
 * which keeps the match screen on top: waking the watch returns to it instead of the watch face.
 *
 * With the shift timer enabled it also vibrates when the timer runs out, also with the screen off
 * or with another app on screen. It then holds a partial wake lock while the match is running,
 * so the CPU keeps counting with the screen off; during a pause the timer stands still, so the
 * lock is released.
 * Each start re-reads the match from storage — that is how the app reports start, pause,
 * resume and confirmation, and how the service recovers after its process was killed.
 */
class MatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var alarmJob: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    /** Long.MIN_VALUE / 2: none yet, so the first alarm vibrates at once (and no overflow below). */
    private var lastVibrationAt = Long.MIN_VALUE / 2
    private val vibrator by lazy { getSystemService(Vibrator::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MatchNotes:shiftAlarm")
            .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (e: Exception) {
            // E.g. a restart while the app is in the background; the app starts us again when opened
            Log.w(TAG, "Cannot start in the foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        alarmJob?.cancel()
        alarmJob = scope.launch { runAlarm(matchRepository(this@MatchService).findActiveMatch()) }
        return START_STICKY
    }

    private suspend fun runAlarm(match: ActiveMatch?) {
        if (match == null) {
            stopSelf()
            return
        }
        hideAlarmNotification()
        val timer = match.shiftTimer
        if (timer == null || match.isPaused) {
            // Nothing to count. A pause mutes the alarm but keeps its state; resuming starts this service again
            vibrator.cancel()
            releaseWakeLock()
            return
        }
        wakeLock.acquire(MAX_WAKE_LOCK_MILLIS)
        val state = timer.stateAt(playTimeMillis(System.currentTimeMillis(), match.startedAt, match.pauses))
        when (state) {
            is ShiftTimerState.Running -> delay(state.remainingMillis)
            // Re-armed during the alarm (e.g. the app was reopened): keep the 5 s rhythm
            else -> delay(lastVibrationAt + ALARM_REPEAT_MILLIS - SystemClock.elapsedRealtime())
        }
        showAlarmNotification()
        while (true) {
            vibrate()
            delay(ALARM_REPEAT_MILLIS)
        }
    }

    private fun vibrate() {
        lastVibrationAt = SystemClock.elapsedRealtime()
        val effect = VibrationEffect.createWaveform(ALARM_PATTERN, -1)
        // Alarm usage: not muted by Do Not Disturb
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    /**
     * The persistent notification of the service, also published as a Wear OS Ongoing Activity:
     * an icon on the watch face, and waking the watch returns to the match screen.
     */
    private fun notification(): Notification {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.match_channel))
                .build(),
        )
        val openApp = openMatchScreen(turnScreenOn = false)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ball)
            .setContentTitle(getString(R.string.match_in_progress))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        OngoingActivity.Builder(this, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_ball)
            .setTouchIntent(openApp)
            .setStatus(Status.Builder().addTemplate(getString(R.string.match_in_progress)).build())
            .build()
            .apply(this)
        return builder.build()
    }

    /**
     * When the alarm starts and the match screen is not in view (screen off, watch face or another
     * app), turns the screen on with the match screen, so the confirm button is one tap away.
     * Done by a full-screen notification: a background service may not open an activity directly.
     */
    private fun showAlarmNotification() {
        val appInView = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (appInView && getSystemService(PowerManager::class.java).isInteractive) return
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(ALARM_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(getString(R.string.shift_alarm_channel))
                // The service vibrates by itself, in its own rhythm
                .setVibrationEnabled(false)
                .setSound(null, null)
                .build(),
        )
        val showMatch = openMatchScreen(turnScreenOn = true)
        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(getString(R.string.confirm_shift))
            .setContentIntent(showMatch)
            .setFullScreenIntent(showMatch, true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(ALARM_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot show the alarm notification", e)
        }
    }

    private fun hideAlarmNotification() {
        NotificationManagerCompat.from(this).cancel(ALARM_NOTIFICATION_ID)
    }

    /** Brings the existing match screen to the front instead of opening a second one. */
    private fun openMatchScreen(turnScreenOn: Boolean): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_TURN_SCREEN_ON, turnScreenOn)
        return PendingIntent.getActivity(
            this, if (turnScreenOn) 1 else 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onDestroy() {
        scope.cancel()
        vibrator.cancel()
        releaseWakeLock()
        hideAlarmNotification()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MatchService"
        const val CHANNEL_ID = "match"
        const val NOTIFICATION_ID = 1
        const val ALARM_CHANNEL_ID = "shift_alarm"
        const val ALARM_NOTIFICATION_ID = 2
    }
}
