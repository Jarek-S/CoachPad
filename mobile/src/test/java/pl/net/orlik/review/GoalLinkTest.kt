package pl.net.orlik.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.ReviewStatus
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType

class GoalLinkTest {

    private fun min(m: Int) = m * 60_000L

    private fun event(
        id: String,
        minute: Int,
        type: EventType?,
        team: Team? = null,
        link: String? = null,
        status: ReviewStatus = ReviewStatus.CONFIRMED,
    ) = MatchEvent(id, "m1", min(minute), type, "$id.m4a", 0, team = team, linkedGoalEventId = link, status = status)

    // The example from the design doc: a home goal at 12:34, a disallowed away goal at 41:02
    private val homeGoal = event("home", 12, EventType.GOAL, Team.HOME)
    private val awayGoal = event("away", 30, EventType.GOAL, Team.AWAY)
    private val disallowed = event("dis", 41, EventType.GOAL_DISALLOWED, Team.AWAY, status = ReviewStatus.TO_REVIEW)

    @Test
    fun cancels_lastEarlierGoalOfTheSameTeam() {
        val events = listOf(homeGoal, awayGoal, event("away2", 35, EventType.GOAL, Team.AWAY), disallowed)
        assertEquals("away2", goalToCancel(events, "dis", disallowed.recordedAt, Team.AWAY))
        // The other team's goal is never cancelled
        assertNull(goalToCancel(listOf(homeGoal, disallowed), "dis", disallowed.recordedAt, Team.AWAY))
    }

    @Test
    fun laterAndDeletedGoals_areNotCandidates() {
        val events = listOf(
            event("deleted", 20, EventType.GOAL, Team.AWAY, status = ReviewStatus.DELETED),
            disallowed,
            event("later", 50, EventType.GOAL, Team.AWAY),
        )
        assertNull(goalToCancel(events, "dis", disallowed.recordedAt, Team.AWAY))
    }

    @Test
    fun goalAlreadyCancelled_isSkipped() {
        val first = event("dis1", 32, EventType.GOAL_DISALLOWED, Team.AWAY, link = "away2")
        val events = listOf(awayGoal, event("away2", 31, EventType.GOAL, Team.AWAY), first, disallowed)
        assertEquals("away", goalToCancel(events, "dis", disallowed.recordedAt, Team.AWAY))
        // Its own earlier link does not count as taken
        assertEquals("away2", goalToCancel(events, "dis1", first.recordedAt, Team.AWAY))
    }

    @Test
    fun relinked_followsTypeAndTeam() {
        val events = listOf(homeGoal, awayGoal, disallowed)
        val draft = EventDraft(type = EventType.GOAL_DISALLOWED)
        assertNull(relinked(draft, disallowed, events).linkedGoalId)
        assertEquals("away", relinked(draft.copy(team = Team.AWAY), disallowed, events).linkedGoalId)
        assertEquals("home", relinked(draft.copy(team = Team.HOME), disallowed, events).linkedGoalId)
        assertNull(relinked(EventDraft(EventType.GOAL, Team.AWAY), disallowed, events).linkedGoalId)
    }

    @Test
    fun storedLink_onlyForDisallowedGoals() {
        assertEquals("g", EventDraft(EventType.GOAL_DISALLOWED, Team.AWAY, linkedGoalId = "g").storedLink)
        assertNull(EventDraft(EventType.GOAL, Team.AWAY, linkedGoalId = "g").storedLink)
    }

    @Test
    fun review_marksCancelledGoalAndLink() {
        val start = event("start", 0, EventType.MATCH_START).copy(audioFileRef = null)
        val rows = matchReviewOf(listOf(start, homeGoal, awayGoal, disallowed.copy(linkedGoalEventId = "away")))
            .let { it.toReview + it.confirmed }.associateBy { it.eventId }
        assertEquals(true, rows.getValue("away").cancelled)
        assertEquals(false, rows.getValue("home").cancelled)
        assertEquals(min(30), rows.getValue("dis").cancelsGoalAt)
        assertNull(rows.getValue("dis").hint)
    }

    @Test
    fun review_noGoalHint_onlyWhileWaiting() {
        val waiting = matchReviewOf(listOf(homeGoal, disallowed)).toReview.single()
        assertEquals(ReviewHint.NO_GOAL, waiting.hint)
        val accepted = matchReviewOf(listOf(homeGoal, disallowed.copy(status = ReviewStatus.CONFIRMED)))
            .confirmed.single { it.eventId == "dis" }
        assertNull(accepted.hint)
    }

    @Test
    fun linkToRetypedGoal_isIgnored() {
        val events = listOf(awayGoal.copy(type = EventType.NOTE), disallowed.copy(linkedGoalEventId = "away"))
        assertEquals(emptySet<String>(), cancelledGoals(events))
        assertEquals(ReviewHint.NO_GOAL, matchReviewOf(events).toReview.single { it.eventId == "dis" }.hint)
    }
}
