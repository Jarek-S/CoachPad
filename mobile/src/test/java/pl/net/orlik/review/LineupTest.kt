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

    private val lineup = listOf(
        LineupPlayer(1, "m", Team.HOME, 11, "Mateusz"),
        LineupPlayer(2, "m", Team.HOME, 7, "Marcin"),
        LineupPlayer(3, "m", Team.HOME, 1, "Kuba"),
        LineupPlayer(4, "m", Team.HOME, null, "Tomasz"),
    )

    @Test
    fun playerSuggestions_emptyGivesWholeLineupInOrder() {
        assertEquals(listOf("Kuba", "Marcin", "Mateusz", "Tomasz"), playerSuggestions(lineup, " ").map { it.name })
    }

    @Test
    fun playerSuggestions_byNumberPrefix() {
        assertEquals(listOf("Kuba", "Mateusz"), playerSuggestions(lineup, "1").map { it.name })
        assertEquals(listOf("Marcin"), playerSuggestions(lineup, "7").map { it.name })
    }

    @Test
    fun playerSuggestions_byNamePart_startFirst() {
        // "Tomasz" has "ma" only inside, so it comes after the names starting with it
        assertEquals(listOf("Marcin", "Mateusz", "Tomasz"), playerSuggestions(lineup, "MA").map { it.name })
        assertEquals(listOf("Mateusz"), playerSuggestions(lineup, "teu").map { it.name })
    }

    @Test
    fun playerSuggestions_noneOnceChosenOrUnknown() {
        assertEquals(emptyList<LineupPlayer>(), playerSuggestions(lineup, "marcin"))
        assertEquals(emptyList<LineupPlayer>(), playerSuggestions(lineup, "Zenek"))
    }
}
