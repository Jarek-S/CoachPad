package pl.net.orlik.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType

class EventEditTest {

    private fun row(id: String) = ReviewRow(id, 0, null, null, null, "$id.m4a", ReviewHint.DESCRIBE)

    private val review = MatchReview(toReview = listOf(row("a"), row("b"), row("c")), confirmed = listOf(row("x")))

    @Test
    fun canConfirm_needsTypeAndTeamForGoalsAndCards() {
        assertFalse(EventDraft().canConfirm)
        assertFalse(EventDraft(type = EventType.GOAL).canConfirm)
        assertFalse(EventDraft(type = EventType.RED_CARD).canConfirm)
        assertTrue(EventDraft(type = EventType.GOAL, team = Team.AWAY).canConfirm)
        assertTrue(EventDraft(type = EventType.NOTE).canConfirm)
    }

    @Test
    fun withType_noteDropsTeam() {
        val draft = EventDraft(type = EventType.GOAL, team = Team.HOME).withType(EventType.NOTE)
        assertNull(draft.team)
        assertEquals(Team.HOME, EventDraft(type = EventType.GOAL, team = Team.HOME).withType(EventType.YELLOW_CARD).team)
    }

    @Test
    fun players_savedOnlyForTypesThatHaveThem() {
        val draft = EventDraft(type = EventType.GOAL, player = " Marcin ", assist = "Jurek")
        assertEquals("Marcin", draft.storedPlayer)
        assertEquals("Jurek", draft.storedAssist)

        // A card has a player but no assist; a note has neither
        val card = draft.withType(EventType.YELLOW_CARD)
        assertEquals("Marcin", card.storedPlayer)
        assertNull(card.storedAssist)
        assertNull(draft.withType(EventType.NOTE).storedPlayer)
        assertNull(EventDraft(player = "Marcin").storedPlayer)

        // Kept while editing: switching back brings them again
        assertEquals("Jurek", card.withType(EventType.GOAL).storedAssist)
        assertNull(EventDraft(type = EventType.GOAL, player = "  ").storedPlayer)
    }

    @Test
    fun playersText_scorerAndAssist() {
        fun text(player: String?, assist: String?) = playersText(player, assist, "%1${'$'}s (asysta: %2${'$'}s)", "asysta: %s")
        assertEquals("Marcin (asysta: Jurek)", text("Marcin", "Jurek"))
        assertEquals("Marcin", text("Marcin", null))
        assertEquals("asysta: Jurek", text(null, "Jurek"))
        assertNull(text(null, null))
    }

    @Test
    fun storedNote_blankIsNull() {
        assertNull(EventDraft(note = "  ").storedNote)
        assertEquals("Spalony", EventDraft(note = " Spalony\n").storedNote)
    }

    @Test
    fun draftOf_copiesReviewFields() {
        val event = MatchEvent("e", "m", 0, EventType.GOAL, "e.m4a", 0, team = Team.AWAY, rawTranscript = "Marcin")
        assertEquals(EventDraft(EventType.GOAL, Team.AWAY, "Marcin"), draftOf(event))
        assertEquals("", draftOf(event.copy(rawTranscript = null)).note)
        assertEquals("23", draftOf(event.copy(recordedAt = null, minute = 23)).minute)
    }

    @Test
    fun minute_digitsOnly_zeroIsNone() {
        assertEquals("45", EventDraft().withMinute("4a5'").minute)
        assertEquals("120", EventDraft().withMinute("1200").minute)
        assertEquals(45, EventDraft(minute = "45").storedMinute)
        assertNull(EventDraft(minute = "0").storedMinute)
        assertNull(EventDraft().storedMinute)
    }

    @Test
    fun isEmpty_untilSomethingIsChosenOrTyped() {
        assertTrue(EventDraft(note = " ", minute = "0").isEmpty)
        assertFalse(EventDraft(type = EventType.NOTE).isEmpty)
        assertFalse(EventDraft(team = Team.HOME).isEmpty)
        assertFalse(EventDraft(minute = "7").isEmpty)
        assertFalse(EventDraft(player = "Adam").isEmpty)
    }

    @Test
    fun withDraftMinute_onlyForAnEventAddedByHand() {
        val added = MatchEvent("a", "m", null, EventType.GOAL, null, 0)
        val recorded = MatchEvent("r", "m", 5, EventType.GOAL, "r.m4a", 0)
        val draft = EventDraft(minute = "12")
        assertEquals(12, withDraftMinute(listOf(added, recorded), "a", draft)[0].minute)
        assertNull(withDraftMinute(listOf(added, recorded), "r", draft)[1].minute)
    }

    @Test
    fun position_amongEventsToReview() {
        assertEquals(ReviewPosition(2, 3), positionOf(review, "b"))
        assertNull(positionOf(review, "x"))
    }

    @Test
    fun next_isTheFollowingEventToReview() {
        assertEquals("b", nextToReview(review, "a"))
        assertEquals("c", nextToReview(review, "b"))
        // The last one goes back to the list instead of returning to skipped ones
        assertNull(nextToReview(review, "c"))
        assertNull(nextToReview(review, "x"))
    }
}
