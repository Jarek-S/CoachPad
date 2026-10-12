package pl.net.orlik.shared.sync

/** Wearable Data Layer paths shared by the watch and the phone. */
object DataLayerPaths {

    /** Phone → watch, [SyncRequest]. */
    const val SYNC_REQUEST = "/sync/request"

    /** Watch → phone, [SyncRejected]. */
    const val SYNC_REJECTED = "/sync/rejected"

    /** Watch → phone, [EventMetadata]; one message per event. */
    const val EVENT = "/sync/event"

    /** Phone → watch, [Ack]. */
    const val ACK = "/sync/ack"

    /** Phone → watch, [DeleteMatch]. */
    const val DELETE_MATCH = "/match/delete"

    /** Watch DataItem holding the [QueueSummary]. */
    const val QUEUE_SUMMARY = "/queue/summary"

    /** DataMap key of the JSON-encoded [QueueSummary] in the [QUEUE_SUMMARY] item. */
    const val QUEUE_SUMMARY_KEY = "summary"

    /** Prefix of the ChannelClient path that carries one event's audio file. */
    private const val AUDIO_PREFIX = "/sync/audio/"

    fun audioChannel(eventId: String) = AUDIO_PREFIX + eventId

    /** Event id from an [audioChannel] path, or null for any other path. */
    fun eventIdFromAudioChannel(path: String): String? =
        path.removePrefix(AUDIO_PREFIX).takeIf { path.startsWith(AUDIO_PREFIX) && it.isNotEmpty() }
}
