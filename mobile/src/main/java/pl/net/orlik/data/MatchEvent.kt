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

    /** Events on the phone, mistakes removed in review not counted. */
    @Query("SELECT COUNT(*) FROM events WHERE status != 'DELETED'")
    fun count(): Flow<Int>
}
