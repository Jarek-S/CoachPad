package pl.net.orlik.review

import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType

/** Types offered on the edit screen, in the order of its tiles. */
val editableTypes: List<EventType> = listOf(
    EventType.GOAL,
    EventType.GOAL_DISALLOWED,
    EventType.YELLOW_CARD,
    EventType.RED_CARD,
    EventType.NOTE,
)

/** What the edit screen changes in an event, before it is saved. */
data class EventDraft(
    val type: EventType? = null,
    val team: Team? = null,
    val note: String = "",
) {
    /** A type that has no team (a note) drops the team, so a stale one is not saved. */
    fun withType(type: EventType): EventDraft = copy(type = type, team = team.takeIf { type.needsTeam })

    /** "Zatwierdź" needs a type, and a team for goals and cards. */
    val canConfirm: Boolean
        get() = type != null && (!type.needsTeam || team != null)

    /** The note as stored: null when nothing was typed. */
    val storedNote: String?
        get() = note.trim().takeIf { it.isNotEmpty() }
}

fun draftOf(event: MatchEvent): EventDraft =
    EventDraft(type = event.type, team = event.team, note = event.rawTranscript.orEmpty())

/** Where the edited event is among those waiting for review: "3 z 3". */
data class ReviewPosition(val index: Int, val count: Int)

/** Null when the event is not waiting for review (a confirmed one opened from the list). */
fun positionOf(review: MatchReview, eventId: String): ReviewPosition? {
    val index = review.toReview.indexOfFirst { it.eventId == eventId }
    return if (index < 0) null else ReviewPosition(index + 1, review.toReview.size)
}

/**
 * The event to open after [eventId] is skipped, confirmed or deleted: the next one waiting for
 * review later in the match. Skipped ones before it are not revisited, so "Pomiń" cannot loop;
 * null means back to the list.
 */
fun nextToReview(review: MatchReview, eventId: String): String? {
    val index = review.toReview.indexOfFirst { it.eventId == eventId }
    return if (index < 0) null else review.toReview.getOrNull(index + 1)?.eventId
}
