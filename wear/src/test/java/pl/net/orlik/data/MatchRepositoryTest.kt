package pl.net.orlik.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.net.orlik.shared.EventType

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
