package pl.net.orlik.data

import pl.net.orlik.shared.EventType
import pl.net.orlik.shared.Pause
import pl.net.orlik.timer.ShiftTimer

/**
 * A match that has been started and not yet ended. Timestamps are epoch milliseconds.
 * [shiftTimer] is null when the shift timer is disabled for this match.
 */
data class ActiveMatch(
    val matchId: String,
    val startedAt: Long,
    val pauses: List<Pause> = emptyList(),
    val shiftTimer: ShiftTimer? = null,
) {
    /** True while the last pause has not been resumed. */
    val isPaused: Boolean
        get() = pauses.lastOrNull()?.let { it.resumedAt == null } ?: false
}

/**
 * Rebuilds the active match from the events of a single match, sorted by time.
 * The shift timer is not part of the events; it is restored separately ([MatchTimerStore]).
 * Returns null when the match has no MATCH_START or has already ended.
 */
fun activeMatchFrom(events: List<MatchEvent>): ActiveMatch? {
    val start = events.firstOrNull { it.type == EventType.MATCH_START } ?: return null
    if (events.any { it.type == EventType.MATCH_END }) return null

    val pauses = mutableListOf<Pause>()
    for (event in events) {
        when (event.type) {
            EventType.MATCH_PAUSE -> pauses += Pause(pausedAt = event.recordedAt)
            EventType.MATCH_RESUME -> {
                val last = pauses.lastOrNull()
                if (last != null && last.resumedAt == null) {
                    pauses[pauses.lastIndex] = last.copy(resumedAt = event.recordedAt)
                }
            }
            else -> Unit
        }
    }
    return ActiveMatch(matchId = start.matchId, startedAt = start.recordedAt, pauses = pauses)
}
