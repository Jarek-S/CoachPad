package pl.net.orlik.data

import android.content.Context
import pl.net.orlik.timer.ShiftTimer

/** [MatchTimerStore] backed by SharedPreferences. */
class PreferencesMatchTimerStore(context: Context) : MatchTimerStore {

    private val prefs = context.getSharedPreferences("active_match", Context.MODE_PRIVATE)

    override fun load(matchId: String): ShiftTimer? {
        if (prefs.getString(KEY_MATCH_ID, null) != matchId || !prefs.contains(KEY_INTERVAL)) return null
        return ShiftTimer(
            intervalMillis = prefs.getLong(KEY_INTERVAL, 0),
            lastResetPlayMillis = prefs.getLong(KEY_LAST_RESET, 0),
        )
    }

    override fun save(matchId: String, timer: ShiftTimer?) {
        val editor = prefs.edit().clear().putString(KEY_MATCH_ID, matchId)
        if (timer != null) {
            editor.putLong(KEY_INTERVAL, timer.intervalMillis)
                .putLong(KEY_LAST_RESET, timer.lastResetPlayMillis)
        }
        editor.apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_MATCH_ID = "match_id"
        const val KEY_INTERVAL = "shift_timer_interval_millis"
        const val KEY_LAST_RESET = "shift_timer_last_reset_play_millis"
    }
}
