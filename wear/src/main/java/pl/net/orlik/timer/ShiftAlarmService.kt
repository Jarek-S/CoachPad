package pl.net.orlik.timer

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

/** Alarm repeats every 5 s until the substitution is confirmed (docs section 8). */
private const val ALARM_REPEAT_MILLIS = 5_000L

/** Two short pulses: a single one is easy to miss. */
private val ALARM_PATTERN = longArrayOf(0, 300, 150, 300)

/** Safety net: the wake lock is released even if the service is never told the match ended. */
private const val MAX_WAKE_LOCK_MILLIS = 4 * 60 * 60_000L

/**
 * Foreground service that vibrates when the shift timer runs out, also with the screen off
 * or with another app on screen. Runs only during a match with the shift timer enabled.
 *
 * Holds a partial wake lock while the match is running, so the CPU keeps counting with the
 * screen off; during a pause the timer stands still, so the lock is released.
 * Each start re-reads the match from storage — that is how the app reports start, pause,
 * resume and confirmation, and how the service recovers after its process was killed.
 */
class ShiftAlarmService : Service() {

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
        alarmJob = scope.launch { runAlarm(matchRepository(this@ShiftAlarmService).findActiveMatch()) }
        return START_STICKY
    }

    private suspend fun runAlarm(match: ActiveMatch?) {
        val timer = match?.shiftTimer
        if (timer == null) {
            stopSelf()
            return
        }
        if (match.isPaused) {
            // A pause mutes the alarm but keeps its state; resuming starts this service again
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

    private fun notification(): Notification {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.match_channel))
                .build(),
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_flag)
            .setContentTitle(getString(R.string.match_in_progress))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        vibrator.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ShiftAlarmService"
        const val CHANNEL_ID = "match"
        const val NOTIFICATION_ID = 1
    }
}
