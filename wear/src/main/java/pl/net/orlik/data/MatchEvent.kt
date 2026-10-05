package pl.net.orlik.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import pl.net.orlik.shared.EventType

/**
 * An event waiting in the watch queue. A row is deleted once the phone ACKs it,
 * so every row in this table is pending sync.
 */
@Entity(
    tableName = "match_events",
    indices = [Index("match_id")],
)
data class MatchEvent(
    /** UUID generated on the watch; the phone deduplicates by it. */
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "match_id")
    val matchId: String,

    /** Epoch milliseconds. */
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,

    /** Structural MATCH_* type, or null for a voice recording (type set later on the phone). */
    val type: EventType?,

    /** Audio file name; null for structural events. */
    @ColumnInfo(name = "audio_file_ref")
    val audioFileRef: String?,
)
