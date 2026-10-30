package pl.net.orlik.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import pl.net.orlik.data.MatchListItem

class PlannedMatchTest {

    private fun synced(id: String, home: String? = null, players: Int = 0) = MatchListItem(id, home, null, 1_000, 0, 3, players)
    private fun planned(id: String, home: String? = null, players: Int = 0, plannedAt: Long = 0) =
        MatchListItem(id, home, null, null, 0, 0, players, plannedAt = plannedAt)

    @Test
    fun matchWithoutLineup_isOfferedPlannedMatchesWithSomethingInThem() {
        val matches = listOf(planned("p1", "Orły"), planned("p2", players = 5), planned("empty"), synced("m1"))
        assertEquals(listOf("p1", "p2"), plannedChoices(matches, "m1").map { it.matchId })
    }

    @Test
    fun choices_areTheSoonestFirst() {
        val matches = listOf(planned("later", "Orły", plannedAt = 3_000), planned("sooner", "Orły", plannedAt = 2_000), synced("m1"))
        assertEquals(listOf("sooner", "later"), plannedChoices(matches, "m1").map { it.matchId })
    }

    @Test
    fun matchRecordedOnThePhone_isOfferedNothing() {
        val live = MatchListItem("m1", null, null, 1_000, 0, 0, plannedAt = 500)
        assertEquals(emptyList<MatchListItem>(), plannedChoices(listOf(planned("p1", "Orły"), live), "m1"))
    }

    @Test
    fun onTheDay_areThePlansForTheDayTheMatchStarted() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        fun day(d: Int) = LocalDate.of(2026, 10, d).atStartOfDay(warsaw).toInstant().toEpochMilli()
        // Synced days later, started late in the evening of the 28th
        val startedAt = LocalDateTime.of(2026, 10, 28, 23, 30).atZone(warsaw).toInstant().toEpochMilli()
        val choices = listOf(planned("p27", "A", plannedAt = day(27)), planned("p28", "B", plannedAt = day(28)), planned("p29", "C", plannedAt = day(29)))
        assertEquals(listOf("p28"), plannedOnTheDay(choices, startedAt, warsaw).map { it.matchId })
    }

    @Test
    fun matchWithTeamNamesOrPlayers_isOfferedNothing() {
        assertEquals(emptyList<MatchListItem>(), plannedChoices(listOf(planned("p1", "Orły"), synced("m1", home = "Sokoły")), "m1"))
        assertEquals(emptyList<MatchListItem>(), plannedChoices(listOf(planned("p1", "Orły"), synced("m1", players = 2)), "m1"))
    }

    @Test
    fun dayStart_isTheStartOfTheChosenDayInTheZone() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        val picked = LocalDate.of(2026, 10, 30).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 30).atStartOfDay(warsaw).toInstant().toEpochMilli(), dayStartOf(picked, warsaw))
    }

    @Test
    fun utcDay_isTheLocalDateAsUtcMidnight() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        // 00:30 in Warsaw is still the previous day in UTC
        val now = LocalDateTime.of(2026, 10, 30, 0, 30).atZone(warsaw).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 30).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), utcDayOf(now, warsaw))
    }

    @Test
    fun utcDay_ofADayStart_isThePickedDay() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        val picked = LocalDate.of(2026, 11, 7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(picked, utcDayOf(dayStartOf(picked, warsaw), warsaw))
    }

    @Test
    fun plannedLater_onlyForADayAfterToday() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        val now = LocalDateTime.of(2026, 10, 30, 23, 50).atZone(warsaw).toInstant().toEpochMilli()
        fun day(d: Int) = LocalDate.of(2026, 10, d).atStartOfDay(warsaw).toInstant().toEpochMilli()
        assertFalse(isPlannedLater(day(29), now, warsaw))
        assertFalse(isPlannedLater(day(30), now, warsaw))
        assertTrue(isPlannedLater(day(31), now, warsaw))
        // A plan made before dates were chosen holds the moment it was created
        assertFalse(isPlannedLater(now - 60_000, now, warsaw))
    }
}
