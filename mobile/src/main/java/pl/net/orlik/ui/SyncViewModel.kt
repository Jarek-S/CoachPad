package pl.net.orlik.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.net.orlik.data.PhoneDatabase
import pl.net.orlik.shared.sync.QueueSummary
import pl.net.orlik.sync.PhoneSync
import pl.net.orlik.sync.QueueSummarySource
import pl.net.orlik.sync.SyncStatus

/** What the watch reports about its queue. */
sealed interface WatchQueue {
    data object Loading : WatchQueue

    /** The watch has never published a summary (e.g. the watch app is not installed yet). */
    data object NoWatchData : WatchQueue

    data class Known(val summary: QueueSummary) : WatchQueue
}

data class SyncUiState(
    val watch: WatchQueue = WatchQueue.Loading,
    val sync: SyncStatus = SyncStatus.Idle,
    val eventsOnPhone: Int = 0,
) {
    /**
     * Sync and delete buttons are active only when there is something on the watch and it would
     * accept the command: not during a match there, and not while a sync is running.
     */
    val canSync: Boolean
        get() = watch is WatchQueue.Known &&
            !watch.summary.matchInProgress &&
            watch.summary.matches.isNotEmpty() &&
            sync !is SyncStatus.Running
}

class SyncViewModel(
    summaries: Flow<QueueSummary?>,
    eventsOnPhone: Flow<Int>,
    private val phoneSync: PhoneSync,
) : ViewModel() {

    val state: StateFlow<SyncUiState> = combine(
        summaries.map { if (it == null) WatchQueue.NoWatchData else WatchQueue.Known(it) },
        phoneSync.status,
        eventsOnPhone,
        ::SyncUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    /** Syncs one match, or all pending ones when [matchId] is null. */
    fun sync(matchId: String? = null) = phoneSync.start(matchId)

    /** Deletes what is still queued on the watch for [matchId]. */
    fun deleteMatch(matchId: String) = phoneSync.deleteMatch(matchId)

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SyncViewModel(
                    summaries = QueueSummarySource(app).summaries,
                    eventsOnPhone = PhoneDatabase.get(app).receivedEventDao().count(),
                    phoneSync = PhoneSync.get(app),
                )
            }
        }
    }
}
