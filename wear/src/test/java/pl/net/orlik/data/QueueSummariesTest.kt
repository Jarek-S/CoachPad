package pl.net.orlik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.net.orlik.shared.EventType
import pl.net.orlik.shared.sync.PendingMatch
import pl.net.orlik.shared.sync.QueueSummary

class QueueSummariesTest {

    @Test
    fun emptyQueue_hasNoMatches() {
        assertEquals(QueueSummary(matchInProgress = false, matches = emptyList()), queueSummaryOf(emptyList()))
    }

    @Test
    fun endedMatches_areListedOldestFirst_withCounts() {
        val queue = listOf(
            event("m2", 5_000, EventType.MATCH_START),
            event("m2", 6_000, EventType.MATCH_END),
            event("m1", 1_000, EventType.MATCH_START),
            recording("m1", 1_500),
            recording("m1", 2_000),
            event("m1", 3_000, EventType.MATCH_END),
        )

        assertEquals(
            QueueSummary(
                matchInProgress = false,
                matches = listOf(PendingMatch("m1", 1_000, 4, 2), PendingMatch("m2", 5_000, 2, 0)),
            ),
            queueSummaryOf(queue),
        )
    }

    @Test
    fun matchInProgress_isReported_alsoDuringPause() {
        val queue = listOf(
            event("m1", 1_000, EventType.MATCH_START),
            event("m1", 2_000, EventType.MATCH_END),
            event("m2", 5_000, EventType.MATCH_START),
            event("m2", 6_000, EventType.MATCH_PAUSE),
        )
        assertTrue(queueSummaryOf(queue).matchInProgress)
    }

    @Test
    fun partlySyncedMatch_usesOldestRemainingEvent() {
        // MATCH_START already reached the phone before the sync was interrupted
        val queue = listOf(
            recording("m1", 2_000),
            event("m1", 3_000, EventType.MATCH_END),
        )

        val summary = queueSummaryOf(queue)
        assertFalse(summary.matchInProgress)
        assertEquals(listOf(PendingMatch("m1", 2_000, 2, 1)), summary.matches)
    }

    private var nextId = 0

    private fun event(matchId: String, at: Long, type: EventType) =
        MatchEvent("e${nextId++}", matchId, at, type, audioFileRef = null)

    private fun recording(matchId: String, at: Long) =
        MatchEvent("e${nextId++}", matchId, at, type = null, audioFileRef = "r$nextId.m4a")
}
