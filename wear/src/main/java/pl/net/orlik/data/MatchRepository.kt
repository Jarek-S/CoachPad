package pl.net.orlik.data

import java.util.UUID
import pl.net.orlik.shared.EventType

/** Records match lifecycle events in the watch queue. */
class MatchRepository(
    private val dao: MatchEventDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * The match still in progress (e.g. after the app was restarted), or null.
     * Relies on its events staying in the queue — sync is blocked while a match is in progress.
     */
    suspend fun findActiveMatch(): ActiveMatch? {
        val matchId = dao.getLatestStartedMatchId() ?: return null
        return activeMatchFrom(dao.getByMatch(matchId))
    }

    /** Starts a new match: generates its id and records MATCH_START. */
    suspend fun startMatch(): ActiveMatch {
        val match = ActiveMatch(matchId = UUID.randomUUID().toString(), startedAt = clock())
        dao.insert(structuralEvent(match.matchId, EventType.MATCH_START, match.startedAt))
        return match
    }

    /** Ends [match] by recording MATCH_END. Allowed during a pause too. */
    suspend fun endMatch(match: ActiveMatch) {
        dao.insert(structuralEvent(match.matchId, EventType.MATCH_END, clock()))
    }

    private fun structuralEvent(matchId: String, type: EventType, at: Long) = MatchEvent(
        id = UUID.randomUUID().toString(),
        matchId = matchId,
        recordedAt = at,
        type = type,
        audioFileRef = null,
    )
}
