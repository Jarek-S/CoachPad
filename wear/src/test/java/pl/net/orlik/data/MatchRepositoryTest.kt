package pl.net.orlik.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.net.orlik.settings.SettingsStore
import pl.net.orlik.settings.ShiftTimerSettings
import pl.net.orlik.shared.EventType
import pl.net.orlik.shared.Pause
import pl.net.orlik.timer.ShiftTimer

class MatchRepositoryTest {

    private val dao = FakeDao()
    private val settings = FakeSettings()
    private val timerStore = FakeTimerStore()
    private val repository = repository { 1_000 }

    private fun repository(clock: () -> Long) = MatchRepository(dao, settings, timerStore, clock)

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
        assertEquals(started, MatchRepository(dao, settings, timerStore).findActiveMatch())
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
        val repository = repository { now }
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
    fun voiceRecording_isSavedWithStartTime_andNoType() = runBlocking {
        var now = 1_000L
        val repository = repository { now }
        val match = repository.startMatch()

        now = 5_000
        val recording = repository.newVoiceRecording(match)
        now = 12_000 // saved when recording stops; timestamp stays at start
        repository.saveVoiceRecording(recording)

        val event = dao.events.last()
        assertEquals(recording.eventId, event.id)
        assertEquals(match.matchId, event.matchId)
        assertEquals(5_000L, event.recordedAt)
        assertNull(event.type)
        assertEquals("${recording.eventId}.m4a", event.audioFileRef)
        // A recording does not affect the match state
        assertEquals(match, repository.findActiveMatch())
    }

    @Test
    fun shiftTimerDisabled_byDefault() = runBlocking {
        assertNull(repository.startMatch().shiftTimer)
    }

    @Test
    fun shiftTimerSettings_areReadAtStart() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = true, intervalMinutes = 7)

        assertEquals(ShiftTimer(intervalMillis = 7 * 60_000L), repository.startMatch().shiftTimer)
    }

    @Test
    fun restart_restoresTimerSnapshot_notCurrentSettings() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = true, intervalMinutes = 7)
        val started = repository.startMatch()

        // Settings changed during the match must not affect it
        settings.current = ShiftTimerSettings(enabled = false)
        assertEquals(started, MatchRepository(dao, settings, timerStore).findActiveMatch())
    }

    @Test
    fun pauseAndResume_keepShiftTimer() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = true)
        val started = repository.startMatch()

        val resumed = repository.resumeMatch(repository.pauseMatch(started))
        assertEquals(started.shiftTimer, resumed.shiftTimer)
    }

    @Test
    fun endMatch_clearsTimerSnapshot() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = true)
        val match = repository.startMatch()
        repository.endMatch(match)

        assertNull(timerStore.load(match.matchId))
    }

    @Test
    fun emptyQueue_noActiveMatch() = runBlocking {
        assertNull(repository.findActiveMatch())
    }

    private class FakeSettings : SettingsStore {
        var current = ShiftTimerSettings()
        override fun shiftTimerSettings() = current
        override fun saveShiftTimerSettings(settings: ShiftTimerSettings) {
            current = settings
        }
    }

    private class FakeTimerStore : MatchTimerStore {
        private var saved: Pair<String, ShiftTimer?>? = null
        override fun load(matchId: String) = saved?.takeIf { it.first == matchId }?.second
        override fun save(matchId: String, timer: ShiftTimer?) {
            saved = matchId to timer
        }
        override fun clear() {
            saved = null
        }
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
