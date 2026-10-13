package pl.net.orlik.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import pl.net.orlik.shared.EventType

/** In-memory stand-in for Room in JVM tests. */
class FakeDao : MatchEventDao {
    val events = mutableListOf<MatchEvent>()
    val matches = mutableListOf<Match>()

    override suspend fun insert(event: MatchEvent) {
        if (events.none { it.id == event.id }) events += event
    }

    override suspend fun insertMatch(match: Match) {
        if (matches.none { it.id == match.id }) matches += match
    }

    override suspend fun exists(id: String) = events.any { it.id == id }

    override fun count(): Flow<Int> = flowOf(events.size)

    override fun eventsOf(matchId: String): Flow<List<MatchEvent>> = flowOf(events.filter { it.matchId == matchId })

    override fun match(matchId: String): Flow<Match?> = flowOf(matches.firstOrNull { it.id == matchId })

    override fun matches(): Flow<List<MatchListItem>> = flowOf(emptyList())

    override suspend fun setTeamNames(matchId: String, homeTeam: String?, awayTeam: String?) {
        val index = matches.indexOfFirst { it.id == matchId }
        if (index >= 0) matches[index] = matches[index].copy(homeTeam = homeTeam, awayTeam = awayTeam)
    }

    override suspend fun event(id: String) = events.firstOrNull { it.id == id }

    override suspend fun updateReview(
        id: String,
        type: EventType?,
        team: Team?,
        note: String?,
        linkedGoalId: String?,
        status: ReviewStatus,
    ) = change(id) {
        it.copy(type = type, team = team, rawTranscript = note, linkedGoalEventId = linkedGoalId, status = status)
    }

    override suspend fun setStatus(id: String, status: ReviewStatus) = change(id) { it.copy(status = status) }

    override suspend fun deletedWithAudio() =
        events.filter { it.status == ReviewStatus.DELETED && it.audioFileRef != null }

    override suspend fun clearAudio(id: String) = change(id) { it.copy(audioFileRef = null) }

    private fun change(id: String, update: (MatchEvent) -> MatchEvent) {
        val index = events.indexOfFirst { it.id == id }
        if (index >= 0) events[index] = update(events[index])
    }
}
