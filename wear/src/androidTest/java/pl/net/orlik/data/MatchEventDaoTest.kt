package pl.net.orlik.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.net.orlik.shared.EventType

@RunWith(AndroidJUnit4::class)
class MatchEventDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MatchEventDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        dao = db.matchEventDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getByMatch_returnsOnlyThatMatch_sortedByTime() = runBlocking {
        dao.insert(event("b", "m1", 200, type = null, audio = "b.m4a"))
        dao.insert(event("a", "m1", 100, type = EventType.MATCH_START))
        dao.insert(event("c", "m2", 50, type = EventType.MATCH_START))

        assertEquals(listOf("a", "b"), dao.getByMatch("m1").map { it.id })
    }

    @Test
    fun getPending_returnsAllMatches_sortedByTime() = runBlocking {
        dao.insert(event("x", "m2", 300, type = EventType.MATCH_END))
        dao.insert(event("y", "m1", 100, type = EventType.MATCH_START))

        assertEquals(listOf("y", "x"), dao.getPending().map { it.id })
    }

    @Test
    fun enumAndNulls_roundTrip() = runBlocking {
        val recording = event("r", "m1", 100, type = null, audio = "r.m4a")
        val pause = event("p", "m1", 200, type = EventType.MATCH_PAUSE)
        dao.insert(recording)
        dao.insert(pause)

        assertEquals(listOf(recording, pause), dao.getByMatch("m1"))
    }

    @Test
    fun getLatestStartedMatchId_returnsNewestStart() = runBlocking {
        assertEquals(null, dao.getLatestStartedMatchId())

        dao.insert(event("a", "m1", 100, type = EventType.MATCH_START))
        dao.insert(event("b", "m2", 300, type = EventType.MATCH_START))
        dao.insert(event("c", "m1", 500, type = null, audio = "c.m4a"))

        assertEquals("m2", dao.getLatestStartedMatchId())
    }

    private fun event(id: String, matchId: String, at: Long, type: EventType?, audio: String? = null) =
        MatchEvent(id = id, matchId = matchId, recordedAt = at, type = type, audioFileRef = audio)
}
