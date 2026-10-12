package pl.net.orlik.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MatchEventDao {

    @Insert
    suspend fun insert(event: MatchEvent)

    @Query("SELECT * FROM match_events WHERE match_id = :matchId ORDER BY recorded_at")
    suspend fun getByMatch(matchId: String): List<MatchEvent>

    /** All events not yet ACKed by the phone, oldest first. */
    @Query("SELECT * FROM match_events ORDER BY recorded_at")
    suspend fun getPending(): List<MatchEvent>

    /** The oldest queued event of [matchId], or of any match when [matchId] is null. */
    @Query("SELECT * FROM match_events WHERE :matchId IS NULL OR match_id = :matchId ORDER BY recorded_at LIMIT 1")
    suspend fun getOldestPending(matchId: String?): MatchEvent?

    @Query("SELECT * FROM match_events WHERE id = :id")
    suspend fun getById(id: String): MatchEvent?

    @Query("DELETE FROM match_events WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Match id of the most recently started match, or null if the queue has none. */
    @Query("SELECT match_id FROM match_events WHERE type = 'MATCH_START' ORDER BY recorded_at DESC LIMIT 1")
    suspend fun getLatestStartedMatchId(): String?
}
