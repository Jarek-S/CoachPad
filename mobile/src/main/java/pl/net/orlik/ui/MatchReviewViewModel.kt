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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.net.orlik.audio.AudioDurations
import pl.net.orlik.audio.AudioPlayer
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEventDao
import pl.net.orlik.data.PhoneDatabase
import pl.net.orlik.data.recordingsDir
import pl.net.orlik.review.MatchReview
import pl.net.orlik.review.matchReviewOf

data class MatchReviewUiState(
    val match: Match? = null,
    /** Time of the match's first event; null while loading. */
    val startedAt: Long? = null,
    val review: MatchReview = MatchReview(emptyList(), emptyList()),
    /** Recording lengths by event id; missing until read from the file. */
    val durations: Map<String, Long> = emptyMap(),
    /** Event whose recording is playing. */
    val playingId: String? = null,
)

class MatchReviewViewModel(
    matchId: String,
    dao: MatchEventDao,
    private val recordingsDir: File,
) : ViewModel() {

    private val player = AudioPlayer()

    private val events = dao.eventsOf(matchId)

    private val durations = events.map { list ->
        list.mapNotNull { event ->
            val file = event.audioFileRef ?: return@mapNotNull null
            AudioDurations.of(File(recordingsDir, file))?.let { event.id to it }
        }.toMap()
    }

    val state: StateFlow<MatchReviewUiState> = combine(
        dao.match(matchId),
        events,
        durations,
        player.playingId,
    ) { match, list, durations, playingId ->
        MatchReviewUiState(
            match = match,
            startedAt = list.minOfOrNull { it.recordedAt },
            review = matchReviewOf(list),
            durations = durations,
            playingId = playingId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchReviewUiState())

    /** Plays the event's recording, or stops it when it is already playing. */
    fun togglePlayback(eventId: String, audioFileRef: String) {
        player.toggle(eventId, File(recordingsDir, audioFileRef))
    }

    fun stopPlayback() = player.stop()

    override fun onCleared() {
        player.stop()
    }

    companion object {
        fun factory(app: Application, matchId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MatchReviewViewModel(matchId, PhoneDatabase.get(app).matchEventDao(), recordingsDir(app))
            }
        }
    }
}
