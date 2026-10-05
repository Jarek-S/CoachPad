package pl.net.orlik.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.net.orlik.data.ActiveMatch
import pl.net.orlik.data.AppDatabase
import pl.net.orlik.data.MatchRepository

sealed interface MatchUiState {
    /** Reading the queue to find a match in progress. */
    data object Loading : MatchUiState
    data object NoMatch : MatchUiState
    data class InProgress(val match: ActiveMatch) : MatchUiState
}

class MatchViewModel(private val repository: MatchRepository) : ViewModel() {

    private val _state = MutableStateFlow<MatchUiState>(MatchUiState.Loading)
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val active = repository.findActiveMatch()
            _state.value = if (active != null) MatchUiState.InProgress(active) else MatchUiState.NoMatch
        }
    }

    fun startMatch() {
        // Ignore repeated taps and taps before the queue was read
        if (_state.value != MatchUiState.NoMatch) return
        _state.value = MatchUiState.Loading
        viewModelScope.launch {
            _state.value = MatchUiState.InProgress(repository.startMatch())
        }
    }

    fun endMatch() {
        val current = _state.value as? MatchUiState.InProgress ?: return
        _state.value = MatchUiState.Loading
        viewModelScope.launch {
            repository.endMatch(current.match)
            _state.value = MatchUiState.NoMatch
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val db = AppDatabase.get(this[APPLICATION_KEY]!!)
                MatchViewModel(MatchRepository(db.matchEventDao()))
            }
        }
    }
}
