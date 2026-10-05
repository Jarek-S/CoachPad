package pl.net.orlik.data

/**
 * A voice recording in progress. The event timestamp is the moment recording started
 * (docs section 8). [fileName] is relative to the recordings directory.
 */
data class VoiceRecording(
    val eventId: String,
    val matchId: String,
    val startedAt: Long,
    val fileName: String,
)
