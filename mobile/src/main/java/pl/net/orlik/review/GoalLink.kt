package pl.net.orlik.review

import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.ReviewStatus
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType

// Linking a disallowed goal to the goal it cancels (design doc, section 8).

/**
 * Goals of [team] before the event [beforeId] in the match, in match order: what a disallowed goal
 * can cancel.
 */
fun goalCandidates(events: List<MatchEvent>, beforeId: String, team: Team): List<MatchEvent> =
    timelineOf(events).events
        .takeWhile { it.id != beforeId }
        .filter { it.isGoal && it.team == team }

/**
 * The goal a disallowed goal [disallowedId] of [team] cancels on its own: the last earlier goal of
 * the same team, skipping goals already cancelled by another disallowed goal. Null when there is
 * none ("nie znaleziono gola do powiązania").
 */
fun goalToCancel(events: List<MatchEvent>, disallowedId: String, team: Team): String? {
    val taken = cancelledGoals(events.filter { it.id != disallowedId })
    return goalCandidates(events, disallowedId, team).lastOrNull { it.id !in taken }?.id
}

/**
 * Goals that do not count towards the score. A link counts only while it points at a goal: one
 * deleted or retyped since is ignored.
 */
fun cancelledGoals(events: List<MatchEvent>): Set<String> {
    val goals = events.filter { it.isGoal }.map { it.id }.toSet()
    return events
        .filter { it.type == EventType.GOAL_DISALLOWED && it.status != ReviewStatus.DELETED }
        .mapNotNull { it.linkedGoalEventId?.takeIf { id -> id in goals } }
        .toSet()
}

private val MatchEvent.isGoal: Boolean
    get() = type == EventType.GOAL && status != ReviewStatus.DELETED
