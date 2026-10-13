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
    /** Play time at the event: time since MATCH_START minus pauses (design doc, section 8). */
    val playTimeMillis: Long,
    val type: EventType?,
    val team: Team?,
    val note: String?,
    val audioFileRef: String?,
    val hint: ReviewHint?,
    /** A goal cancelled by a disallowed goal: it does not count towards the score. */
    val cancelled: Boolean = false,
    /** For a disallowed goal: play time of the goal it cancels. */
    val cancelsGoalAt: Long? = null,
)

data class MatchReview(
    val toReview: List<ReviewRow>,
    val confirmed: List<ReviewRow>,
)

/** Splits a match's events into the two review sections, each in time order. */
fun matchReviewOf(events: List<MatchEvent>): MatchReview {
    val sorted = events.filter { it.status != ReviewStatus.DELETED }.sortedBy { it.recordedAt }
    // Without MATCH_START (not synced yet) minutes count from the oldest event the phone has
    val start = (sorted.firstOrNull { it.type == EventType.MATCH_START } ?: sorted.firstOrNull())?.recordedAt ?: 0
    val pauses = pausesFrom(sorted, type = { it.type }, at = { it.recordedAt })
    val cancelled = cancelledGoals(sorted)
    val byId = sorted.associateBy { it.id }
    val rows = sorted.map { event ->
        val cancelledGoal = event.linkedGoalEventId?.takeIf { event.type == EventType.GOAL_DISALLOWED && it in cancelled }
        event.status to ReviewRow(
            eventId = event.id,
            playTimeMillis = playTimeMillis(event.recordedAt, start, pauses),
            type = event.type,
            team = event.team,
            note = event.rawTranscript?.takeIf { it.isNotBlank() },
            audioFileRef = event.audioFileRef,
            hint = hintOf(event, linked = cancelledGoal != null),
            cancelled = event.id in cancelled,
            cancelsGoalAt = cancelledGoal?.let { playTimeMillis(byId.getValue(it).recordedAt, start, pauses) },
        )
    }
    return MatchReview(
        toReview = rows.filter { it.first == ReviewStatus.TO_REVIEW }.map { it.second },
        confirmed = rows.filter { it.first == ReviewStatus.CONFIRMED }.map { it.second },
    )
}

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

/** A team name as typed: trimmed, and null when left empty (the default name shows then). */
fun teamNameOf(typed: String): String? = typed.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }

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
