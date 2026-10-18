package pl.net.orlik.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Recordings waiting for speech-to-text: still under "Do przejrzenia" and not recognised yet. */
@Dao
interface TranscriptDao {

    @Query(
        "SELECT * FROM events WHERE match_id = :matchId AND audio_file_ref IS NOT NULL " +
            "AND status = 'TO_REVIEW' AND transcript IS NULL ORDER BY recorded_at",
    )
    suspend fun untranscribed(matchId: String): List<MatchEvent>

    @Query(
        "SELECT COUNT(*) FROM events WHERE match_id = :matchId AND audio_file_ref IS NOT NULL " +
            "AND status = 'TO_REVIEW' AND transcript IS NULL",
    )
    fun untranscribedCount(matchId: String): Flow<Int>

    @Query(
        "SELECT DISTINCT match_id FROM events WHERE audio_file_ref IS NOT NULL " +
            "AND status = 'TO_REVIEW' AND transcript IS NULL",
    )
    suspend fun matchesWithUntranscribed(): List<String>

    @Query("UPDATE events SET transcript = :text WHERE id = :id")
    suspend fun setTranscript(id: String, text: String)
}
