package pl.net.orlik.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.net.orlik.review.LiveAction
import pl.net.orlik.shared.EventType

class LiveRecorderTest {

    private val dao = FakeDao()
    private var clock = 1_000L
    private val recorder = LiveRecorder(dao) { clock++ }

    @Test
    fun recordsStructuralEvents_onlyWhereTheyFit() = runBlocking {
        recorder.record("m", LiveAction.START)
        recorder.record("m", LiveAction.START)
        recorder.record("m", LiveAction.PAUSE)
        recorder.record("m", LiveAction.PAUSE)
        recorder.record("m", LiveAction.RESUME)
        recorder.record("m", LiveAction.END)
        recorder.record("m", LiveAction.PAUSE)

        assertEquals(
            listOf(EventType.MATCH_START, EventType.MATCH_PAUSE, EventType.MATCH_RESUME, EventType.MATCH_END),
            dao.events.map { it.type },
        )
        // Structural events need no review
        assertEquals(setOf(ReviewStatus.CONFIRMED), dao.events.map { it.status }.toSet())
        assertEquals(listOf(1_000L, 1_001L, 1_002L, 1_003L), dao.events.map { it.recordedAt })
    }
}
