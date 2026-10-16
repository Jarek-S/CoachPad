package pl.net.orlik.export

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * Name of an export file from the teams and the match date, e.g. "Orly-RywaleFC_2026-10-11.csv":
 * letters and digits only, Polish letters without accents, so any phone or computer takes it.
 * [homeTeam] / [awayTeam] are the names as shown (the default ones while not set).
 */
fun exportFileName(export: MatchExport, homeTeam: String, awayTeam: String, extension: String, zone: ZoneId): String {
    val date = (export.startedAt ?: export.events.firstOrNull()?.recordedAt)
        ?.let { DATE.format(Instant.ofEpochMilli(it).atZone(zone)) }
    val teams = listOf(homeTeam, awayTeam).map(::fileNamePart).filter { it.isNotEmpty() }.joinToString("-")
    return listOf(teams, date).filter { !it.isNullOrEmpty() }.joinToString("_").ifEmpty { "match" } + ".$extension"
}

private fun fileNamePart(name: String): String =
    Normalizer.normalize(name.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
        .filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
