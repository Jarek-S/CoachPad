package pl.net.orlik.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.net.orlik.shared.sync.QueueSummary
import pl.net.orlik.sync.QueueSummarySource

sealed interface SyncUiState {
    data object Loading : SyncUiState

    /** The watch has never published a summary (e.g. the watch app is not installed yet). */
    data object NoWatchData : SyncUiState

    data class Watch(val summary: QueueSummary) : SyncUiState
}

class SyncViewModel(source: QueueSummarySource) : ViewModel() {

    val state: StateFlow<SyncUiState> = source.summaries
        .map { if (it == null) SyncUiState.NoWatchData else SyncUiState.Watch(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState.Loading)

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { SyncViewModel(QueueSummarySource(app)) }
        }
    }
}
