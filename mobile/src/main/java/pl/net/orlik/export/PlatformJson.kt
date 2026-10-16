package pl.net.orlik.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pl.net.orlik.shared.EventType

// The match as one JSON file for the team platform (design doc, section 5).

/** Raised on changes the platform cannot read the old way. */
const val PLATFORM_FORMAT_VERSION = 1

private val json = Json { prettyPrint = true }

/** [export] as the platform's JSON; times in ISO 8601 with the offset of [zone]. */
fun platformJsonOf(export: MatchExport, zone: ZoneId): String =
    json.encodeToString(JsonElement.serializer(), platformJsonObjectOf(export, zone))

internal fun platformJsonObjectOf(export: MatchExport, zone: ZoneId): JsonObject = buildJsonObject {
    put("formatVersion", PLATFORM_FORMAT_VERSION)
    put(
        "match",
        buildJsonObject {
            put("id", export.matchId)
            putTime("startedAt", export.startedAt, zone)
            putTime("endedAt", export.endedAt, zone)
            putTeam("homeTeam", export.homeTeam)
            putTeam("awayTeam", export.awayTeam)
            put(
                "score",
                buildJsonObject {
                    put("home", export.score.home)
                    put("away", export.score.away)
                },
            )
            put("events", buildJsonArray { export.events.forEach { add(eventJsonOf(it, zone)) } })
        },
    )
}

private fun eventJsonOf(event: ExportEvent, zone: ZoneId): JsonObject = buildJsonObject {
    put("id", event.id)
    put("type", event.type.name)
    event.team?.let { put("team", it.name) }
    putTime("recordedAt", event.recordedAt, zone)
    put("playTimeSeconds", event.playTimeMillis / 1_000)
    event.note?.let { put("note", it) }
    if (event.type == EventType.GOAL) put("counted", event.counted)
    event.cancelsGoalId?.let { put("cancelsGoalId", it) }
    event.audioFile?.let { put("audioFile", it) }
}

/** Fields without a value are left out, so a team not named yet has no object at all. */
private fun JsonObjectBuilder.putTeam(key: String, name: String?) {
    if (name != null) put(key, buildJsonObject { put("name", name) })
}

private fun JsonObjectBuilder.putTime(key: String, epochMillis: Long?, zone: ZoneId) {
    if (epochMillis != null) put(key, isoTimeOf(epochMillis, zone))
}

/** E.g. "2026-10-11T14:00:03+02:00": whole seconds, with the offset of [zone] at that moment. */
fun isoTimeOf(epochMillis: Long, zone: ZoneId): String =
    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        Instant.ofEpochMilli(epochMillis).truncatedTo(ChronoUnit.SECONDS).atZone(zone).toOffsetDateTime(),
    )
