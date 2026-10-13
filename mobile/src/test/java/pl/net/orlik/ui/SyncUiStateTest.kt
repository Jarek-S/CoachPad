package pl.net.orlik.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.net.orlik.shared.sync.PendingMatch
import pl.net.orlik.shared.sync.QueueSummary
import pl.net.orlik.sync.SyncStatus

class SyncUiStateTest {

    private val pending = WatchQueue.Known(QueueSummary(false, listOf(PendingMatch("m1", 0, 3, 1))))
    private val ready = SyncUiState(watch = pending, watchConnected = true)

    @Test
    fun canSync_whenThereIsSomethingAndTheWatchIsInRange() {
        assertTrue(ready.canSync)
    }

    @Test
    fun cannotSync_outOfRange_orNotCheckedYet() {
        // The summary is the last known state; a command would not get through
        assertFalse(ready.copy(watchConnected = false).canSync)
        assertFalse(ready.copy(watchConnected = null).canSync)
    }

    @Test
    fun cannotSync_duringMatch_whileRunning_orWithNothingQueued() {
        assertFalse(ready.copy(watch = WatchQueue.Known(pending.summary.copy(matchInProgress = true))).canSync)
        assertFalse(ready.copy(sync = SyncStatus.Running(0)).canSync)
        assertFalse(ready.copy(watch = WatchQueue.Known(QueueSummary(false, emptyList()))).canSync)
        assertFalse(ready.copy(watch = WatchQueue.NoWatchData).canSync)
    }
}
