package pl.net.orlik.export

import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType

class PlatformJsonTest {

    private val zone = ZoneId.of("Europe/Warsaw")

    private fun at(hour: Int, minute: Int, second: Int, millis: Int = 0) =
        LocalDateTime.of(2026, 10, 11, hour, minute, second).atZone(zone).toInstant().toEpochMilli() + millis

    private val export = MatchExport(
        matchId = "m1",
        homeTeam = "Orły",
        awayTeam = "Rywale FC",
        startedAt = at(14, 0, 3),
        endedAt = at(15, 41, 20),
        score = Score(home = 1, away = 0),
        events = listOf(
            ExportEvent("start", EventType.MATCH_START, null, at(14, 0, 3), 0, note = null),
            ExportEvent(
                "g1", EventType.GOAL, Team.HOME, at(14, 12, 40, 600), 754_900, note = "strzał z woleja",
                player = "Marcin", assist = "Jurek",
            ),
            ExportEvent("g2", EventType.GOAL, Team.AWAY, at(14, 39, 0), 2_337_000, note = null, cancelledBy = "no"),
            ExportEvent("no", EventType.GOAL_DISALLOWED, Team.AWAY, at(14, 42, 0), 2_462_000, note = "Spalony", cancelsGoalId = "g2"),
        ),
        unreviewed = 0,
    )

    @Test
    fun matchesTheFormatInTheDesignDoc() {
        val expected = """
            {"formatVersion":1,"match":{"id":"m1","startedAt":"2026-10-11T14:00:03+02:00",
            "endedAt":"2026-10-11T15:41:20+02:00","homeTeam":{"name":"Orły"},"awayTeam":{"name":"Rywale FC"},
            "score":{"home":1,"away":0},"events":[
            {"id":"start","type":"MATCH_START","recordedAt":"2026-10-11T14:00:03+02:00","playTimeSeconds":0},
            {"id":"g1","type":"GOAL","team":"HOME","recordedAt":"2026-10-11T14:12:40+02:00","playTimeSeconds":754,
            "player":{"name":"Marcin"},"assist":{"name":"Jurek"},"note":"strzał z woleja","counted":true},
            {"id":"g2","type":"GOAL","team":"AWAY","recordedAt":"2026-10-11T14:39:00+02:00","playTimeSeconds":2337,
            "counted":false},
            {"id":"no","type":"GOAL_DISALLOWED","team":"AWAY","recordedAt":"2026-10-11T14:42:00+02:00",
            "playTimeSeconds":2462,"note":"Spalony","cancelsGoalId":"g2"}]}}
        """.trimIndent().replace("\n", "")

        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(platformJsonOf(export, zone)))
    }

    @Test
    fun unnamedTeamsAndMissingTimes_areLeftOut() {
        val bare = export.copy(homeTeam = null, awayTeam = null, startedAt = null, endedAt = null, events = emptyList())

        val match = platformJsonObjectOf(bare, zone).getValue("match").jsonObject

        assertEquals(setOf("id", "score", "events"), match.keys)
    }

    @Test
    fun offsetFollowsTheDate() {
        // Poland is on winter time from the last Sunday of October
        val winter = LocalDateTime.of(2026, 11, 7, 10, 0).atZone(zone).toInstant().toEpochMilli()

        assertEquals("2026-11-07T10:00:00+01:00", isoTimeOf(winter, zone))
    }

    @Test
    fun polishLettersStayReadable() {
        assertFalse(platformJsonOf(export, zone).contains("\\u"))
    }

    @Test
    fun matchFromNotes_hasTheDayItWasPlayed_noTimes() {
        val match = platformJsonObjectOf(export.copy(dateOnly = true), zone).getValue("match").jsonObject
        assertEquals("\"2026-10-11\"", match.getValue("playedOn").toString())
        assertFalse("startedAt" in match)
        assertFalse("endedAt" in match)
    }

    @Test
    fun typedEvents_leaveOutWhatIsNotKnown() {
        val typed = export.copy(
            events = listOf(
                ExportEvent("t1", EventType.GOAL, Team.HOME, null, 22 * 60_000L, note = null, minuteOnly = true),
                ExportEvent("t2", EventType.NOTE, null, null, null, note = "z kartki"),
            ),
        )
        val events = platformJsonObjectOf(typed, zone).getValue("match").jsonObject.getValue("events").toString()

        assertEquals(
            Json.parseToJsonElement(
                """[{"id":"t1","type":"GOAL","team":"HOME","playTimeSeconds":1320,"counted":true},""" +
                    """{"id":"t2","type":"NOTE","note":"z kartki"}]""",
            ),
            Json.parseToJsonElement(events),
        )
    }
}
