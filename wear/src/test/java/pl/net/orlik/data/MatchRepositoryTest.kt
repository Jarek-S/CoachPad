package pl.net.orlik.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.net.orlik.shared.EventType
import pl.net.orlik.shared.Pause

class MatchRepositoryTest {

    private val dao = FakeDao()
    private val repository = MatchRepository(dao, clock = { 1_000 })

    @Test
    fun startMatch_recordsMatchStart() = runBlocking {
        val match = repository.startMatch()

        val event = dao.events.single()
        assertEquals(EventType.MATCH_START, event.type)
        assertEquals(match.matchId, event.matchId)
        assertEquals(1_000L, event.recordedAt)
        assertNull(event.audioFileRef)
    }

    @Test
    fun startedMatch_isFoundAfterRestart() = runBlocking {
        val started = repository.startMatch()

        // A new repository over the same data simulates an app restart
        assertEquals(started, MatchRepository(dao).findActiveMatch())
    }

    @Test
    fun endMatch_recordsMatchEnd_andMatchIsNoLongerActive() = runBlocking {
        val match = repository.startMatch()
        repository.endMatch(match)

        val end = dao.events.last()
        assertEquals(EventType.MATCH_END, end.type)
        assertEquals(match.matchId, end.matchId)
        assertNull(end.audioFileRef)
        assertNull(repository.findActiveMatch())
    }

    @Test
    fun endMatch_duringPause_endsMatch() = runBlocking {
        val match = repository.startMatch()
        dao.insert(MatchEvent("p", match.matchId, 1_000, EventType.MATCH_PAUSE, audioFileRef = null))
        repository.endMatch(match)

        assertNull(repository.findActiveMatch())
    }

    @Test
    fun pauseAndResume_areRecorded_andRestored() = runBlocking {
        var now = 1_000L
        val repository = MatchRepository(dao, clock = { now })
        val started = repository.startMatch()

        now = 2_000
        val paused = repository.pauseMatch(started)
        assertTrue(paused.isPaused)
        assertEquals(paused, repository.findActiveMatch())

        now = 3_000
        val resumed = repository.resumeMatch(paused)
        assertFalse(resumed.isPaused)
        assertEquals(listOf(Pause(2_000, 3_000)), resumed.pauses)
        assertEquals(resumed, repository.findActiveMatch())
        assertEquals(
            listOf(EventType.MATCH_START, EventType.MATCH_PAUSE, EventType.MATCH_RESUME),
            dao.events.map { it.type },
        )
    }

    @Test
    fun emptyQueue_noActiveMatch() = runBlocking {
        assertNull(repository.findActiveMatch())
    }

    /** In-memory DAO: enough to test repository logic without Room or a device. */
    private class FakeDao : MatchEventDao {
        val events = mutableListOf<MatchEvent>()

        override suspend fun insert(event: MatchEvent) {
            events += event
        }

        override suspend fun getByMatch(matchId: String) =
            events.filter { it.matchId == matchId }.sortedBy { it.recordedAt }

        override suspend fun getPending() = events.sortedBy { it.recordedAt }

        override suspend fun getLatestStartedMatchId() =
            events.filter { it.type == EventType.MATCH_START }.maxByOrNull { it.recordedAt }?.matchId
    }
}
