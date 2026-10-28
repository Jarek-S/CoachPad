package pl.net.orlik.stt

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.net.orlik.R
import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.PhoneDatabase
import pl.net.orlik.data.recordingsDir
import pl.net.orlik.review.EventDraft
import pl.net.orlik.review.TranscriptReading
import pl.net.orlik.review.draftOf
import pl.net.orlik.review.filledFrom
import pl.net.orlik.review.readTranscript
import pl.net.orlik.review.refilledFrom
import pl.net.orlik.review.relinked

sealed interface TranscriptionStatus {
    data object Idle : TranscriptionStatus

    data class Running(val matchId: String, val done: Int, val total: Int) : TranscriptionStatus

    /** Stopped on the first failure: the rest of the match waits for "Rozpoznaj". */
    data class Failed(val matchId: String, val error: SttError) : TranscriptionStatus
}

/**
 * Sends recordings waiting for review to speech-to-text, one at a time, and fills what the text
 * says into the empty fields of the event (design doc, section 4, level 3). Runs after a sync and
 * on "Rozpoznaj" in a match's review, only when speech recognition is on and a key is saved; the
 * events stay under "Do przejrzenia" for a person to confirm.
 */
class Transcriber private constructor(private val context: Context) {

    private val db = PhoneDatabase.get(context)
    private val events = db.matchEventDao()
    private val transcripts = db.transcriptDao()
    private val lineup = db.lineupDao()
    private val settings = SttSettings(context)
    private val provider: SpeechToTextProvider = WhisperProvider(apiKey = settings::apiKey)
    private val recordings = recordingsDir(context)

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w(TAG, "Transcription failed", e) },
    )
    private val mutex = Mutex()

    private val _status = MutableStateFlow<TranscriptionStatus>(TranscriptionStatus.Idle)
    val status: StateFlow<TranscriptionStatus> = _status.asStateFlow()

    /** Speech recognition is on and has a key, so "Rozpoznaj" can be offered. */
    suspend fun available(): Boolean = settings.enabled && settings.apiKey() != null

    /** Every match with recordings not recognised yet, e.g. right after a sync. */
    fun startPending() {
        scope.launch {
            mutex.withLock {
                if (!available()) return@withLock
                for (matchId in transcripts.matchesWithUntranscribed()) {
                    if (!transcribeMatch(matchId)) break
                }
            }
        }
    }

    /** One match, from its review. */
    fun start(matchId: String) {
        scope.launch {
            mutex.withLock {
                if (available()) transcribeMatch(matchId)
            }
        }
    }

    /**
     * After a match without a lineup took over a planned one: its recognised recordings read again
     * with the lineup, so names match the players. No speech-to-text call, so no key is needed.
     */
    fun reread(matchId: String) {
        scope.launch {
            mutex.withLock {
                val players = lineup.lineupOf(matchId).first()
                val match = events.match(matchId).first()
                for (event in transcripts.transcribed(matchId)) {
                    val text = event.transcript ?: continue
                    val earlier = readTranscript(text, emptyList(), null, null)
                    val now = readTranscript(text, players, match?.homeTeam, match?.awayTeam)
                    update(event) { it.refilledFrom(earlier, now) }
                }
            }
        }
    }

    /** False when it stopped on a failure; the same failure would stop the next match too. */
    private suspend fun transcribeMatch(matchId: String): Boolean {
        val waiting = transcripts.untranscribed(matchId)
        if (waiting.isEmpty()) return true
        val players = lineup.lineupOf(matchId).first()
        val match = events.match(matchId).first()
        val hints = context.getString(R.string.stt_hint_words).split(",") +
            players.map { it.name } + listOfNotNull(match?.homeTeam, match?.awayTeam)
        val language = context.resources.configuration.locales[0].language
        waiting.forEachIndexed { index, event ->
            _status.value = TranscriptionStatus.Running(matchId, index, waiting.size)
            val audio = File(recordings, event.audioFileRef ?: return@forEachIndexed)
            when (val result = provider.transcribe(audio, language, hints)) {
                is TranscriptResult.Success -> {
                    transcripts.setTranscript(event.id, result.text)
                    fill(event, readTranscript(result.text, players, match?.homeTeam, match?.awayTeam))
                }
                is TranscriptResult.Failure -> {
                    _status.value = TranscriptionStatus.Failed(matchId, result.error)
                    return false
                }
            }
        }
        _status.value = TranscriptionStatus.Idle
        return true
    }

    /** Saves the reading into the fields still empty. */
    private suspend fun fill(stale: MatchEvent, reading: TranscriptReading) = update(stale) { it.filledFrom(reading) }

    /** Saves what [change] makes of the event, read again in case the review changed it meanwhile. */
    private suspend fun update(stale: MatchEvent, change: (EventDraft) -> EventDraft) {
        val event = events.event(stale.id) ?: return
        val before = draftOf(event)
        var draft = change(before)
        if (draft == before) return
        if (draft.type != before.type || draft.team != before.team) {
            draft = relinked(draft, event, events.eventsOf(event.matchId).first())
        }
        events.updateReview(
            id = event.id,
            type = draft.type,
            team = draft.team,
            note = draft.storedNote,
            linkedGoalId = draft.storedLink,
            player = draft.storedPlayer,
            assist = draft.storedAssist,
            status = event.status,
        )
    }

    companion object {
        private const val TAG = "Transcriber"

        @Volatile
        private var instance: Transcriber? = null

        fun get(context: Context): Transcriber =
            instance ?: synchronized(this) {
                instance ?: Transcriber(context.applicationContext).also { instance = it }
            }
    }
}
