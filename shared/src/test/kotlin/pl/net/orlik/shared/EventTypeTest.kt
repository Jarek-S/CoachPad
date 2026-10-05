package pl.net.orlik.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class EventTypeTest {

    @Test
    fun structuralTypes_areExactlyMatchLifecycleEvents() {
        val structural = EventType.entries.filter { it.isStructural }.toSet()
        assertEquals(
            setOf(EventType.MATCH_START, EventType.MATCH_END, EventType.MATCH_PAUSE, EventType.MATCH_RESUME),
            structural,
        )
    }
}
