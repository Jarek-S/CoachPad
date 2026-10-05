package pl.net.orlik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.net.orlik.shared.EventType
import pl.net.orlik.shared.Pause

class ActiveMatchTest {

    @Test
    fun noEvents_noActiveMatch() {
        assertNull(activeMatchFrom(emptyList()))
    }

    @Test
    fun started_isActive() {
        val events = listOf(event(100, EventType.MATCH_START), event(200, type = null))
        assertEquals(ActiveMatch("m1", startedAt = 100), activeMatchFrom(events))
    }

    @Test
    fun ended_isNotActive() {
        val events = listOf(event(100, EventType.MATCH_START), event(900, EventType.MATCH_END))
        assertNull(activeMatchFrom(events))
    }

    @Test
    fun pauses_areRebuilt_lastOneOpen() {
        val events = listOf(
            event(100, EventType.MATCH_START),
            event(200, EventType.MATCH_PAUSE),
            event(300, EventType.MATCH_RESUME),
            event(400, EventType.MATCH_PAUSE),
        )
        assertEquals(
            listOf(Pause(200, 300), Pause(400, null)),
            activeMatchFrom(events)?.pauses,
        )
    }

    private fun event(at: Long, type: EventType?) =
        MatchEvent(id = "e$at", matchId = "m1", recordedAt = at, type = type, audioFileRef = null)
}
