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

/** An event received from the watch. Review data (team, note, links) comes in Phase 3. */
@Entity(
    tableName = "events",
    indices = [Index("match_id")],
)
data class ReceivedEvent(
    /** UUID generated on the watch; duplicates from a repeated sync are dropped by it. */
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "match_id")
    val matchId: String,

    /** Epoch milliseconds, as recorded on the watch. */
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,

    /** Structural MATCH_* type, or null for a voice recording. */
    val type: EventType?,

    /** Audio file name in the recordings directory; null for structural events. */
    @ColumnInfo(name = "audio_file_ref")
    val audioFileRef: String?,

    /** Epoch milliseconds when the phone stored the event. */
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,
)

@Dao
interface ReceivedEventDao {

    /** Ignores an event that is already stored, so a repeated sync cannot duplicate it. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: ReceivedEvent)

    @Query("SELECT EXISTS(SELECT 1 FROM events WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("SELECT COUNT(*) FROM events")
    fun count(): Flow<Int>
}
