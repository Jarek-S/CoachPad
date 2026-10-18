package pl.net.orlik.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.net.orlik.data.LineupPlayer
import pl.net.orlik.data.Team

class LineupTest {

    @Test
    fun parsePlayerEntry_numberThenName() {
        assertEquals(PlayerEntry(7, "Marcin"), parsePlayerEntry("7 Marcin"))
        assertEquals(PlayerEntry(10, "Kuba S."), parsePlayerEntry("  10.  Kuba   S. "))
        assertEquals(PlayerEntry(1, "Kuba"), parsePlayerEntry("1.Kuba"))
    }

    @Test
    fun parsePlayerEntry_nameAlone() {
        assertEquals(PlayerEntry(null, "Piotrek"), parsePlayerEntry("Piotrek"))
        // A number inside or after the name stays part of it
        assertEquals(PlayerEntry(null, "Marcin 7"), parsePlayerEntry("Marcin 7"))
        assertEquals(PlayerEntry(null, "7Marcin"), parsePlayerEntry("7Marcin"))
    }

    @Test
    fun parsePlayerEntry_nothingToAdd() {
        assertNull(parsePlayerEntry(""))
        assertNull(parsePlayerEntry("   "))
        assertNull(parsePlayerEntry("7"))
    }

    @Test
    fun playerEntryText_roundTrips() {
        assertEquals("7 Marcin", playerEntryText(7, "Marcin"))
        assertEquals("Piotrek", playerEntryText(null, "Piotrek"))
        assertEquals(PlayerEntry(7, "Marcin"), parsePlayerEntry(playerEntryText(7, "Marcin")))
    }

    @Test
    fun lineupOrder_byNumberThenName_withoutNumberLast() {
        fun player(number: Int?, name: String) = LineupPlayer(matchId = "m", team = Team.HOME, number = number, name = name)
        val sorted = listOf(player(null, "Piotrek"), player(10, "Kuba S."), player(null, "adam"), player(1, "Kuba"))
            .sortedWith(lineupOrder)
        assertEquals(listOf("Kuba", "Kuba S.", "adam", "Piotrek"), sorted.map { it.name })
    }
}
