package pl.net.orlik.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.net.orlik.audio.AudioRecorder
import pl.net.orlik.data.EventEdits
import pl.net.orlik.data.LineupDao
import pl.net.orlik.data.LineupPlayer
import pl.net.orlik.data.LiveRecorder
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEventDao
import pl.net.orlik.data.PhoneDatabase
import pl.net.orlik.data.Team
import pl.net.orlik.data.recordingsDir
import pl.net.orlik.review.EventDraft
import pl.net.orlik.review.LiveAction
import pl.net.orlik.review.LiveMatch
import pl.net.orlik.review.LiveScore
import pl.net.orlik.review.QuickEvent
import pl.net.orlik.review.lineupOrder
import pl.net.orlik.review.liveMatchOf
import pl.net.orlik.review.liveScoreOf
import pl.net.orlik.review.quickConfirms
import pl.net.orlik.stt.Transcriber
import pl.net.orlik.shared.EventType

/** An event from a quick button, open in the sheet below the match screen. */
data class QuickEdit(val eventId: String, val recordedAt: Long?, val draft: EventDraft)

data class LiveMatchUiState(
    val match: Match? = null,
    /** Null while loading. */
    val live: LiveMatch? = null,
    val score: LiveScore = LiveScore(),
    val lineup: Map<Team, List<LineupPlayer>> = emptyMap(),
    /** The event whose details are being filled in; null when the sheet is closed. */
    val editing: QuickEdit? = null,
    /** When the recording in progress started; null when the microphone is off. */
    val recordingSince: Long? = null,
    /** The microphone could not be opened, shown until the next tap. */
    val micFailed: Boolean = false,
)

/** The match screen of a match recorded live on the phone (design doc, section 3.3). */
class LiveMatchViewModel(
    private val matchId: String,
    private val dao: MatchEventDao,
    lineup: LineupDao,
    private val recorder: LiveRecorder,
    private val edits: EventEdits,
    private val audio: AudioRecorder,
    private val recordingsDir: File,
    private val onRecorded: () -> Unit,
) : ViewModel() {

    private val editing = MutableStateFlow<QuickEdit?>(null)

    private val recording = MutableStateFlow<VoiceRecording?>(null)

    private val micFailed = MutableStateFlow(false)

    private data class VoiceRecording(val eventId: String, val startedAt: Long) {
        val fileName get() = "$eventId.m4a"
    }

    val state: StateFlow<LiveMatchUiState> = combine(
        combine(dao.match(matchId), dao.eventsOf(matchId), ::Pair),
        lineup.lineupOf(matchId),
        editing,
        recording,
        micFailed,
    ) { (match, events), players, edit, voice, failed ->
        LiveMatchUiState(
            match = match,
            live = liveMatchOf(events),
            score = liveScoreOf(events),
            lineup = players.sortedWith(lineupOrder).groupBy { it.team },
            editing = edit,
            recordingSince = voice?.startedAt,
            micFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveMatchUiState())

    fun pause() = record(LiveAction.PAUSE)

    fun resume() = record(LiveAction.RESUME)

    /** "Koniec meczu", held; [then] opens the match's review. */
    fun end(then: () -> Unit) {
        stopRecording()
        viewModelScope.launch {
            recorder.record(matchId, LiveAction.END)
            then()
        }
    }

    private fun record(action: LiveAction) {
        viewModelScope.launch { recorder.record(matchId, action) }
    }

    /** A quick button: the event is saved at once with its time, then its details can be added. */
    fun quick(button: QuickEvent) {
        viewModelScope.launch {
            val id = recorder.add(matchId, button.type, button.team)
            val draft = EventDraft(type = button.type, team = button.team)
            edits.save(id, draft, confirm = quickConfirms(draft))
            editing.value = QuickEdit(id, dao.event(id)?.recordedAt, draft)
        }
    }

    fun setType(type: EventType) = change { it.withType(type) }

    fun setTeam(team: Team) = change { it.copy(team = team) }

    fun setPlayer(player: String) = change { it.copy(player = player) }

    fun setAssist(assist: String) = change { it.copy(assist = assist) }

    fun setNote(note: String) = change { it.copy(note = note) }

    /** Saved as it changes, so nothing is lost if the app closes with the sheet open. */
    private fun change(update: (EventDraft) -> EventDraft) {
        val edit = editing.value ?: return
        val draft = update(edit.draft)
        editing.value = edit.copy(draft = draft)
        viewModelScope.launch { edits.save(edit.eventId, draft, confirm = quickConfirms(draft)) }
    }

    fun closeSheet() {
        editing.value = null
    }

    /** A button tapped by mistake: the event goes, as a deleted recording does. */
    fun deleteQuick() {
        val edit = editing.value ?: return
        editing.value = null
        viewModelScope.launch { edits.delete(edit.eventId) }
    }

    /** Tap to start, tap to stop; it stops by itself after the limit, as on the watch. */
    fun toggleRecording() {
        if (recording.value != null) stopRecording() else startRecording()
    }

    private fun startRecording() {
        micFailed.value = false
        val voice = VoiceRecording(UUID.randomUUID().toString(), System.currentTimeMillis())
        val file = File(recordingsDir.apply { mkdirs() }, voice.fileName)
        try {
            audio.start(file) { stopRecording() }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot start recording", e)
            file.delete()
            micFailed.value = true
            return
        }
        recording.value = voice
    }

    /** Stops and saves a recording in progress, e.g. when the screen is left. */
    fun stopRecording() {
        val voice = recording.value ?: return
        recording.value = null
        val file = File(recordingsDir, voice.fileName)
        if (!audio.stop()) {
            file.delete()
            return
        }
        viewModelScope.launch {
            recorder.addRecording(matchId, voice.eventId, voice.startedAt, voice.fileName)
            onRecorded()
        }
    }

    override fun onCleared() {
        // The screen saves a recording when it is left; this only frees the microphone if it did not
        val voice = recording.value ?: return
        audio.stop()
        File(recordingsDir, voice.fileName).delete()
    }

    companion object {
        private const val TAG = "LiveMatch"

        fun factory(app: Application, matchId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val db = PhoneDatabase.get(app)
                val dao = db.matchEventDao()
                LiveMatchViewModel(
                    matchId = matchId,
                    dao = dao,
                    lineup = db.lineupDao(),
                    recorder = LiveRecorder(dao),
                    edits = EventEdits(dao, recordingsDir(app)),
                    audio = AudioRecorder(app),
                    recordingsDir = recordingsDir(app),
                    // Recognised right away when speech-to-text is set up; else it waits in review
                    onRecorded = { Transcriber.get(app).start(matchId) },
                )
            }
        }
    }
}
