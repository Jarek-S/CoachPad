package pl.net.orlik.export

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType

class ShareTextTest {

    private val zone = ZoneId.of("Europe/Warsaw")

    private val polish = ShareTextLabels(
        homeTeam = "Gospodarze",
        awayTeam = "Goście",
        datePattern = "EEEE, d MMMM yyyy, HH:mm",
        locale = Locale.forLanguageTag("pl"),
        notCounted = "nie liczy się (gol nieuznany, %s)",
        disallowedGoal = "Gol nieuznany",
        playerWithAssist = "%1${'$'}s (asysta: %2${'$'}s)",
        assistOnly = "asysta: %s",
    )

    private val english = ShareTextLabels(
        homeTeam = "Home",
        awayTeam = "Away",
        datePattern = "EEEE, d MMMM yyyy, HH:mm",
        locale = Locale.ENGLISH,
        notCounted = "does not count (disallowed, %s)",
        disallowedGoal = "Disallowed goal",
        playerWithAssist = "%1${'$'}s (assist: %2${'$'}s)",
        assistOnly = "assist: %s",
    )

    private val kickOff = LocalDateTime.of(2026, 10, 11, 14, 0, 3).atZone(zone).toInstant().toEpochMilli()

    private fun play(minutes: Int, seconds: Int = 0) = (minutes * 60 + seconds) * 1_000L

    private fun event(
        id: String,
        type: EventType,
        playTime: Long,
        team: Team? = null,
        note: String? = null,
        cancelledBy: String? = null,
        cancelsGoalId: String? = null,
        player: String? = null,
        assist: String? = null,
    ) = ExportEvent(id, type, team, kickOff + playTime, playTime, note, cancelledBy, cancelsGoalId, player, assist)

    private val export = MatchExport(
        matchId = "m1",
        homeTeam = "Orły",
        awayTeam = "Rywale FC",
        startedAt = kickOff,
        endedAt = null,
        score = Score(home = 2, away = 0),
        events = listOf(
            event("start", EventType.MATCH_START, 0),
            event("h1", EventType.GOAL, play(12, 34), Team.HOME, player = "Marcin", assist = "Jurek"),
            event("y1", EventType.YELLOW_CARD, play(27, 10), Team.AWAY, player = "Kowal"),
            event("n1", EventType.NOTE, play(30), note = "zmiana: Kuba za Marcina"),
            event("a1", EventType.GOAL, play(38, 5), Team.AWAY, cancelledBy = "no"),
            event("no", EventType.GOAL_DISALLOWED, play(41, 20), Team.AWAY, note = "Spalony", cancelsGoalId = "a1"),
            event("h2", EventType.GOAL, play(66, 0), Team.HOME, note = "samobój"),
            event("r1", EventType.RED_CARD, play(80, 0), Team.HOME),
            event("end", EventType.MATCH_END, play(90)),
        ),
        unreviewed = 0,
    )

    @Test
    fun polish() {
        assertEquals(
            """
            Orły – Rywale FC  2:0
            niedziela, 11 października 2026, 14:00

            13' ⚽ Orły – Marcin (asysta: Jurek)
            28' 🟨 Rywale FC – Kowal
            39' ⚽ Rywale FC – nie liczy się (gol nieuznany, 42')
            67' ⚽ Orły – samobój
            81' 🟥 Orły
            """.trimIndent(),
            shareTextOf(export, polish, zone),
        )
    }

    @Test
    fun english_withDefaultTeamNames() {
        val unnamed = export.copy(homeTeam = null, awayTeam = null, events = export.events.take(3))

        assertEquals(
            """
            Home – Away  2:0
            Sunday, 11 October 2026, 14:00

            13' ⚽ Home – Marcin (assist: Jurek)
            28' 🟨 Away – Kowal
            """.trimIndent(),
            shareTextOf(unnamed, english, zone),
        )
    }

    @Test
    fun disallowedGoalWithoutAGoal_hasItsOwnLine() {
        val alone = export.copy(
            events = listOf(event("no", EventType.GOAL_DISALLOWED, play(41, 20), Team.AWAY, note = "Spalony")),
        )

        assertEquals("42' 🚫 Rywale FC – Gol nieuznany – Spalony", shareTextOf(alone, polish, zone).lines().last())
    }

    @Test
    fun cancelledGoalWithANote_keepsTheNote() {
        val noted = export.copy(
            events = listOf(
                event("a1", EventType.GOAL, play(38, 5), Team.AWAY, note = "z wolnego", cancelledBy = "no", player = "Kowal"),
                event("no", EventType.GOAL_DISALLOWED, play(41, 20), Team.AWAY, cancelsGoalId = "a1"),
            ),
        )

        assertEquals(
            "39' ⚽ Rywale FC – Kowal – z wolnego – nie liczy się (gol nieuznany, 42')",
            shareTextOf(noted, polish, zone).lines().last(),
        )
    }

    @Test
    fun withoutStartOrEvents_onlyTheScore() {
        val empty = export.copy(startedAt = null, events = emptyList(), score = Score(0, 0))

        assertEquals("Orły – Rywale FC  0:0", shareTextOf(empty, polish, zone))
    }

    @Test
    fun footballMinute_isTheMinuteBeingPlayed() {
        assertEquals("1'", footballMinute(0))
        assertEquals("1'", footballMinute(play(0, 59)))
        assertEquals("2'", footballMinute(play(1)))
        assertEquals("13'", footballMinute(play(12, 34)))
    }
}
