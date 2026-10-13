package pl.net.orlik.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.net.orlik.shared.EventType

enum class Team { HOME, AWAY }

enum class ReviewStatus {
    /** Shown under "Do przejrzenia". */
    TO_REVIEW,

    /** Shown under "Potwierdzone". */
    CONFIRMED,

    /**
     * A recording removed as a mistake: hidden everywhere and its audio deleted. The row stays so
     * that the same UUID sent again by the watch is dropped as a duplicate instead of coming back.
     */
    DELETED,
}

/** A match event on the phone: what the watch sent plus what the review adds. */
@Entity(
    tableName = "events",
    indices = [Index("match_id")],
)
data class MatchEvent(
    /** UUID generated on the watch; duplicates from a repeated sync are dropped by it. */
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "match_id")
    val matchId: String,

    /** Epoch milliseconds, as recorded on the watch. */
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,

    /** Structural MATCH_* type, the type set in review, or null for a recording not described yet. */
    val type: EventType?,

    /** Audio file name in the recordings directory; null for structural events. */
    @ColumnInfo(name = "audio_file_ref")
    val audioFileRef: String?,

    /** Epoch milliseconds when the phone stored the event. */
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    /** Null for structural events and notes, and until set in review. */
    val team: Team? = null,

    /** The note typed after listening ("Notatka"); later filled by speech-to-text. */
    @ColumnInfo(name = "raw_transcript")
    val rawTranscript: String? = null,

    @ColumnInfo(defaultValue = "'TO_REVIEW'")
    val status: ReviewStatus = initialStatus(type),

    /** For GOAL_DISALLOWED: the goal it cancels (design doc, section 8). */
    @ColumnInfo(name = "linked_goal_event_id")
    val linkedGoalEventId: String? = null,
)

/** Structural events need no review; everything else waits under "Do przejrzenia". */
fun initialStatus(type: EventType?): ReviewStatus =
    if (type?.isStructural == true) ReviewStatus.CONFIRMED else ReviewStatus.TO_REVIEW

/** A match as the phone knows it. Team names are set on the phone; null until then. */
@Entity(tableName = "matches")
data class Match(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "home_team")
    val homeTeam: String? = null,

    @ColumnInfo(name = "away_team")
    val awayTeam: String? = null,
)

/** One row of the match list. */
data class MatchListItem(
    val matchId: String,
    val homeTeam: String?,
    val awayTeam: String?,
    /** Time of the match's first event (MATCH_START when it was synced). */
    val startedAt: Long,
    val toReview: Int,
    val recordings: Int,
)

@Dao
interface MatchEventDao {

    /** Ignores an event that is already stored, so a repeated sync cannot duplicate it. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: MatchEvent)

    /** Creates the match on its first received event; an existing one keeps its names. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMatch(match: Match)

    @Query("SELECT EXISTS(SELECT 1 FROM events WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    /** A match's events in time order, mistakes removed in review left out. */
    @Query("SELECT * FROM events WHERE match_id = :matchId AND status != 'DELETED' ORDER BY recorded_at")
    fun eventsOf(matchId: String): Flow<List<MatchEvent>>

    @Query("SELECT * FROM matches WHERE id = :matchId")
    fun match(matchId: String): Flow<Match?>

    /** Matches on the phone, newest first. */
    @Query(
        """
        SELECT m.id AS matchId, m.home_team AS homeTeam, m.away_team AS awayTeam,
            (SELECT MIN(recorded_at) FROM events e WHERE e.match_id = m.id) AS startedAt,
            (SELECT COUNT(*) FROM events e WHERE e.match_id = m.id AND e.status = 'TO_REVIEW') AS toReview,
            (SELECT COUNT(*) FROM events e
                WHERE e.match_id = m.id AND e.audio_file_ref IS NOT NULL AND e.status != 'DELETED') AS recordings
        FROM matches m
        ORDER BY startedAt DESC
        """,
    )
    fun matches(): Flow<List<MatchListItem>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun event(id: String): MatchEvent?

    /** Saves what the edit screen changes. */
    @Query(
        """
        UPDATE events SET type = :type, team = :team, raw_transcript = :note, linked_goal_event_id = :linkedGoalId,
            status = :status
        WHERE id = :id
        """,
    )
    suspend fun updateReview(
        id: String,
        type: EventType?,
        team: Team?,
        note: String?,
        linkedGoalId: String?,
        status: ReviewStatus,
    )

    @Query("UPDATE events SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: ReviewStatus)

    /** Deleted recordings whose audio file is still on the phone (kept while "Cofnij" is offered). */
    @Query("SELECT * FROM events WHERE status = 'DELETED' AND audio_file_ref IS NOT NULL")
    suspend fun deletedWithAudio(): List<MatchEvent>

    @Query("UPDATE events SET audio_file_ref = NULL WHERE id = :id")
    suspend fun clearAudio(id: String)

    /** Events on the phone, mistakes removed in review not counted. */
    @Query("SELECT COUNT(*) FROM events WHERE status != 'DELETED'")
    fun count(): Flow<Int>
}
