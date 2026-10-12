package pl.net.orlik.shared.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import pl.net.orlik.shared.EventType

/** Asks the watch to send its queued events: one match, or all pending matches when [matchId] is null. */
@Serializable
data class SyncRequest(val matchId: String? = null)

@Serializable
data class SyncRejected(val reason: Reason) {
    enum class Reason {
        /** Sync is refused from MATCH_START to MATCH_END; see the design doc, section 3.1. */
        MATCH_IN_PROGRESS,
    }
}

/** One queued event. When [hasAudio] is true its file follows on [DataLayerPaths.audioChannel]. */
@Serializable
data class EventMetadata(
    /** UUID generated on the watch; the phone deduplicates by it. */
    val id: String,
    val matchId: String,
    /** Epoch milliseconds. */
    val recordedAt: Long,
    /** Structural MATCH_* type, or null for a voice recording. */
    val type: EventType?,
    val hasAudio: Boolean,
)

/** Sent by the phone once the event (and its audio, if any) is stored; also for duplicates. */
@Serializable
data class Ack(val eventId: String)

/** Deletes a match's queued events and audio on the watch. */
@Serializable
data class DeleteMatch(val matchId: String)

/** What the watch still holds, published as a DataItem so the phone knows about it without syncing. */
@Serializable
data class QueueSummary(
    val matchInProgress: Boolean,
    val matches: List<PendingMatch>,
)

@Serializable
data class PendingMatch(
    val matchId: String,
    /** Time of MATCH_START, or of the oldest queued event if MATCH_START was already synced. */
    val startedAt: Long,
    /** All queued events of the match, structural ones included — what a sync will send. */
    val eventCount: Int,
    /** Queued voice recordings only — what the user thinks of as the match's events. */
    val recordingCount: Int,
)

/** JSON encoding of the messages; unknown fields are ignored so either side can add fields later. */
object SyncCodec {

    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    inline fun <reified T> encode(message: T): ByteArray =
        json.encodeToString(serializer<T>(), message).encodeToByteArray()

    inline fun <reified T> decode(bytes: ByteArray): T =
        json.decodeFromString(serializer<T>(), bytes.decodeToString())
}
