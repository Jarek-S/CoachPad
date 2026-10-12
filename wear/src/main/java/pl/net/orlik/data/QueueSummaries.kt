package pl.net.orlik.data

import pl.net.orlik.shared.EventType
import pl.net.orlik.shared.sync.PendingMatch
import pl.net.orlik.shared.sync.QueueSummary

/** Describes the queue for the phone: one entry per match, oldest first, without any recordings. */
fun queueSummaryOf(pending: List<MatchEvent>): QueueSummary {
    val byMatch = pending.sortedBy { it.recordedAt }.groupBy { it.matchId }
    val matches = byMatch.map { (matchId, events) ->
        PendingMatch(
            matchId = matchId,
            startedAt = (events.firstOrNull { it.type == EventType.MATCH_START } ?: events.first()).recordedAt,
            eventCount = events.size,
            recordingCount = events.count { it.audioFileRef != null },
        )
    }
    return QueueSummary(
        matchInProgress = byMatch.values.any { activeMatchFrom(it) != null },
        matches = matches.sortedBy { it.startedAt },
    )
}

/** Makes the queue summary visible to the phone. */
fun interface QueueSummaryPublisher {
    suspend fun publish(summary: QueueSummary)
}
