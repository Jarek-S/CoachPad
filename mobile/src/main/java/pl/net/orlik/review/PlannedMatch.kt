package pl.net.orlik.review

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import pl.net.orlik.data.MatchListItem

// Matches planned on the phone before kickoff, joined by hand with a match from the watch (design doc, section 11).

/**
 * Planned matches a match from the watch can still be joined with, the soonest first: offered only
 * while the match has neither team names nor players, so nothing typed for it is replaced. Planned
 * matches with nothing in them plan nothing and are left out; a match recorded or typed in on the
 * phone was planned itself and is offered none.
 */
fun plannedChoices(matches: List<MatchListItem>, matchId: String): List<MatchListItem> {
    val match = matches.firstOrNull { it.matchId == matchId } ?: return emptyList()
    if (match.isPlanned || match.plannedAt != null || match.hasLineup) return emptyList()
    return matches.filter { it.isPlanned && it.hasLineup }.sortedBy { it.plannedAt }
}

/**
 * Of the [choices], the ones planned for the day a match from the watch started, [startedAt], in
 * [zone]: the review offers only these, however late the match is synced.
 */
fun plannedOnTheDay(choices: List<MatchListItem>, startedAt: Long, zone: ZoneId): List<MatchListItem> =
    choices.filter { it.plannedAt != null && utcDayOf(it.plannedAt, zone) == utcDayOf(startedAt, zone) }

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
