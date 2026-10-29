package pl.net.orlik.review

import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.ReviewStatus
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType
import pl.net.orlik.shared.pausesFrom
import pl.net.orlik.shared.playTimeMillis

/** Why an event still waits under "Do przejrzenia". */
enum class ReviewHint {
    /** A recording nobody has described yet. */
    DESCRIBE,

    /** A goal or a card without the team it belongs to. */
    MISSING_TEAM,

    /** A disallowed goal whose team has no earlier goal to cancel. */
    NO_GOAL,
}

data class ReviewRow(
    val eventId: String,
    /**
     * Play time at the event: time since MATCH_START minus pauses (design doc, section 8), or the
     * start of the minute typed for an event added by hand; null when that has no minute.
     */
    val playTimeMillis: Long?,
    val type: EventType?,
    val team: Team?,
    val note: String?,
    val audioFileRef: String?,
    val hint: ReviewHint?,
    /** A goal cancelled by a disallowed goal: it does not count towards the score. */
    val cancelled: Boolean = false,
    /** For a disallowed goal: play time of the goal it cancels. */
    val cancelsGoalAt: Long? = null,
    /** The scorer of a goal, the player of a card. */
    val player: String? = null,
    val assist: String? = null,
    /** What speech-to-text heard; null when not recognised or nothing was heard. */
    val transcript: String? = null,
    /** Only the minute is known (typed by hand), not the exact play time. */
    val minuteOnly: Boolean = false,
)

data class MatchReview(
    val toReview: List<ReviewRow>,
    val confirmed: List<ReviewRow>,
)

/** Splits a match's events into the two review sections, each in match order. */
fun matchReviewOf(events: List<MatchEvent>): MatchReview {
    val timeline = timelineOf(events)
    val cancelled = cancelledGoals(timeline.events)
    val rows = timeline.events.map { event ->
        val cancelledGoal = event.linkedGoalEventId?.takeIf { event.type == EventType.GOAL_DISALLOWED && it in cancelled }
        event.status to ReviewRow(
            eventId = event.id,
            playTimeMillis = timeline.playTimeOf(event.id),
            minuteOnly = event.recordedAt == null,
            type = event.type,
            team = event.team,
            note = event.rawTranscript?.takeIf { it.isNotBlank() },
            player = event.playerName,
            assist = event.assistName,
            transcript = event.transcript?.takeIf { it.isNotBlank() },
            audioFileRef = event.audioFileRef,
            hint = hintOf(event, linked = cancelledGoal != null),
            cancelled = event.id in cancelled,
            cancelsGoalAt = cancelledGoal?.let { timeline.playTimeOf(it) },
        )
    }
    return MatchReview(
        toReview = rows.filter { it.first == ReviewStatus.TO_REVIEW }.map { it.second },
        confirmed = rows.filter { it.first == ReviewStatus.CONFIRMED }.map { it.second },
    )
}

/**
 * A match's not deleted events in match order, with the play time of each (design doc, sections
 * 3.3 and 8). Events with a time come in time order; an event typed in by hand sits at the start of
 * its minute, after the timed ones of that moment, and one without a minute at the end, in the order
 * typed in.
 */
class Timeline(val events: List<MatchEvent>, private val playTimes: Map<String, Long?>) {
    /** Null for an event typed in by hand without a minute. */
    fun playTimeOf(eventId: String): Long? = playTimes[eventId]
}

fun timelineOf(events: List<MatchEvent>): Timeline {
    val kept = events.filter { it.status != ReviewStatus.DELETED }
    val clock = playClockOf(kept.mapNotNull { it.recordedAt?.let { at -> it.type to at } }.sortedBy { it.second })
    val playTimes = kept.associate { event ->
        event.id to (event.recordedAt?.let(clock) ?: event.minute?.let { minuteStartMillis(it) })
    }
    val sorted = kept.sortedWith(
        compareBy<MatchEvent>(
            { playTimes[it.id] == null },
            { playTimes[it.id] },
            { it.recordedAt == null },
            { it.recordedAt },
            { it.receivedAt },
        ),
    )
    return Timeline(sorted, playTimes)
}

/** Play time at the start of a football minute: 23' begins at 22:00. */
fun minuteStartMillis(minute: Int): Long = (minute - 1).coerceAtLeast(0) * 60_000L

/**
 * Play time at a moment of the match, from its timed events ([timed]: type and time, in time
 * order): since MATCH_START minus pauses. Without MATCH_START (not synced yet) it counts from the
 * oldest event the phone has.
 */
private fun playClockOf(timed: List<Pair<EventType?, Long>>): (Long) -> Long {
    val start = (timed.firstOrNull { it.first == EventType.MATCH_START } ?: timed.firstOrNull())?.second ?: 0
    val pauses = pausesFrom(timed, type = { it.first }, at = { it.second })
    return { at -> playTimeMillis(at, start, pauses) }
}

/** Goals (also disallowed) have a scorer, cards the player who got them. */
val EventType.hasPlayer: Boolean
    get() = needsTeam

/** Only goals have an assist. */
val EventType.hasAssist: Boolean
    get() = this == EventType.GOAL || this == EventType.GOAL_DISALLOWED

/** Goals and cards belong to a team; notes and structural events do not. */
val EventType.needsTeam: Boolean
    get() = this == EventType.GOAL || this == EventType.GOAL_DISALLOWED ||
        this == EventType.YELLOW_CARD || this == EventType.RED_CARD

private fun hintOf(event: MatchEvent, linked: Boolean): ReviewHint? {
    val type = event.type ?: return ReviewHint.DESCRIBE
    return when {
        type.needsTeam && event.team == null -> ReviewHint.MISSING_TEAM
        // A confirmed one without a goal was accepted as such (the goal was never recorded)
        type == EventType.GOAL_DISALLOWED && !linked && event.status == ReviewStatus.TO_REVIEW -> ReviewHint.NO_GOAL
        else -> null
    }
}

/**
 * The scorer with the assist, e.g. "Marcin (asysta: Jurek)", from the app's [withAssist]
 * ("%1$s (asysta: %2$s)") and [assistOnly] ("asysta: %s") texts; null when neither is known.
 */
fun playersText(player: String?, assist: String?, withAssist: String, assistOnly: String): String? = when {
    player != null && assist != null -> withAssist.format(player, assist)
    player != null -> player
    assist != null -> assistOnly.format(assist)
    else -> null
}

/** A team name as typed: trimmed, and null when left empty (the default name shows then). */
fun teamNameOf(typed: String): String? = typed.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }

/**
 * When an event happened, as shown in review: the play time "41:02", only the football minute
 * "23'" for an event typed in by hand, or "–" when that has no minute.
 */
fun formatEventTime(millis: Long?, minuteOnly: Boolean): String = when {
    millis == null -> "–"
    minuteOnly -> "${millis / 60_000 + 1}'"
    else -> formatPlayTime(millis)
}

/** Play time as minutes:seconds, e.g. "41:02"; minutes keep counting past 59. */
fun formatPlayTime(millis: Long): String {
    val seconds = millis / 1_000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

/** Recording length rounded to whole seconds, e.g. "0:06". */
fun formatDuration(millis: Long): String {
    val seconds = (millis + 500) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
