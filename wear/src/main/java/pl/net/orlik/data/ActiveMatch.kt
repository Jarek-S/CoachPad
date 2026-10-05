package pl.net.orlik.data

import pl.net.orlik.shared.EventType
import pl.net.orlik.shared.Pause

/** A match that has been started and not yet ended. Timestamps are epoch milliseconds. */
data class ActiveMatch(
    val matchId: String,
    val startedAt: Long,
    val pauses: List<Pause> = emptyList(),
) {
    /** True while the last pause has not been resumed. */
    val isPaused: Boolean
        get() = pauses.lastOrNull()?.let { it.resumedAt == null } ?: false
}

/**
 * Rebuilds the active match from the events of a single match, sorted by time.
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
