package pl.net.orlik.export

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType

class MatchCsvTest {

    private val zone = ZoneId.of("Europe/Warsaw")

    private val labels = CsvLabels(
        homeTeam = "Gospodarze",
        awayTeam = "Goście",
        columns = "mecz;data;minuta;czas_gry;godzina;typ;druzyna;zawodnik;asysta;notatka;liczy_sie;nagranie".split(";"),
        typeNames = mapOf(
            EventType.MATCH_START to "Początek meczu",
            EventType.GOAL to "Gol",
            EventType.GOAL_DISALLOWED to "Gol nieuznany",
            EventType.NOTE to "Notatka",
        ),
        yes = "tak",
        no = "nie",
    )

    private val kickOff = LocalDateTime.of(2026, 10, 11, 14, 0, 3).atZone(zone).toInstant().toEpochMilli()

    private fun play(minutes: Int, seconds: Int = 0) = (minutes * 60 + seconds) * 1_000L

    private val export = MatchExport(
        matchId = "m1",
        homeTeam = "Orły",
        awayTeam = "Rywale FC",
        startedAt = kickOff,
        endedAt = null,
        score = Score(home = 1, away = 0),
        events = listOf(
            ExportEvent("start", EventType.MATCH_START, null, kickOff, 0, note = null),
            ExportEvent(
                "h1", EventType.GOAL, Team.HOME, kickOff + play(12, 37), play(12, 34), note = "Marcin",
                audioFileRef = "h1.m4a", audioFile = "2026-10-11_14-12-40.m4a",
            ),
            ExportEvent("a1", EventType.GOAL, Team.AWAY, kickOff + play(38), play(38), note = null, cancelledBy = "no"),
            ExportEvent("no", EventType.GOAL_DISALLOWED, Team.AWAY, kickOff + play(41), play(41), note = "Spalony", cancelsGoalId = "a1"),
            ExportEvent("n1", EventType.NOTE, null, kickOff + play(50), play(50), note = "Kuba; za \"Marcina\"\nprawa strona"),
        ),
        unreviewed = 0,
    )

    @Test
    fun oneRowPerEvent_withBomAndCrlf() {
        val expected = "﻿" +
            "mecz;data;minuta;czas_gry;godzina;typ;druzyna;zawodnik;asysta;notatka;liczy_sie;nagranie\r\n" +
            "Orły – Rywale FC;2026-10-11;1';00:00;14:00:03;Początek meczu;;;;;;\r\n" +
            "Orły – Rywale FC;2026-10-11;13';12:34;14:12:40;Gol;Orły;;;Marcin;tak;2026-10-11_14-12-40.m4a\r\n" +
            "Orły – Rywale FC;2026-10-11;39';38:00;14:38:03;Gol;Rywale FC;;;;nie;\r\n" +
            "Orły – Rywale FC;2026-10-11;42';41:00;14:41:03;Gol nieuznany;Rywale FC;;;Spalony;;\r\n" +
            "Orły – Rywale FC;2026-10-11;51';50:00;14:50:03;Notatka;;;;\"Kuba; za \"\"Marcina\"\"\nprawa strona\";;\r\n"

        assertEquals(expected, matchCsvOf(export, labels, zone))
    }

    @Test
    fun unnamedTeams_useTheDefaultNames() {
        val unnamed = export.copy(homeTeam = null, awayTeam = null, events = export.events.subList(1, 2))

        assertTrue(matchCsvOf(unnamed, labels, zone).contains("\r\nGospodarze – Goście;2026-10-11;13';12:34;14:12:40;Gol;Gospodarze;"))
    }

    @Test
    fun field_quotedOnlyWhenNeeded() {
        assertEquals("Orły", csvField("Orły"))
        assertEquals("\"a;b\"", csvField("a;b"))
        assertEquals("\"powiedział \"\"gol\"\"\"", csvField("powiedział \"gol\""))
    }

    @Test
    fun fileName_fromTeamsAndDate_withoutPolishLetters() {
        assertEquals("Orly-RywaleFC_2026-10-11.csv", exportFileName(export, "Orły", "Rywale FC", "csv", zone))
        assertEquals("ZolteLwy-Goscie_2026-10-11.json", exportFileName(export, "Żółte Lwy!", "Goście", "json", zone))
        assertEquals("match.csv", exportFileName(export.copy(startedAt = null, events = emptyList()), "", "", "csv", zone))
    }
}
