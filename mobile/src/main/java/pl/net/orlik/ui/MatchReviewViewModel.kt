package pl.net.orlik.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.net.orlik.audio.AudioDurations
import pl.net.orlik.audio.AudioLevels
import pl.net.orlik.audio.AudioPlayer
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEventDao
import pl.net.orlik.data.MatchListItem
import pl.net.orlik.data.PhoneDatabase
import pl.net.orlik.data.TranscriptDao
import pl.net.orlik.data.recordingsDir
import pl.net.orlik.export.MatchExport
import pl.net.orlik.export.matchExportOf
import pl.net.orlik.review.MatchReview
import pl.net.orlik.review.matchReviewOf
import pl.net.orlik.review.plannedChoices
import pl.net.orlik.stt.Transcriber
import pl.net.orlik.stt.TranscriptionStatus

data class MatchReviewUiState(
    val match: Match? = null,
    /** Time of the match's first event; null while loading. */
    val startedAt: Long? = null,
    val review: MatchReview = MatchReview(emptyList(), emptyList()),
    /** Recording lengths by event id; missing until read from the file. */
    val durations: Map<String, Long> = emptyMap(),
    /** Event whose recording is playing. */
    val playingId: String? = null,
    val stt: SttUiState = SttUiState(),
    /** Planned matches this match can take its lineups from; empty once it has any. */
    val planned: List<MatchListItem> = emptyList(),
)

/** Speech recognition for this match: what "Rozpoznaj" offers and how far it got. */
data class SttUiState(
    /** On in settings, with a key. */
    val available: Boolean = false,
    /** Recordings under "Do przejrzenia" not recognised yet. */
    val untranscribed: Int = 0,
    /** Idle unless running or failed for this match. */
    val status: TranscriptionStatus = TranscriptionStatus.Idle,
)

class MatchReviewViewModel(
    private val matchId: String,
    private val dao: MatchEventDao,
    transcripts: TranscriptDao,
    private val transcriber: Transcriber,
    private val recordingsDir: File,
) : ViewModel() {

    private val player = AudioPlayer(viewModelScope)

    private val events = dao.eventsOf(matchId)

    // The events show at once; lengths follow when read from the files
    private val durations = events.map { list ->
        list.mapNotNull { event ->
            val file = event.audioFileRef ?: return@mapNotNull null
            AudioDurations.of(File(recordingsDir, file))?.let { event.id to it }
        }.toMap()
    }.onStart { emit(emptyMap()) }

    init {
        // Loudness measured ahead, so playback starts without the wait; decoding every recording
        // takes seconds, so it stays out of what the screen waits for
        viewModelScope.launch {
            events.collectLatest { list ->
                list.forEach { event -> event.audioFileRef?.let { AudioLevels.gainMillibels(File(recordingsDir, it)) } }
            }
        }
    }

    private val stt = combine(
        flow { emit(transcriber.available()) },
        transcripts.untranscribedCount(matchId),
        transcriber.status,
    ) { available, untranscribed, status ->
        val ours = when (status) {
            is TranscriptionStatus.Running -> status.matchId == matchId
            is TranscriptionStatus.Failed -> status.matchId == matchId
            TranscriptionStatus.Idle -> false
        }
        SttUiState(available, untranscribed, if (ours) status else TranscriptionStatus.Idle)
    }

    private val sttAndPlanned = combine(stt, dao.matches()) { stt, matches -> stt to plannedChoices(matches, matchId) }

    val state: StateFlow<MatchReviewUiState> = combine(
        dao.match(matchId),
        events,
        durations,
        player.playingId,
        sttAndPlanned,
    ) { match, list, durations, playingId, (stt, planned) ->
        MatchReviewUiState(
            match = match,
            startedAt = list.minOfOrNull { it.recordedAt },
            review = matchReviewOf(list),
            durations = durations,
            playingId = playingId,
            stt = stt,
            planned = planned,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchReviewUiState())

    /** Plays the event's recording, or stops it when it is already playing. */
    fun togglePlayback(eventId: String, audioFileRef: String) {
        player.toggle(eventId, File(recordingsDir, audioFileRef))
    }

    fun stopPlayback() = player.stop()

    /** "Rozpoznaj nagrania": recordings of this match not recognised yet. */
    fun transcribe() = transcriber.start(matchId)

    /** Takes the team names and lineups of a planned match, then reads the recognised recordings again with them. */
    fun takeOverPlanned(plannedId: String) {
        viewModelScope.launch {
            dao.takeOverPlanned(plannedId, matchId)
            transcriber.reread(matchId)
        }
    }

    /** The match as it is now, for "Udostępnij". */
    suspend fun export(): MatchExport = matchExportOf(dao.match(matchId).first() ?: Match(matchId), events.first())

    override fun onCleared() {
        player.stop()
    }

    companion object {
        fun factory(app: Application, matchId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val db = PhoneDatabase.get(app)
                MatchReviewViewModel(matchId, db.matchEventDao(), db.transcriptDao(), Transcriber.get(app), recordingsDir(app))
            }
        }
    }
}
