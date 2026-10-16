package pl.net.orlik.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.ReviewStatus
import pl.net.orlik.data.Team
import pl.net.orlik.review.cancelledGoals
import pl.net.orlik.review.playClockOf
import pl.net.orlik.shared.EventType

// One model of an exported match, behind every export format: JSON, text, CSV and the audio
// package (design doc, section 5).

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
    /** Recording file name on the phone, in the recordings directory. */
    val audioFileRef: String? = null,
    /** Recording file name in the export, the same in every format: the time it was recorded. */
    val audioFile: String? = null,
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
 * its own. Recording names use local time in [zone].
 */
fun matchExportOf(match: Match, events: List<MatchEvent>, zone: ZoneId): MatchExport {
    val sorted = events.filter { it.status != ReviewStatus.DELETED }.sortedBy { it.recordedAt }
    val playTime = playClockOf(sorted)
    val confirmed = sorted.filter { it.status == ReviewStatus.CONFIRMED && it.type != null }
    val cancelled = cancelledGoals(confirmed)
    val cancelledBy = confirmed
        .filter { it.type == EventType.GOAL_DISALLOWED }
        .mapNotNull { disallowed -> disallowed.linkedGoalEventId?.takeIf { it in cancelled }?.let { it to disallowed.id } }
        .toMap()
    val audioFiles = audioFileNames(confirmed.filter { it.audioFileRef != null }, zone)

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
            audioFileRef = event.audioFileRef,
            audioFile = audioFiles[event.id],
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

private val AUDIO_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

/**
 * Export names of [recordings] (in time order) by event id, e.g. "2026-10-11_14-12-40.m4a".
 * Recordings within the same second get "_2", "_3"… so no file in the package overwrites another.
 */
private fun audioFileNames(recordings: List<MatchEvent>, zone: ZoneId): Map<String, String> {
    val used = mutableMapOf<String, Int>()
    return recordings.associate { event ->
        val base = AUDIO_NAME.format(Instant.ofEpochMilli(event.recordedAt).atZone(zone))
        val n = used.merge(base, 1, Int::plus)!!
        val extension = event.audioFileRef!!.substringAfterLast('.', "m4a")
        event.id to if (n == 1) "$base.$extension" else "${base}_$n.$extension"
    }
}
