package pl.net.orlik.data

import pl.net.orlik.timer.ShiftTimer

/**
 * Shift timer of the match in progress, kept so that an app restart restores it instead of
 * re-reading the (possibly changed) settings. Local to the watch, never synced (docs section 8).
 */
interface MatchTimerStore {
    /** Timer saved for [matchId]; null when the timer is disabled or nothing was saved for that match. */
    fun load(matchId: String): ShiftTimer?

    /** Saves [timer] for [matchId] (null = timer disabled), replacing any earlier match. */
    fun save(matchId: String, timer: ShiftTimer?)

    fun clear()
}
