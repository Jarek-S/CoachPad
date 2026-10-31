package pl.net.orlik.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.ReviewStatus
import pl.net.orlik.data.Team
import pl.net.orlik.export.Score
import pl.net.orlik.shared.EventType

class LiveMatchTest {

    private fun event(id: String, type: EventType?, at: Long?) = MatchEvent(id, "m", at, type, null, 0)

    private val start = event("s", EventType.MATCH_START, 1_000)
    private val pause = event("p", EventType.MATCH_PAUSE, 61_000)
    private val resume = event("r", EventType.MATCH_RESUME, 91_000)

    @Test
    fun notStarted_hasNoClock() {
        val match = liveMatchOf(listOf(event("n", null, null)))
        assertEquals(LiveStatus.NOT_STARTED, match.status)
        assertEquals(0, match.playTimeAt(99_000))
    }

    @Test
    fun pausedClock_stopsAndResumesWithoutThePause() {
        val paused = liveMatchOf(listOf(start, pause))
        assertEquals(LiveStatus.PAUSED, paused.status)
        assertEquals(60_000, paused.playTimeAt(80_000))

        val running = liveMatchOf(listOf(resume, start, pause))
        assertEquals(LiveStatus.RUNNING, running.status)
        assertEquals(70_000, running.playTimeAt(101_000))
    }

    @Test
    fun ended_clockStopsAtTheEnd() {
        val match = liveMatchOf(listOf(start, event("e", EventType.MATCH_END, 31_000)))
        assertEquals(LiveStatus.ENDED, match.status)
        assertEquals(30_000, match.playTimeAt(500_000))
    }

    @Test
    fun deletedOrUntimedEvents_doNotCount() {
        val match = liveMatchOf(listOf(start, pause.copy(status = ReviewStatus.DELETED), event("x", EventType.MATCH_END, null)))
        assertEquals(LiveStatus.RUNNING, match.status)
    }

    @Test
    fun actions_onlyWhereTheyFit() {
        val notStarted = liveMatchOf(emptyList())
        val running = liveMatchOf(listOf(start))
        val paused = liveMatchOf(listOf(start, pause))
        val ended = liveMatchOf(listOf(start, event("e", EventType.MATCH_END, 2_000)))

        assertEquals(EventType.MATCH_START, liveEventFor(notStarted, LiveAction.START))
        assertNull(liveEventFor(running, LiveAction.START))
        assertEquals(EventType.MATCH_PAUSE, liveEventFor(running, LiveAction.PAUSE))
        assertNull(liveEventFor(paused, LiveAction.PAUSE))
        assertEquals(EventType.MATCH_RESUME, liveEventFor(paused, LiveAction.RESUME))
        assertEquals(EventType.MATCH_END, liveEventFor(paused, LiveAction.END))
        assertNull(liveEventFor(ended, LiveAction.END))
        assertNull(liveEventFor(notStarted, LiveAction.END))
    }

    @Test
    fun quickEvents_confirmOnceComplete() {
        assertFalse(quickConfirms(EventDraft(EventType.GOAL, Team.HOME)))
        assertFalse(quickConfirms(EventDraft(EventType.GOAL, Team.HOME, player = " ")))
        assertTrue(quickConfirms(EventDraft(EventType.GOAL, Team.HOME, player = "7 Marcin")))
        assertFalse(quickConfirms(EventDraft(EventType.YELLOW_CARD)))
        assertTrue(quickConfirms(EventDraft(EventType.RED_CARD, Team.AWAY)))
        assertFalse(quickConfirms(EventDraft(EventType.NOTE, note = " ")))
        assertTrue(quickConfirms(EventDraft(EventType.NOTE, note = "Zmiana")))
    }

    private fun goal(id: String, team: Team, status: ReviewStatus) =
        MatchEvent(id, "m", 1_000, EventType.GOAL, null, 0, team = team, status = status)

    @Test
    fun liveScore_countsGoalsWaitingForTheirScorer() {
        val score = liveScoreOf(
            listOf(
                goal("h1", Team.HOME, ReviewStatus.CONFIRMED),
                goal("h2", Team.HOME, ReviewStatus.TO_REVIEW),
                goal("a1", Team.AWAY, ReviewStatus.CONFIRMED),
                goal("a2", Team.AWAY, ReviewStatus.DELETED),
                MatchEvent("n", "m", 2_000, EventType.GOAL, null, 0),
            ),
        )
        assertEquals(LiveScore(Score(2, 1), provisional = true), score)
    }

    @Test
    fun liveScore_leavesOutACancelledGoal() {
        val disallowed = MatchEvent(
            "d", "m", 2_000, EventType.GOAL_DISALLOWED, null, 0,
            team = Team.HOME, status = ReviewStatus.CONFIRMED, linkedGoalEventId = "h1",
        )
        val score = liveScoreOf(listOf(goal("h1", Team.HOME, ReviewStatus.CONFIRMED), disallowed))
        assertEquals(LiveScore(Score(0, 0), provisional = false), score)
    }
}
