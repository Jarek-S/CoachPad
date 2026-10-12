package pl.net.orlik.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import pl.net.orlik.data.ActiveMatch

/** Keeps [MatchService] in line with the match in progress. */
fun interface MatchServiceControl {
    /** Reports a change of the match in progress (null = no match). */
    fun update(match: ActiveMatch?)
}

/** Runs [MatchService] for as long as a match is in progress. */
class AndroidMatchServiceControl(private val context: Context) : MatchServiceControl {
    override fun update(match: ActiveMatch?) {
        val intent = Intent(context, MatchService::class.java)
        if (match != null) {
            // The service re-reads the saved match on every start, so this also delivers the change
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.stopService(intent)
        }
    }
}
