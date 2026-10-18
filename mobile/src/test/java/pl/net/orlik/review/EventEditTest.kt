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
