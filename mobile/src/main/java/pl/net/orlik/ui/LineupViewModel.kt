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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.net.orlik.data.LineupDao
import pl.net.orlik.data.LiveRecorder
import pl.net.orlik.data.LineupPlayer
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEventDao
import pl.net.orlik.data.PhoneDatabase
import pl.net.orlik.data.Team
import pl.net.orlik.review.LiveAction
import pl.net.orlik.review.PlayerEntry
import java.time.ZoneId
import pl.net.orlik.review.lineupOrder
import pl.net.orlik.review.playedAtOf
import pl.net.orlik.review.teamNameOf

data class LineupUiState(
    /** Null while loading. */
    val match: Match? = null,
    val startedAt: Long? = null,
    /** Each team's players, sorted for display. */
    val players: Map<Team, List<LineupPlayer>> = emptyMap(),
    /** Another match is being recorded live on the phone: this one cannot start yet. */
    val otherMatchLive: Boolean = false,
)

class LineupViewModel(
    private val matchId: String,
    private val events: MatchEventDao,
    private val lineup: LineupDao,
    private val recorder: LiveRecorder,
) : ViewModel() {

    val state: StateFlow<LineupUiState> = combine(
        events.match(matchId),
        events.eventsOf(matchId).map { list -> list.mapNotNull { it.recordedAt }.minOrNull() },
        lineup.lineupOf(matchId),
        events.liveMatchId(),
    ) { match, startedAt, players, liveId ->
        LineupUiState(
            match = match,
            startedAt = startedAt ?: match?.playedAt,
            players = players.sortedWith(lineupOrder).groupBy { it.team },
            otherMatchLive = liveId != null && liveId != matchId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LineupUiState())

    /** Saved as typed; an empty name goes back to "Gospodarze" / "Goście". */
    fun setTeamName(team: Team, typed: String) {
        val name = teamNameOf(typed)
        viewModelScope.launch {
            when (team) {
                Team.HOME -> lineup.setHomeTeam(matchId, name)
                Team.AWAY -> lineup.setAwayTeam(matchId, name)
            }
        }
    }

    fun addPlayer(team: Team, entry: PlayerEntry) {
        viewModelScope.launch { lineup.insert(LineupPlayer(matchId = matchId, team = team, number = entry.number, name = entry.name)) }
    }

    fun updatePlayer(id: Long, entry: PlayerEntry) {
        viewModelScope.launch { lineup.update(id, entry.number, entry.name) }
    }

    fun removePlayer(id: Long) {
        viewModelScope.launch { lineup.delete(id) }
    }

    /** "Rozpocznij mecz": kickoff now, recorded on the phone; [then] opens the match screen. */
    fun startMatch(then: () -> Unit) {
        viewModelScope.launch {
            recorder.record(matchId, LiveAction.START)
            then()
        }
    }

    /** "Wpisz z notatek": the match was played on the chosen day; [then] opens it to type events in. */
    fun enterFromNotes(utcDayMillis: Long, then: () -> Unit) {
        viewModelScope.launch {
            lineup.setPlayedAt(matchId, playedAtOf(utcDayMillis, ZoneId.systemDefault()))
            then()
        }
    }

    companion object {
        fun factory(app: Application, matchId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val db = PhoneDatabase.get(app)
                LineupViewModel(matchId, db.matchEventDao(), db.lineupDao(), LiveRecorder(db.matchEventDao()))
            }
        }
    }
}
