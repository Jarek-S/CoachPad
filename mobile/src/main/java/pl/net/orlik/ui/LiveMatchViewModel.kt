package pl.net.orlik.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.net.orlik.data.LiveRecorder
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEventDao
import pl.net.orlik.data.PhoneDatabase
import pl.net.orlik.review.LiveAction
import pl.net.orlik.review.LiveMatch
import pl.net.orlik.review.liveMatchOf

data class LiveMatchUiState(
    val match: Match? = null,
    /** Null while loading. */
    val live: LiveMatch? = null,
)

/** The match screen of a match recorded live on the phone (design doc, section 3.3). */
class LiveMatchViewModel(
    private val matchId: String,
    dao: MatchEventDao,
    private val recorder: LiveRecorder,
) : ViewModel() {

    val state: StateFlow<LiveMatchUiState> = combine(dao.match(matchId), dao.eventsOf(matchId)) { match, events ->
        LiveMatchUiState(match, liveMatchOf(events))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveMatchUiState())

    fun pause() = record(LiveAction.PAUSE)

    fun resume() = record(LiveAction.RESUME)

    /** "Koniec meczu", held; [then] opens the match's review. */
    fun end(then: () -> Unit) {
        viewModelScope.launch {
            recorder.record(matchId, LiveAction.END)
            then()
        }
    }

    private fun record(action: LiveAction) {
        viewModelScope.launch { recorder.record(matchId, action) }
    }

    companion object {
        fun factory(app: Application, matchId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val dao = PhoneDatabase.get(app).matchEventDao()
                LiveMatchViewModel(matchId, dao, LiveRecorder(dao))
            }
        }
    }
}
