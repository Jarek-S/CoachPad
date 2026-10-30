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
    /** For a disallowed goal: the goal it cancels; null when none is linked. */
    val linkedGoalId: String? = null,
    /** The scorer of a goal, the player of a card. */
    val player: String = "",
    /** The assist of a goal. */
    val assist: String = "",
    /** The football minute of an event added by hand, as typed; empty when not known. */
    val minute: String = "",
) {
    /**
     * A type that has no team (a note) drops the team, so a stale one is not saved. The player and
     * the assist stay while editing, so switching the type back and forth loses nothing; only what
     * the type has is saved.
     */
    fun withType(type: EventType): EventDraft = copy(type = type, team = team.takeIf { type.needsTeam })

    val storedPlayer: String?
        get() = player.trim().takeIf { it.isNotEmpty() && type?.hasPlayer == true }

    val storedAssist: String?
        get() = assist.trim().takeIf { it.isNotEmpty() && type?.hasAssist == true }

    /** The link as stored: only a disallowed goal has one. */
    val storedLink: String?
        get() = linkedGoalId.takeIf { type == EventType.GOAL_DISALLOWED }

    /** "Zatwierdź" needs a type, and a team for goals and cards. */
    val canConfirm: Boolean
        get() = type != null && (!type.needsTeam || team != null)

    /** The note as stored: null when nothing was typed. */
    val storedNote: String?
        get() = note.trim().takeIf { it.isNotEmpty() }

    /** The minute as stored: null when none (or 0) was typed. */
    val storedMinute: Int?
        get() = minute.toIntOrNull()?.takeIf { it > 0 }

    /** Nothing chosen or typed: an event added by hand is dropped again on leaving it like this. */
    val isEmpty: Boolean
        get() = type == null && team == null && storedNote == null && player.isBlank() && assist.isBlank() && storedMinute == null

    /** Keeps only digits, at most [MAX_MINUTE_DIGITS] of them. */
    fun withMinute(typed: String): EventDraft = copy(minute = typed.filter { it.isDigit() }.take(MAX_MINUTE_DIGITS))
}

const val MAX_MINUTE_DIGITS = 3

fun draftOf(event: MatchEvent): EventDraft =
    EventDraft(
        type = event.type,
        team = event.team,
        note = event.rawTranscript.orEmpty(),
        linkedGoalId = event.linkedGoalEventId,
        player = event.playerName.orEmpty(),
        assist = event.assistName.orEmpty(),
        minute = event.minute?.toString().orEmpty(),
    )

/** [events] as they would be with the edited event at the minute typed in [draft], for its order. */
fun withDraftMinute(events: List<MatchEvent>, eventId: String, draft: EventDraft): List<MatchEvent> =
    events.map { if (it.id == eventId && it.recordedAt == null) it.copy(minute = draft.storedMinute) else it }

/**
 * After the type or the team changes, a disallowed goal is linked again to the goal it cancels on
 * its own (design doc, section 8): this is the moment its team becomes known. [events] are the
 * match's events as stored.
 */
fun relinked(draft: EventDraft, event: MatchEvent, events: List<MatchEvent>): EventDraft {
    val team = draft.team
    val link = if (draft.type == EventType.GOAL_DISALLOWED && team != null) {
        goalToCancel(events, event.id, team)
    } else {
        null
    }
    return draft.copy(linkedGoalId = link)
}

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
