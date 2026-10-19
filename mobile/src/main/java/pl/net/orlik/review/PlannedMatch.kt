package pl.net.orlik.review

// Matches planned on the phone before kickoff, taken over by a match from the watch (design doc, section 11).

/** A match created on the phone with a lineup or team names, still without events from the watch. */
data class PlannedMatch(val id: String, val plannedAt: Long)

/**
 * The planned match a watch match new to the phone takes over, given the time of its first event
 * received: the only one planned before it. Null when there is none or several to choose from;
 * a match planned later cannot be for a match that had already started.
 */
fun plannedMatchFor(firstEventAt: Long, planned: List<PlannedMatch>): String? =
    planned.filter { it.plannedAt <= firstEventAt }.singleOrNull()?.id
