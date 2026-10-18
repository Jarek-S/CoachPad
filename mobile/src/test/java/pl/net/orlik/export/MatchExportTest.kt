package pl.net.orlik.export

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.ReviewStatus
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType

class MatchExportTest {

    private val zone = ZoneId.of("Europe/Warsaw")

    /** 2026-10-11 14:00:03 in Warsaw: the kick-off. */
    private val kickOff = LocalDateTime.of(2026, 10, 11, 14, 0, 3).atZone(zone).toInstant().toEpochMilli()

    private fun min(m: Int) = kickOff + m * 60_000L

    private val match = Match(id = "m1", homeTeam = "Orły", awayTeam = "Rywale FC")

    private fun event(
        id: String,
        at: Long,
        type: EventType?,
        team: Team? = null,
        note: String? = null,
        status: ReviewStatus = ReviewStatus.CONFIRMED,
        linkedGoal: String? = null,
    ) = MatchEvent(
        id = id,
        matchId = "m1",
        recordedAt = at,
        type = type,
        audioFileRef = if (type?.isStructural == true) null else "$id.m4a",
        receivedAt = 0,
        team = team,
        rawTranscript = note,
        status = status,
        linkedGoalEventId = linkedGoal,
    )

    private fun export(vararg events: MatchEvent) = matchExportOf(match, events.toList())

    @Test
    fun onlyConfirmedEventsGoOut_unreviewedAreCounted() {
        val export = export(
            event("start", min(0), EventType.MATCH_START),
            event("goal", min(12), EventType.GOAL, Team.HOME),
            event("rec", min(20), type = null, status = ReviewStatus.TO_REVIEW),
            event("card", min(25), EventType.YELLOW_CARD, team = null, status = ReviewStatus.TO_REVIEW),
            event("gone", min(30), EventType.NOTE, status = ReviewStatus.DELETED),
            event("end", min(90), EventType.MATCH_END),
        )

        assertEquals(listOf("start", "goal", "end"), export.events.map { it.id })
        assertEquals(2, export.unreviewed)
    }

    @Test
    fun matchTimesAndPlayTime_subtractPauses() {
        val export = export(
            event("start", min(0), EventType.MATCH_START),
            event("pause", min(30), EventType.MATCH_PAUSE),
            event("resume", min(45), EventType.MATCH_RESUME),
            event("goal", min(50) + 2_000, EventType.GOAL, Team.HOME),
            event("end", min(80), EventType.MATCH_END),
        )

        assertEquals(min(0), export.startedAt)
        assertEquals(min(80), export.endedAt)
        assertEquals(35 * 60_000L + 2_000, export.events.single { it.id == "goal" }.playTimeMillis)
    }

    @Test
    fun withoutStructuralEvents_matchTimesAreMissing() {
        val export = export(event("goal", min(12), EventType.GOAL, Team.AWAY))

        assertNull(export.startedAt)
        assertNull(export.endedAt)
        assertEquals(0, export.events.single().playTimeMillis)
    }

    @Test
    fun score_leavesOutCancelledGoals() {
        val export = export(
            event("start", min(0), EventType.MATCH_START),
            event("h1", min(12), EventType.GOAL, Team.HOME),
            event("a1", min(38), EventType.GOAL, Team.AWAY),
            event("no", min(41), EventType.GOAL_DISALLOWED, Team.AWAY, note = "Spalony", linkedGoal = "a1"),
            event("h2", min(66), EventType.GOAL, Team.HOME),
            event("card", min(70), EventType.RED_CARD, Team.AWAY),
        )

        assertEquals(Score(home = 2, away = 0), export.score)
        val goal = export.events.single { it.id == "a1" }
        assertFalse(goal.counted)
        assertEquals("no", goal.cancelledBy)
        assertEquals("a1", export.events.single { it.id == "no" }.cancelsGoalId)
    }

    @Test
    fun goalCountsWhenItsDisallowedGoalIsNotExported() {
        val export = export(
            event("a1", min(38), EventType.GOAL, Team.AWAY),
            event("no", min(41), EventType.GOAL_DISALLOWED, Team.AWAY, status = ReviewStatus.TO_REVIEW, linkedGoal = "a1"),
        )

        assertTrue(export.events.single().counted)
        assertEquals(Score(home = 0, away = 1), export.score)
    }

    @Test
    fun goalToReview_doesNotCount_andIsNotCancelled() {
        val export = export(
            event("a1", min(38), EventType.GOAL, Team.AWAY, status = ReviewStatus.TO_REVIEW),
            event("no", min(41), EventType.GOAL_DISALLOWED, Team.AWAY, linkedGoal = "a1"),
        )

        assertNull(export.events.single().cancelsGoalId)
        assertEquals(Score(home = 0, away = 0), export.score)
    }

    @Test
    fun notes_areTrimmed_blankOnesLeftOut() {
        val export = export(
            event("n1", min(5), EventType.NOTE, note = "  zmiana: Kuba za Marcina "),
            event("n2", min(6), EventType.NOTE, note = "   "),
        )

        assertEquals(listOf("zmiana: Kuba za Marcina", null), export.events.map { it.note })
    }

    @Test
    fun players_trimmed_onlyForTypesThatHaveThem() {
        val export = export(
            event("g1", min(12), EventType.GOAL, Team.HOME).copy(playerName = " Marcin ", assistName = "Jurek"),
            event("y1", min(20), EventType.YELLOW_CARD, Team.AWAY).copy(playerName = "Kowal", assistName = "stale"),
            event("n1", min(25), EventType.NOTE).copy(playerName = "stale"),
            event("g2", min(30), EventType.GOAL, Team.AWAY).copy(playerName = "  "),
        )

        assertEquals(listOf("Marcin", "Kowal", null, null), export.events.map { it.player })
        assertEquals(listOf("Jurek", null, null, null), export.events.map { it.assist })
    }

    @Test
    fun teamNames_comeFromTheMatch_nullWhenNotNamed() {
        val export = matchExportOf(Match(id = "m1"), emptyList())

        assertEquals("m1", export.matchId)
        assertNull(export.homeTeam)
        assertNull(export.awayTeam)
        assertEquals(Score(0, 0), export.score)
    }
}
