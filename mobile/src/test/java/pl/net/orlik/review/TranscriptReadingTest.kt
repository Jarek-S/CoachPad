package pl.net.orlik.review

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.net.orlik.data.LineupPlayer
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType

class TranscriptReadingTest {

    private var nextId = 0L
    private fun player(team: Team, number: Int?, name: String) = LineupPlayer(++nextId, "m", team, number, name)

    private val lineup = listOf(
        player(Team.HOME, 7, "Adam"),
        player(Team.HOME, 9, "Łukasz"),
        player(Team.HOME, 10, "Kuba"),
        player(Team.HOME, 11, "Kuba S."),
        player(Team.AWAY, 4, "Benek"),
        player(Team.AWAY, 8, "Barnaba"),
        player(Team.AWAY, null, "Grzegorz"),
    )

    private fun read(text: String, players: List<LineupPlayer> = lineup) = readTranscript(text, players, "Paprotki", "Drewniaki")

    @Test
    fun goalWithAssist_teamFromTheLineup() {
        assertEquals(TranscriptReading(EventType.GOAL, Team.HOME, "Adam", "Łukasz"), read("Gol Adam, asysta Łukasz."))
        assertEquals(TranscriptReading(EventType.GOAL, Team.AWAY, "Barnaba", "Grzegorz"), read("Bramka Barnaby, asystował Grzegorz"))
    }

    @Test
    fun withoutGoalWord_aNamedPlayerIsAGoal() {
        assertEquals(TranscriptReading(EventType.GOAL, Team.HOME, "Adam", "Łukasz"), read("Adam, asysta Łukasza"))
        assertEquals(TranscriptReading(EventType.GOAL, Team.AWAY, "Benek", null), read("Benek"))
    }

    @Test
    fun typosAndMissingMarks() {
        assertEquals(TranscriptReading(EventType.GOAL, Team.HOME, "Łukasz", "Adam"), read("gol lukas asysta adam"))
        assertEquals(TranscriptReading(EventType.GOAL, Team.AWAY, "Barnaba", null), read("Gol Barnabo"))
    }

    @Test
    fun correctionReplacesTheName() {
        assertEquals(TranscriptReading(EventType.GOAL, Team.HOME, "Łukasz", "Adam"), read("Gol Adam, nie, Łukasz. Asysta Adam."))
        assertEquals(TranscriptReading(EventType.GOAL, Team.HOME, "Adam", "Kuba"), read("Gol Adam, asysta Łukasz, przepraszam, Kuba"))
        // Without a correction the first name in a role stays
        assertEquals(TranscriptReading(EventType.GOAL, Team.HOME, "Adam", null), read("Gol Adam, Adam super strzał, Łukasz się cieszy"))
    }

    @Test
    fun wholeNameBeforeFirstName() {
        assertEquals("Kuba S.", read("Gol Kuba S").player)
        assertEquals("Kuba", read("Gol Kuba").player)
    }

    @Test
    fun shirtNumber() {
        assertEquals(TranscriptReading(EventType.GOAL, Team.AWAY, "Barnaba", "Benek"), read("Gol 8, asysta 4"))
    }

    @Test
    fun cards() {
        assertEquals(TranscriptReading(EventType.YELLOW_CARD, Team.AWAY, "Benek", null), read("Żółta kartka Benek"))
        assertEquals(TranscriptReading(EventType.RED_CARD, Team.HOME, "Adam", null), read("Czerwona kartka dla Adama"))
        assertEquals(EventType.YELLOW_CARD, read("Kartka, Grzegorz").type)
    }

    @Test
    fun disallowedGoal() {
        assertEquals(TranscriptReading(EventType.GOAL_DISALLOWED, Team.AWAY, "Barnaba", null), read("Gol nieuznany, Barnaba, spalony"))
    }

    @Test
    fun noteHasNoTeamOrPlayers() {
        assertEquals(TranscriptReading(EventType.NOTE), read("Zmiana, Adam za Kubę"))
    }

    @Test
    fun assistFromTheOtherTeamIsDropped() {
        assertEquals(TranscriptReading(EventType.GOAL, Team.HOME, "Adam", null), read("Gol Adam, asysta Benek"))
    }

    @Test
    fun teamBySaidName_whenNoPlayerIsKnown() {
        assertEquals(TranscriptReading(EventType.GOAL, Team.AWAY), read("Gol dla Drewniaków"))
    }

    @Test
    fun withoutLineup_capitalisedWordsAreNames() {
        assertEquals(TranscriptReading(EventType.GOAL, null, "Piotr", "Grzegorz"), read("Gol Piotr, asysta Grzegorz.", emptyList()))
        assertEquals(TranscriptReading(EventType.GOAL, null, "Benek", "Rafał"), read("Jarek, nie, Benek, asysta Rafał", emptyList()))
    }

    @Test
    fun nothingKnown() {
        assertEquals(TranscriptReading(), read("eee yyy"))
        assertEquals(TranscriptReading(), read(""))
    }

    @Test
    fun filledFrom_keepsWhatIsSet() {
        val reading = TranscriptReading(EventType.GOAL, Team.HOME, "Adam", "Łukasz")
        assertEquals(EventDraft(EventType.GOAL, Team.HOME, player = "Adam", assist = "Łukasz"), EventDraft().filledFrom(reading))
        assertEquals(
            EventDraft(EventType.YELLOW_CARD, Team.AWAY, player = "Benek", assist = "Łukasz"),
            EventDraft(EventType.YELLOW_CARD, Team.AWAY, player = "Benek").filledFrom(reading),
        )
        assertEquals(EventDraft(note = "x"), EventDraft(note = "x").filledFrom(TranscriptReading()))
    }

    @Test
    fun levenshtein_counts() {
        assertEquals(0, levenshtein("adam", "adam"))
        assertEquals(1, levenshtein("lukas", "lukasz"))
        assertEquals(3, levenshtein("kitten", "sitting"))
    }
}
