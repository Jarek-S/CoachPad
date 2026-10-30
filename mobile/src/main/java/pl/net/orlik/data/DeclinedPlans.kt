package pl.net.orlik.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Matches from the watch whose review no longer offers the planned match of their day: "Nie łącz"
 * (design doc, section 11). Kept in the app's settings, not the database; a planned match can
 * still be taken from the lineups.
 */
class DeclinedPlans private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _matchIds = MutableStateFlow(prefs.getStringSet(MATCH_IDS, null).orEmpty().toSet())
    val matchIds: StateFlow<Set<String>> = _matchIds.asStateFlow()

    fun decline(matchId: String) {
        val ids = _matchIds.value + matchId
        prefs.edit().putStringSet(MATCH_IDS, ids).apply()
        _matchIds.value = ids
    }

    companion object {
        private const val PREFS = "declined_plans"
        private const val MATCH_IDS = "match_ids"

        @Volatile private var instance: DeclinedPlans? = null

        fun get(context: Context): DeclinedPlans =
            instance ?: synchronized(this) { instance ?: DeclinedPlans(context).also { instance = it } }
    }
}
