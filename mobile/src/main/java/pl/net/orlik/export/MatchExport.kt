package pl.net.orlik.export

import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.ReviewStatus
import pl.net.orlik.data.Team
import pl.net.orlik.review.cancelledGoals
import pl.net.orlik.review.hasAssist
import pl.net.orlik.review.hasPlayer
import pl.net.orlik.review.playClockOf
import pl.net.orlik.shared.EventType

// One model of an exported match, behind every export format: JSON, text and CSV (design doc,
// section 5). Recordings stay on the phone and are not exported.

data class Score(val home: Int, val away: Int)

data class ExportEvent(
    /** UUID from the watch: lets the platform recognise the same match imported again. */
    val id: String,
    val type: EventType,
    /** Null for structural events and notes. */
    val team: Team?,
    /** Epoch milliseconds, as recorded on the watch. */
    val recordedAt: Long,
    /** Time since MATCH_START minus pauses (design doc, section 8). */
    val playTimeMillis: Long,
    val note: String?,
    /** For a goal: the disallowed goal that cancels it, so it does not count. */
    val cancelledBy: String? = null,
    /** For a disallowed goal: the goal it cancels. */
    val cancelsGoalId: String? = null,
    /** The scorer of a goal, the player of a card; a name typed in review until team rosters (phase 5). */
    val player: String? = null,
    /** The assist of a goal. */
    val assist: String? = null,
) {
    /** Only goals count towards the score, and only those not cancelled. */
    val counted: Boolean
        get() = type == EventType.GOAL && cancelledBy == null
}

data class MatchExport(
    val matchId: String,
    /** Null until named in review; each format shows its default name then. */
    val homeTeam: String?,
    val awayTeam: String?,
    /** MATCH_START; null when it was not synced. */
    val startedAt: Long?,
    /** MATCH_END; null when it was not synced. */
    val endedAt: Long?,
    val score: Score,
    /** Confirmed and structural events in time order. */
    val events: List<ExportEvent>,
    /** Events left under "Do przejrzenia": not exported, the export warns about them first. */
    val unreviewed: Int,
)

/**
 * The export of [match] from its [events]. Only confirmed events go out (structural ones are
 * confirmed from the start); deleted recordings go nowhere. A cancelled goal counts as cancelled
 * only when the disallowed goal cancelling it is exported too, so every file stays consistent on
 * its own.
 */
fun matchExportOf(match: Match, events: List<MatchEvent>): MatchExport {
    val sorted = events.filter { it.status != ReviewStatus.DELETED }.sortedBy { it.recordedAt }
    val playTime = playClockOf(sorted)
    val confirmed = sorted.filter { it.status == ReviewStatus.CONFIRMED && it.type != null }
    val cancelled = cancelledGoals(confirmed)
    val cancelledBy = confirmed
        .filter { it.type == EventType.GOAL_DISALLOWED }
        .mapNotNull { disallowed -> disallowed.linkedGoalEventId?.takeIf { it in cancelled }?.let { it to disallowed.id } }
        .toMap()

    val exported = confirmed.map { event ->
        ExportEvent(
            id = event.id,
            type = event.type!!,
            team = event.team,
            recordedAt = event.recordedAt,
            playTimeMillis = playTime(event.recordedAt),
            note = event.rawTranscript?.trim()?.takeIf { it.isNotEmpty() },
            cancelledBy = cancelledBy[event.id],
            cancelsGoalId = event.linkedGoalEventId?.takeIf { event.type == EventType.GOAL_DISALLOWED && it in cancelled },
            player = event.playerName?.trim()?.takeIf { it.isNotEmpty() && event.type.hasPlayer },
            assist = event.assistName?.trim()?.takeIf { it.isNotEmpty() && event.type.hasAssist },
        )
    }
    return MatchExport(
        matchId = match.id,
        homeTeam = match.homeTeam,
        awayTeam = match.awayTeam,
        startedAt = confirmed.firstOrNull { it.type == EventType.MATCH_START }?.recordedAt,
        endedAt = confirmed.lastOrNull { it.type == EventType.MATCH_END }?.recordedAt,
        score = Score(
            home = exported.count { it.counted && it.team == Team.HOME },
            away = exported.count { it.counted && it.team == Team.AWAY },
        ),
        events = exported,
        unreviewed = sorted.count { it.status == ReviewStatus.TO_REVIEW },
    )
}

