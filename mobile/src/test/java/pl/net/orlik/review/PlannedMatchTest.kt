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

    @Test
    fun theOnlyPlannedMatch_isTakenOver() {
        assertEquals("p1", plannedMatchFor(5_000, listOf(PlannedMatch("p1", 1_000))))
    }

    @Test
    fun noPlannedMatch_takesNothing() {
        assertNull(plannedMatchFor(5_000, emptyList()))
    }

    @Test
    fun severalPlannedMatches_areLeftToChoose() {
        assertNull(plannedMatchFor(5_000, listOf(PlannedMatch("p1", 1_000), PlannedMatch("p2", 2_000))))
    }

    @Test
    fun matchPlannedAfterTheStart_isNotForThisMatch() {
        // An old match waiting on the watch must not take the lineup planned for the next one
        assertNull(plannedMatchFor(5_000, listOf(PlannedMatch("p1", 6_000))))
    }

    @Test
    fun onlyMatchesPlannedBeforeTheStart_count() {
        assertEquals("p1", plannedMatchFor(5_000, listOf(PlannedMatch("p1", 1_000), PlannedMatch("p2", 6_000))))
    }

    private fun synced(id: String, home: String? = null, players: Int = 0) = MatchListItem(id, home, null, 1_000, 0, 3, players)
    private fun planned(id: String, home: String? = null, players: Int = 0) = MatchListItem(id, home, null, null, 0, 0, players)

    @Test
    fun matchWithoutLineup_isOfferedPlannedMatchesWithSomethingInThem() {
        val matches = listOf(planned("p1", "Orły"), planned("p2", players = 5), planned("empty"), synced("m1"))
        assertEquals(listOf("p1", "p2"), plannedChoices(matches, "m1").map { it.matchId })
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
