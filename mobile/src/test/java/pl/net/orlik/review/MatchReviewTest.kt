package pl.net.orlik.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.ReviewStatus
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType

class MatchReviewTest {

    private fun min(m: Int) = m * 60_000L

    private fun event(
        id: String,
        at: Long,
        type: EventType?,
        team: Team? = null,
        note: String? = null,
        status: ReviewStatus? = null,
    ) = MatchEvent(
        id = id,
        matchId = "m1",
        recordedAt = at,
        type = type,
        audioFileRef = if (type?.isStructural == true) null else "$id.m4a",
        receivedAt = 0,
        team = team,
        rawTranscript = note,
    ).let { if (status != null) it.copy(status = status) else it }

    @Test
    fun minutes_subtractPauses() {
        val review = matchReviewOf(
            listOf(
                event("start", min(0), EventType.MATCH_START),
                event("pause", min(30), EventType.MATCH_PAUSE),
                event("resume", min(45), EventType.MATCH_RESUME),
                event("goal", min(50) + 2_000, EventType.GOAL, Team.HOME),
            ),
        )

        // 50:02 since the start minus the 15-minute pause
        assertEquals(min(35) + 2_000, review.toReview.single().playTimeMillis)
    }

    @Test
    fun structuralEvents_areConfirmed_recordingsWaitForReview() {
        val review = matchReviewOf(
            listOf(
                event("start", 0, EventType.MATCH_START),
                event("rec", min(3), type = null),
                event("end", min(90), EventType.MATCH_END),
            ),
        )

        assertEquals(listOf("rec"), review.toReview.map { it.eventId })
        assertEquals(listOf("start", "end"), review.confirmed.map { it.eventId })
    }

    @Test
    fun deletedEvents_areLeftOut() {
        val review = matchReviewOf(
            listOf(
                event("start", 0, EventType.MATCH_START),
                event("mistake", min(3), type = null, status = ReviewStatus.DELETED),
            ),
        )

        assertEquals(emptyList<ReviewRow>(), review.toReview)
    }

    @Test
    fun hints_explainWhatIsMissing() {
        val rows = matchReviewOf(
            listOf(
                event("start", 0, EventType.MATCH_START),
                event("undescribed", min(1), type = null),
                event("card", min(2), EventType.YELLOW_CARD),
                event("goal", min(3), EventType.GOAL, Team.AWAY),
                event("note", min(4), EventType.NOTE, note = "Zmiana: Kuba za Marcina"),
            ),
        ).toReview.associateBy { it.eventId }

        assertEquals(ReviewHint.DESCRIBE, rows.getValue("undescribed").hint)
        assertEquals(ReviewHint.MISSING_TEAM, rows.getValue("card").hint)
        assertNull(rows.getValue("goal").hint)
        // A note has no team
        assertNull(rows.getValue("note").hint)
    }

    @Test
    fun blankNote_isNoNote() {
        val row = matchReviewOf(listOf(event("rec", 0, type = null, note = "  "))).toReview.single()
        assertNull(row.note)
    }

    @Test
    fun withoutMatchStart_minutesCountFromOldestEvent() {
        val review = matchReviewOf(listOf(event("rec", min(5), type = null), event("rec2", min(7), type = null)))

        assertEquals(listOf(0L, min(2)), review.toReview.map { it.playTimeMillis })
    }

    @Test
    fun teamName_trimmedAndBlankIsNone() {
        assertEquals("Orły Kraków", teamNameOf("  Orły   Kraków "))
        assertNull(teamNameOf("   "))
    }

    @Test
    fun formats() {
        assertEquals("41:02", formatPlayTime(min(41) + 2_400))
        assertEquals("93:15", formatPlayTime(min(93) + 15_000))
        assertEquals("0:06", formatDuration(5_600))
        assertEquals("1:05", formatDuration(65_000))
    }
}
