package pl.net.orlik.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import pl.net.orlik.data.Team
import pl.net.orlik.review.playersText
import pl.net.orlik.shared.EventType

// The match as a short text to share, e.g. in a WhatsApp group (design doc, section 5).

/** Texts in the app's language, read from strings.xml by the caller. */
data class ShareTextLabels(
    /** Shown while a team has no name set ("Gospodarze" / "Goście"). */
    val homeTeam: String,
    val awayTeam: String,
    /** java.time pattern of the match date, e.g. "EEEE, d MMMM yyyy, HH:mm". */
    val datePattern: String,
    val locale: Locale,
    /** After a cancelled goal; %s is the minute of the disallowed goal, e.g. "nie liczy się (gol nieuznany, %s)". */
    val notCounted: String,
    /** A disallowed goal with no goal to cancel, e.g. "Gol nieuznany". */
    val disallowedGoal: String,
    /** The scorer with the assist, e.g. "%1$s (asysta: %2$s)". */
    val playerWithAssist: String,
    /** An assist without the scorer, e.g. "asysta: %s". */
    val assistOnly: String,
    /** Heading of the per-player summary, e.g. "Strzelcy i asysty". */
    val scorersTitle: String,
    /** A count of goals with its plural, e.g. "2 gole". */
    val goals: (Int) -> String,
    /** A count of assists with its plural, e.g. "1 asysta". */
    val assists: (Int) -> String,
)

/** A player's counted goals and assists in the match, under the name as first written. */
data class PlayerTally(val name: String, val goals: Int, val assists: Int)

/**
 * Goals and assists per player of [team]: only goals that count (a disallowed one does not), most
 * goals first, then most assists, then the order of the match. Names compare without case and
 * surrounding spaces, so "marcin" and "Marcin " add up.
 */
fun playerTalliesOf(export: MatchExport, team: Team): List<PlayerTally> {
    data class Tally(val name: String, val first: Int, var goals: Int = 0, var assists: Int = 0)
    val tallies = linkedMapOf<String, Tally>()
    fun tally(name: String?, index: Int): Tally? {
        val shown = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return tallies.getOrPut(shown.lowercase()) { Tally(shown, index) }
    }
    export.events.forEachIndexed { index, event ->
        if (!event.counted || event.team != team) return@forEachIndexed
        tally(event.player, index)?.let { it.goals++ }
        tally(event.assist, index)?.let { it.assists++ }
    }
    return tallies.values
        .sortedWith(compareByDescending<Tally> { it.goals }.thenByDescending { it.assists }.thenBy { it.first })
        .map { PlayerTally(it.name, it.goals, it.assists) }
}

/**
 * [export] as text: the teams and score, the date, then one line per goal and card, with the
 * scorer and assist (or the player of a card) and the note of extra remarks. Notes
 * ("Notatka") and structural events are left out — usually remarks not meant for everyone. A
 * disallowed goal shows on the goal it cancels. At the end, goals and assists per player of each
 * team ([playerTalliesOf]), when any goal names a player.
 */
fun shareTextOf(export: MatchExport, labels: ShareTextLabels, zone: ZoneId): String {
    val home = export.homeTeam ?: labels.homeTeam
    val away = export.awayTeam ?: labels.awayTeam
    val byId = export.events.associateBy { it.id }
    val date = (export.startedAt ?: export.events.firstNotNullOfOrNull { it.recordedAt })?.let {
        DateTimeFormatter.ofPattern(labels.datePattern, labels.locale).format(Instant.ofEpochMilli(it).atZone(zone))
    }

    val lines = export.events.mapNotNull { event ->
        val mark = when (event.type) {
            EventType.GOAL -> "⚽"
            EventType.YELLOW_CARD -> "🟨"
            EventType.RED_CARD -> "🟥"
            // Shown on the goal it cancels
            EventType.GOAL_DISALLOWED -> if (event.cancelsGoalId != null) return@mapNotNull null else "🚫"
            else -> return@mapNotNull null
        }
        val team = when (event.team) {
            Team.HOME -> home
            Team.AWAY -> away
            null -> null
        }
        val details = listOfNotNull(
            event.type.takeIf { it == EventType.GOAL_DISALLOWED }?.let { labels.disallowedGoal },
            playersText(event.player, event.assist, labels.playerWithAssist, labels.assistOnly),
            event.note,
            event.cancelledBy?.let { byId[it] }?.let { goal -> footballMinute(goal.playTimeMillis)?.let(labels.notCounted::format) },
        )
        listOfNotNull(footballMinute(event.playTimeMillis), mark, team).joinToString(" ") +
            details.joinToString("") { " – $it" }
    }

    return buildString {
        append("$home – $away  ${export.score.home}:${export.score.away}")
        date?.let { append('\n').append(it) }
        if (lines.isNotEmpty()) append("\n\n").append(lines.joinToString("\n"))
        val teams = listOf(Team.HOME to home, Team.AWAY to away)
            .map { (team, name) -> name to playerTalliesOf(export, team) }
            .filter { (_, tallies) -> tallies.isNotEmpty() }
        if (teams.isNotEmpty()) {
            append("\n\n").append(labels.scorersTitle)
            teams.forEach { (name, tallies) ->
                append('\n').append(name)
                tallies.forEach { tally ->
                    val counts = listOfNotNull(
                        tally.goals.takeIf { it > 0 }?.let(labels.goals),
                        tally.assists.takeIf { it > 0 }?.let(labels.assists),
                    )
                    append("\n• ").append(tally.name).append(" – ").append(counts.joinToString(", "))
                }
            }
        }
    }
}

/** The football convention: the minute being played, so 12:34 of play time is "13'"; null without a time. */
fun footballMinute(playTimeMillis: Long?): String? = playTimeMillis?.let { "${it / 60_000 + 1}'" }
