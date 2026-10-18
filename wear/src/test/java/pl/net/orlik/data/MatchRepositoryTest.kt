package pl.net.orlik.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.net.orlik.settings.ShiftTimerSettings
import pl.net.orlik.shared.EventType
import pl.net.orlik.shared.Pause
import pl.net.orlik.shared.sync.PendingMatch
import pl.net.orlik.shared.sync.QueueSummary
import pl.net.orlik.timer.ShiftTimer

class MatchRepositoryTest {

    private val dao = FakeDao()
    private val settings = FakeSettings()
    private val timerStore = FakeTimerStore()
    private val published = mutableListOf<QueueSummary>()
    private val publisher = QueueSummaryPublisher { published += it }
    private val repository = repository { 1_000 }

    private fun repository(clock: () -> Long) = MatchRepository(dao, settings, timerStore, publisher, clock)

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
        assertEquals(started, MatchRepository(dao, settings, timerStore, publisher).findActiveMatch())
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
        assertEquals(started, MatchRepository(dao, settings, timerStore, publisher).findActiveMatch())
    }

    @Test
    fun pauseAndResume_keepShiftTimer() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = true)
        val started = repository.startMatch()

        val resumed = repository.resumeMatch(repository.pauseMatch(started))
        assertEquals(started.shiftTimer, resumed.shiftTimer)
    }

    @Test
    fun confirmShift_whileAwaiting_restartsAndSavesTimer() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = true, intervalMinutes = 5)
        var now = 0L
        val repository = repository { now }
        val started = repository.startMatch()

        now = 6 * 60_000L
        val confirmed = repository.confirmShift(started)

        assertEquals(ShiftTimer(intervalMillis = 5 * 60_000L, lastResetPlayMillis = 6 * 60_000L), confirmed.shiftTimer)
        assertEquals(confirmed, MatchRepository(dao, settings, timerStore, publisher).findActiveMatch())
    }

    @Test
    fun confirmShift_whileRunning_changesNothing() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = true, intervalMinutes = 5)
        var now = 0L
        val repository = repository { now }
        val started = repository.startMatch()

        now = 2 * 60_000L
        assertEquals(started, repository.confirmShift(started))
    }

    @Test
    fun confirmShift_duringPause_nextIntervalCountsFromResume() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = true, intervalMinutes = 5)
        var now = 0L
        val repository = repository { now }
        val started = repository.startMatch()

        now = 6 * 60_000L
        val paused = repository.pauseMatch(started)
        now = 9 * 60_000L
        val confirmed = repository.confirmShift(paused)

        // Play time stood at 6:00 during the pause, so the timer restarts from there
        assertEquals(6 * 60_000L, confirmed.shiftTimer?.lastResetPlayMillis)
    }

    @Test
    fun toggleShiftTimer_on_countsFullIntervalFromNow_andSavesSettings() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = false, intervalMinutes = 7)
        var now = 0L
        val repository = repository { now }
        val started = repository.startMatch()

        now = 10 * 60_000L
        val toggled = repository.toggleShiftTimer(started)

        assertEquals(ShiftTimer(intervalMillis = 7 * 60_000L, lastResetPlayMillis = 10 * 60_000L), toggled.shiftTimer)
        assertEquals(ShiftTimerSettings(enabled = true, intervalMinutes = 7), settings.current)
        assertEquals(toggled, MatchRepository(dao, settings, timerStore, publisher).findActiveMatch())
    }

    @Test
    fun toggleShiftTimer_off_removesTimerEvenDuringAlarm_andSavesSettings() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = true, intervalMinutes = 5)
        var now = 0L
        val repository = repository { now }
        val started = repository.startMatch()

        now = 6 * 60_000L
        val toggled = repository.toggleShiftTimer(started)

        assertNull(toggled.shiftTimer)
        assertFalse(settings.current.enabled)
        assertEquals(toggled, MatchRepository(dao, settings, timerStore, publisher).findActiveMatch())
    }

    @Test
    fun toggleShiftTimer_againAndAgain_eachOnStartsAfresh() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = true, intervalMinutes = 5)
        var now = 0L
        val repository = repository { now }
        var match = repository.startMatch()

        now = 3 * 60_000L
        match = repository.toggleShiftTimer(match)
        now = 4 * 60_000L
        match = repository.toggleShiftTimer(match)

        assertEquals(4 * 60_000L, match.shiftTimer?.lastResetPlayMillis)
        assertTrue(settings.current.enabled)
    }

    @Test
    fun toggleShiftTimer_duringPause_countsFromPausedPlayTime() = runBlocking {
        var now = 0L
        val repository = repository { now }
        val started = repository.startMatch()

        now = 6 * 60_000L
        val paused = repository.pauseMatch(started)
        now = 9 * 60_000L

        assertEquals(6 * 60_000L, repository.toggleShiftTimer(paused).shiftTimer?.lastResetPlayMillis)
    }

    @Test
    fun endMatch_clearsTimerSnapshot() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = true)
        val match = repository.startMatch()
        repository.endMatch(match)

        assertNull(timerStore.load(match.matchId))
    }

    @Test
    fun everyQueueChange_publishesSummary() = runBlocking {
        var now = 1_000L
        val repository = repository { now }
        val match = repository.startMatch()
        assertEquals(QueueSummary(true, listOf(PendingMatch(match.matchId, 1_000, 1, 0))), published.last())

        now = 2_000
        repository.saveVoiceRecording(repository.newVoiceRecording(match))
        val paused = repository.pauseMatch(match)
        repository.endMatch(paused)

        assertEquals(4, published.size)
        assertEquals(QueueSummary(false, listOf(PendingMatch(match.matchId, 1_000, 4, 1))), published.last())
    }

    @Test
    fun confirmShift_doesNotPublish() = runBlocking {
        settings.current = ShiftTimerSettings(enabled = true, intervalMinutes = 5)
        var now = 0L
        val repository = repository { now }
        val started = repository.startMatch()

        now = 6 * 60_000L
        repository.confirmShift(started)
        // The timer lives outside the queue, so the summary does not change
        assertEquals(1, published.size)
    }

    @Test
    fun deleteMatch_republishesSummary() = runBlocking {
        val match = repository.startMatch()
        repository.endMatch(match)

        assertEquals(2, repository.deleteMatch(match.matchId).size)
        assertEquals(QueueSummary(false, emptyList()), published.last())
    }

    @Test
    fun emptyQueue_noActiveMatch() = runBlocking {
        assertNull(repository.findActiveMatch())
    }
}
