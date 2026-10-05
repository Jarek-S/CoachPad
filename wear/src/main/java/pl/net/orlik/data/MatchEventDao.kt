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
}
