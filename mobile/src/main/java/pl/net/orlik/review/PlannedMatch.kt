package pl.net.orlik.review

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import pl.net.orlik.data.MatchListItem

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

/**
 * Planned matches a synced match can still be joined with by hand: offered only while the match
 * has neither team names nor players, so nothing typed for it is replaced. Planned matches with
 * nothing in them plan nothing and are left out.
 */
fun plannedChoices(matches: List<MatchListItem>, matchId: String): List<MatchListItem> {
    val match = matches.firstOrNull { it.matchId == matchId } ?: return emptyList()
    if (match.isPlanned || match.hasLineup) return emptyList()
    return matches.filter { it.isPlanned && it.hasLineup }
}

private val MatchListItem.hasLineup: Boolean get() = homeTeam != null || awayTeam != null || players > 0

/**
 * The start of a day chosen in the date picker, which gives it as UTC midnight, [utcDayMillis]:
 * the day of a planned match, or when one typed in from notes was played.
 */
fun dayStartOf(utcDayMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(utcDayMillis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

/** The day of [epochMillis] in [zone] as the date picker counts days: UTC midnight of that date. */
fun utcDayOf(epochMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** A match planned for a day after today: it cannot be typed in from notes yet. */
fun isPlannedLater(plannedAt: Long, nowMillis: Long, zone: ZoneId): Boolean =
    utcDayOf(plannedAt, zone) > utcDayOf(nowMillis, zone)
