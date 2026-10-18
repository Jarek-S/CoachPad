package pl.net.orlik.review

import pl.net.orlik.data.LineupPlayer

/** A player as typed in the lineup's single field, e.g. "7 Marcin" or just "Piotrek". */
data class PlayerEntry(val number: Int?, val name: String)

private val numbered = Regex("""^(\d{1,3})[.\s]+(.+)$""")

/**
 * "7 Marcin" → 7, "Marcin"; "10. Kuba S." → 10, "Kuba S."; "Piotrek" → no number. Null when no
 * name is left (empty, or a number alone), so nothing is added.
 */
fun parsePlayerEntry(typed: String): PlayerEntry? {
    val text = teamNameOf(typed) ?: return null
    val match = numbered.matchEntire(text)
        ?: return if (text.all { it.isDigit() }) null else PlayerEntry(null, text)
    return PlayerEntry(match.groupValues[1].toInt(), match.groupValues[2])
}

/** Back into the field when a player is corrected: "7 Marcin", or the name alone. */
fun playerEntryText(number: Int?, name: String): String = if (number == null) name else "$number $name"

/** By shirt number, players without one last; then by name. */
val lineupOrder: Comparator<LineupPlayer> =
    compareBy<LineupPlayer, Int?>(nullsLast()) { it.number }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
