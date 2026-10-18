package pl.net.orlik.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import pl.net.orlik.data.Team
import pl.net.orlik.review.formatPlayTime
import pl.net.orlik.shared.EventType

// The match as a CSV file to open in a spreadsheet (design doc, section 5).

/** Texts in the app's language, read from strings.xml by the caller. */
data class CsvLabels(
    /** Shown while a team has no name set ("Gospodarze" / "Goście"). */
    val homeTeam: String,
    val awayTeam: String,
    /** Column names, in the order of [CSV_COLUMN_COUNT] columns. */
    val columns: List<String>,
    val typeNames: Map<EventType, String>,
    /** In the "counts" column of goals. */
    val yes: String,
    val no: String,
)

/** match, date, minute, play time, time, type, team, player, assist, note, counts. */
const val CSV_COLUMN_COUNT = 11

/** Byte order mark: without it Excel reads UTF-8 as the local code page and breaks Polish letters. */
private const val BOM = "\uFEFF"

private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * [export] as CSV: one row per event, notes and structural events included. Starts with a byte
 * order mark, so it must be written as UTF-8; separated by semicolons (the default of Polish
 * Excel), lines end with CRLF. Players are names as typed until team rosters (phase 5).
 */
fun matchCsvOf(export: MatchExport, labels: CsvLabels, zone: ZoneId): String {
    require(labels.columns.size == CSV_COLUMN_COUNT) { "Expected $CSV_COLUMN_COUNT column names" }
    val home = export.homeTeam ?: labels.homeTeam
    val away = export.awayTeam ?: labels.awayTeam
    val match = "$home – $away"

    val rows = export.events.map { event ->
        val time = Instant.ofEpochMilli(event.recordedAt).atZone(zone)
        listOf(
            match,
            DATE.format(time),
            footballMinute(event.playTimeMillis),
            formatPlayTime(event.playTimeMillis),
            TIME.format(time),
            labels.typeNames.getValue(event.type),
            when (event.team) {
                Team.HOME -> home
                Team.AWAY -> away
                null -> ""
            },
            event.player.orEmpty(),
            event.assist.orEmpty(),
            event.note.orEmpty(),
            if (event.type == EventType.GOAL) (if (event.counted) labels.yes else labels.no) else "",
        )
    }
    return BOM + (listOf(labels.columns) + rows).joinToString("") { row -> row.joinToString(";") { csvField(it) } + "\r\n" }
}

/** Quoted only when needed: a separator, a quote or a line break inside (quotes doubled). */
internal fun csvField(value: String): String =
    if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value
