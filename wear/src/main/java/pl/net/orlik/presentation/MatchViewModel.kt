package pl.net.orlik.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.net.orlik.data.ActiveMatch
import pl.net.orlik.data.AppDatabase
import pl.net.orlik.data.MatchRepository
import pl.net.orlik.data.PreferencesMatchTimerStore
import pl.net.orlik.data.VoiceRecording
import pl.net.orlik.recording.AudioRecorder
import pl.net.orlik.settings.PreferencesSettingsStore

sealed interface MatchUiState {
    /** Reading the queue to find a match in progress. */
    data object Loading : MatchUiState
    data object NoMatch : MatchUiState
    data class InProgress(val match: ActiveMatch, val isRecording: Boolean = false) : MatchUiState
}

class MatchViewModel(
    private val repository: MatchRepository,
    private val recorder: AudioRecorder,
    private val recordingsDir: File,
) : ViewModel() {

    private val _state = MutableStateFlow<MatchUiState>(MatchUiState.Loading)
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    private var pauseJob: Job? = null
    private var recording: VoiceRecording? = null

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

    fun togglePause() {
        val match = (_state.value as? MatchUiState.InProgress)?.match ?: return
        // Ignore taps while the previous one is being saved; keep the clock on screen meanwhile
        if (pauseJob?.isActive == true) return
        pauseJob = viewModelScope.launch {
            val updated = if (match.isPaused) repository.resumeMatch(match) else repository.pauseMatch(match)
            updateInProgress { it.copy(match = updated) }
        }
    }

    /** Confirms the substitution when the shift timer alarm is on; ignored otherwise. */
    fun confirmShift() {
        updateInProgress { it.copy(match = repository.confirmShift(it.match)) }
    }

    fun toggleRecording() {
        if (recording != null) stopRecording() else startRecording()
    }

    /** Stops and saves a recording in progress, e.g. when the app leaves the screen. */
    fun stopRecording(): Job? {
        val current = recording ?: return null
        recording = null
        val recorded = recorder.stop()
        updateInProgress { it.copy(isRecording = false) }
        val file = File(recordingsDir, current.fileName)
        if (!recorded) {
            file.delete()
            return null
        }
        return viewModelScope.launch { repository.saveVoiceRecording(current) }
    }

    fun endMatch() {
        val current = _state.value as? MatchUiState.InProgress ?: return
        val saving = stopRecording()
        _state.value = MatchUiState.Loading
        viewModelScope.launch {
            saving?.join()
            repository.endMatch(current.match)
            _state.value = MatchUiState.NoMatch
        }
    }

    private fun startRecording() {
        val match = (_state.value as? MatchUiState.InProgress)?.match ?: return
        val newRecording = repository.newVoiceRecording(match)
        val file = File(recordingsDir.apply { mkdirs() }, newRecording.fileName)
        try {
            recorder.start(file) { stopRecording() }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot start recording", e)
            file.delete()
            return
        }
        recording = newRecording
        updateInProgress { it.copy(isRecording = true) }
    }

    private fun updateInProgress(change: (MatchUiState.InProgress) -> MatchUiState.InProgress) {
        _state.update { if (it is MatchUiState.InProgress) change(it) else it }
    }

    companion object {
        private const val TAG = "MatchViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                MatchViewModel(
                    repository = MatchRepository(
                        dao = AppDatabase.get(app).matchEventDao(),
                        settings = PreferencesSettingsStore(app),
                        timerStore = PreferencesMatchTimerStore(app),
                    ),
                    recorder = AudioRecorder(app),
                    recordingsDir = File(app.filesDir, "recordings"),
                )
            }
        }
    }
}
