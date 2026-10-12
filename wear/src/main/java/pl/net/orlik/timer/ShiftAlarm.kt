package pl.net.orlik.timer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import pl.net.orlik.data.ActiveMatch

/** Keeps the shift timer alarm working in the background for the match in progress. */
fun interface ShiftAlarm {
    /** Reports a change of the match in progress (null = no match). */
    fun update(match: ActiveMatch?)
}

/** Runs the alarm in [ShiftAlarmService] while the match has the shift timer enabled. */
class ServiceShiftAlarm(private val context: Context) : ShiftAlarm {
    override fun update(match: ActiveMatch?) {
        val intent = Intent(context, ShiftAlarmService::class.java)
        if (match?.shiftTimer != null) {
            // The service re-reads the saved match on every start, so this also delivers the change
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.stopService(intent)
        }
    }
}
