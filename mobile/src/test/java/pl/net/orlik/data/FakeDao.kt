package pl.net.orlik.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import pl.net.orlik.review.PlannedMatch
import pl.net.orlik.shared.EventType

/** In-memory stand-in for Room in JVM tests. */
class FakeDao : MatchEventDao {
    val events = mutableListOf<MatchEvent>()
    val matches = mutableListOf<Match>()
    val lineup = mutableListOf<LineupPlayer>()

    override suspend fun insert(event: MatchEvent) {
        if (events.none { it.id == event.id }) events += event
    }

    override suspend fun insertMatch(match: Match) {
        if (matches.none { it.id == match.id }) matches += match
    }

    override suspend fun exists(id: String) = events.any { it.id == id }

    override suspend fun matchExists(matchId: String) = matches.any { it.id == matchId }

    override suspend fun plannedMatches() = matches
        .filter { m ->
            m.plannedAt != null && events.none { it.matchId == m.id } &&
                (m.homeTeam != null || m.awayTeam != null || lineup.any { it.matchId == m.id })
        }
        .map { PlannedMatch(it.id, it.plannedAt!!) }

    override suspend fun copyTeamNames(plannedId: String, matchId: String) {
        val planned = matches.firstOrNull { it.id == plannedId } ?: return
        matches.replaceAll { if (it.id == matchId) it.copy(homeTeam = planned.homeTeam, awayTeam = planned.awayTeam) else it }
    }

    override suspend fun moveLineup(plannedId: String, matchId: String) {
        lineup.replaceAll { if (it.matchId == plannedId) it.copy(matchId = matchId) else it }
    }

    override fun count(): Flow<Int> = flowOf(events.size)

    override fun eventsOf(matchId: String): Flow<List<MatchEvent>> = flowOf(events.filter { it.matchId == matchId })

    override fun match(matchId: String): Flow<Match?> = flowOf(matches.firstOrNull { it.id == matchId })

    override fun matches(): Flow<List<MatchListItem>> = flowOf(emptyList())

    override suspend fun event(id: String) = events.firstOrNull { it.id == id }

    override suspend fun updateReview(
        id: String,
        type: EventType?,
        team: Team?,
        note: String?,
        linkedGoalId: String?,
        player: String?,
        assist: String?,
        status: ReviewStatus,
    ) = change(id) {
        it.copy(
            type = type, team = team, rawTranscript = note, linkedGoalEventId = linkedGoalId,
            playerName = player, assistName = assist, status = status,
        )
    }

    override suspend fun setStatus(id: String, status: ReviewStatus) = change(id) { it.copy(status = status) }

    override suspend fun deletedWithAudio() =
        events.filter { it.status == ReviewStatus.DELETED && it.audioFileRef != null }

    override suspend fun clearAudio(id: String) = change(id) { it.copy(audioFileRef = null) }

    override suspend fun audioFilesOf(matchId: String) = events.filter { it.matchId == matchId }.mapNotNull { it.audioFileRef }

    override suspend fun deleteEventsOf(matchId: String) {
        events.removeAll { it.matchId == matchId }
    }

    override suspend fun deleteLineupOf(matchId: String) = Unit

    override suspend fun deleteMatchRow(matchId: String) {
        matches.removeAll { it.id == matchId }
    }

    private fun change(id: String, update: (MatchEvent) -> MatchEvent) {
        val index = events.indexOfFirst { it.id == id }
        if (index >= 0) events[index] = update(events[index])
    }
}
