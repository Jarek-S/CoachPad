package pl.net.orlik.review

import pl.net.orlik.data.MatchEvent
import pl.net.orlik.export.Score
import pl.net.orlik.data.Team
import pl.net.orlik.data.ReviewStatus
import pl.net.orlik.shared.EventType
import pl.net.orlik.shared.Pause
import pl.net.orlik.shared.pausesFrom
import pl.net.orlik.shared.playTimeMillis

// A match recorded live on the phone, without the watch (design doc, section 3.3).

enum class LiveStatus { NOT_STARTED, RUNNING, PAUSED, ENDED }

/** Where a live match is, rebuilt from its stored events, so it outlasts the app being closed. */
data class LiveMatch(
    val status: LiveStatus,
    /** MATCH_START; null before kickoff. */
    val startedAt: Long? = null,
    val pauses: List<Pause> = emptyList(),
    /** MATCH_END; null until the match ends. */
    val endedAt: Long? = null,
) {
    /** The play clock at [now], with the pauses taken off; it stops at the end of the match. */
    fun playTimeAt(now: Long): Long {
        val start = startedAt ?: return 0
        return playTimeMillis(endedAt ?: now, start, pauses)
    }
}

/** The state of the match from its structural events; ones without a time or deleted do not count. */
fun liveMatchOf(events: List<MatchEvent>): LiveMatch {
    val structural = events
        .filter { it.type?.isStructural == true && it.recordedAt != null && it.status != ReviewStatus.DELETED }
        .sortedBy { it.recordedAt }
    val startedAt = structural.firstOrNull { it.type == EventType.MATCH_START }?.recordedAt
        ?: return LiveMatch(LiveStatus.NOT_STARTED)
    val pauses = pausesFrom(structural, { it.type }, { checkNotNull(it.recordedAt) })
    val endedAt = structural.firstOrNull { it.type == EventType.MATCH_END }?.recordedAt
    val status = when {
        endedAt != null -> LiveStatus.ENDED
        pauses.lastOrNull()?.resumedAt == null && pauses.isNotEmpty() -> LiveStatus.PAUSED
        else -> LiveStatus.RUNNING
    }
    return LiveMatch(status, startedAt, pauses, endedAt)
}

/**
 * The structural event the phone records next for [action] in [match], or null when the action
 * does not fit (a second start, a pause while paused, anything after the end).
 */
fun liveEventFor(match: LiveMatch, action: LiveAction): EventType? = when (action) {
    LiveAction.START -> EventType.MATCH_START.takeIf { match.status == LiveStatus.NOT_STARTED }
    LiveAction.PAUSE -> EventType.MATCH_PAUSE.takeIf { match.status == LiveStatus.RUNNING }
    LiveAction.RESUME -> EventType.MATCH_RESUME.takeIf { match.status == LiveStatus.PAUSED }
    LiveAction.END -> EventType.MATCH_END.takeIf { match.status == LiveStatus.RUNNING || match.status == LiveStatus.PAUSED }
}

enum class LiveAction { START, PAUSE, RESUME, END }

/** The quick buttons of the match screen (design doc, section 3.3). */
enum class QuickEvent(val type: EventType, val team: Team?) {
    HOME_GOAL(EventType.GOAL, Team.HOME),
    AWAY_GOAL(EventType.GOAL, Team.AWAY),
    CARD(EventType.YELLOW_CARD, null),
    NOTE(EventType.NOTE, null),
}

/**
 * Whether an event from a quick button is complete enough to be confirmed at once: a goal once its
 * scorer is chosen, a card once its team is, a note once something is written. Otherwise it waits
 * under "Do przejrzenia".
 */
fun quickConfirms(draft: EventDraft): Boolean =
    draft.canConfirm && when (draft.type) {
        EventType.GOAL -> draft.storedPlayer != null
        EventType.NOTE -> draft.storedNote != null
        else -> true
    }

/** The score on the match screen; [provisional] while a counted goal waits under "Do przejrzenia". */
data class LiveScore(val score: Score = Score(0, 0), val provisional: Boolean = false)

/**
 * The score on the match screen: a goal with its team counts at once, also one still waiting for
 * its scorer; one cancelled by a confirmed disallowed goal does not. The match list and the export
 * count only confirmed goals.
 */
fun liveScoreOf(events: List<MatchEvent>): LiveScore {
    val cancelled = events
        .filter { it.type == EventType.GOAL_DISALLOWED && it.status == ReviewStatus.CONFIRMED }
        .mapNotNull { it.linkedGoalEventId }
        .toSet()
    val goals = events.filter {
        it.type == EventType.GOAL && it.team != null && it.status != ReviewStatus.DELETED && it.id !in cancelled
    }
    return LiveScore(
        score = Score(goals.count { it.team == Team.HOME }, goals.count { it.team == Team.AWAY }),
        provisional = goals.any { it.status != ReviewStatus.CONFIRMED },
    )
}
