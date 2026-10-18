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
import pl.net.orlik.data.MatchRepository
import pl.net.orlik.data.VoiceRecording
import pl.net.orlik.data.matchRepository
import pl.net.orlik.data.recordingsDir
import pl.net.orlik.log.EventLog
import pl.net.orlik.log.fieldLog
import pl.net.orlik.log.short
import pl.net.orlik.recording.AudioRecorder
import pl.net.orlik.service.AndroidMatchServiceControl
import pl.net.orlik.service.MatchServiceControl

sealed interface MatchUiState {
    /** Reading the queue to find a match in progress. */
    data object Loading : MatchUiState
    data object NoMatch : MatchUiState
    data class InProgress(
        val match: ActiveMatch,
        val isRecording: Boolean = false,
        /** Times the microphone could not be opened; each one gets a warning vibration. */
        val failedStarts: Int = 0,
    ) : MatchUiState
}

/** Why a recording stopped; written to the field log. */
enum class StopReason {
    /** Tapped again. */
    TAP,

    /** The 15 s limit. */
    LIMIT,

    /** The app left the screen (e.g. the screen was covered); the microphone would go silent. */
    APP_HIDDEN,
    MATCH_END,
}

class MatchViewModel(
    private val repository: MatchRepository,
    private val recorder: AudioRecorder,
    private val recordingsDir: File,
    private val matchService: MatchServiceControl,
    private val log: EventLog,
) : ViewModel() {

    private val _state = MutableStateFlow<MatchUiState>(MatchUiState.Loading)
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    private var pauseJob: Job? = null
    private var recording: VoiceRecording? = null

    init {
        viewModelScope.launch {
            val active = repository.findActiveMatch()
            log.log("app opened, match in progress: ${active?.matchId?.short() ?: "none"}")
            _state.value = if (active != null) MatchUiState.InProgress(active) else MatchUiState.NoMatch
            matchService.update(active)
            // Republish in case the last change was not delivered to the Data Layer
            repository.publishQueueSummary()
        }
    }

    fun startMatch() {
        // Ignore repeated taps and taps before the queue was read
        if (_state.value != MatchUiState.NoMatch) return
        _state.value = MatchUiState.Loading
        viewModelScope.launch {
            val match = repository.startMatch()
            log.log("match start ${match.matchId.short()}")
            _state.value = MatchUiState.InProgress(match)
            matchService.update(match)
        }
    }

    fun togglePause() {
        val match = (_state.value as? MatchUiState.InProgress)?.match ?: return
        // Ignore taps while the previous one is being saved; keep the clock on screen meanwhile
        if (pauseJob?.isActive == true) return
        pauseJob = viewModelScope.launch {
            val updated = if (match.isPaused) repository.resumeMatch(match) else repository.pauseMatch(match)
            log.log(if (updated.isPaused) "match pause" else "match resume")
            updateInProgress { it.copy(match = updated) }
            matchService.update(updated)
        }
    }

    /** Confirms the substitution when the shift timer alarm is on; ignored otherwise. */
    fun confirmShift() {
        val match = (_state.value as? MatchUiState.InProgress)?.match ?: return
        val confirmed = repository.confirmShift(match)
        if (confirmed == match) return
        updateInProgress { it.copy(match = confirmed) }
        matchService.update(confirmed)
    }

    /** Holding the match clock: turns the shift timer on or off for the rest of the match. */
    fun toggleShiftTimer() {
        val match = (_state.value as? MatchUiState.InProgress)?.match ?: return
        val updated = repository.toggleShiftTimer(match)
        log.log(if (updated.shiftTimer != null) "shift timer on" else "shift timer off")
        updateInProgress { it.copy(match = updated) }
        matchService.update(updated)
    }

    fun toggleRecording() {
        if (recording != null) stopRecording(StopReason.TAP) else startRecording()
    }

    /** Stops and saves a recording in progress, e.g. when the app leaves the screen. */
    fun stopRecording(reason: StopReason): Job? {
        val current = recording ?: return null
        recording = null
        val recorded = recorder.stop()
        updateInProgress { it.copy(isRecording = false) }
        val file = File(recordingsDir, current.fileName)
        val seconds = "%.1f s".format((System.currentTimeMillis() - current.startedAt) / 1_000.0)
        if (!recorded) {
            log.log("recording ${current.eventId.short()} DISCARDED (nothing recorded), $seconds, stop: $reason")
            file.delete()
            return null
        }
        log.log("recording ${current.eventId.short()} stop: $reason, $seconds, ${file.length()} B")
        return viewModelScope.launch {
            repository.saveVoiceRecording(current)
            log.log("recording ${current.eventId.short()} saved")
        }
    }

    fun endMatch() {
        val current = _state.value as? MatchUiState.InProgress ?: return
        val saving = stopRecording(StopReason.MATCH_END)
        _state.value = MatchUiState.Loading
        viewModelScope.launch {
            saving?.join()
            repository.endMatch(current.match)
            log.log("match end ${current.match.matchId.short()}")
            _state.value = MatchUiState.NoMatch
            matchService.update(null)
        }
    }

    private fun startRecording() {
        val match = (_state.value as? MatchUiState.InProgress)?.match ?: return
        val newRecording = repository.newVoiceRecording(match)
        val file = File(recordingsDir.apply { mkdirs() }, newRecording.fileName)
        try {
            recorder.start(file) { stopRecording(StopReason.LIMIT) }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot start recording", e)
            log.log("recording ${newRecording.eventId.short()} FAILED to start: $e")
            file.delete()
            updateInProgress { it.copy(failedStarts = it.failedStarts + 1) }
            return
        }
        recording = newRecording
        log.log("recording ${newRecording.eventId.short()} start")
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
                    repository = matchRepository(app),
                    recorder = AudioRecorder(app),
                    recordingsDir = recordingsDir(app),
                    matchService = AndroidMatchServiceControl(app),
                    log = fieldLog(app),
                )
            }
        }
    }
}
