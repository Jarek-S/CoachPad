package pl.net.orlik.audio

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Plays one recording at a time; starting another stops the current one. */
class AudioPlayer {

    private var player: MediaPlayer? = null

    private val _playingId = MutableStateFlow<String?>(null)

    /** Id of the event whose recording is playing, or null. */
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    /** Plays [file] for event [id], or stops it when it is the one already playing. */
    fun toggle(id: String, file: File) {
        val wasPlaying = _playingId.value == id
        stop()
        if (wasPlaying) return
        try {
            player = MediaPlayer().apply {
                setDataSource(file.path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            _playingId.value = id
        } catch (e: Exception) {
            Log.w(TAG, "Cannot play $file", e)
            stop()
        }
    }

    fun stop() {
        player?.release()
        player = null
        _playingId.value = null
    }

    private companion object {
        const val TAG = "AudioPlayer"
    }
}

/** Recording lengths, read from the files once and kept for the life of the process. */
object AudioDurations {

    private val cache = mutableMapOf<String, Long?>()

    /** Length of [file] in milliseconds, or null when it cannot be read. */
    suspend fun of(file: File): Long? {
        synchronized(cache) { if (file.path in cache) return cache[file.path] }
        val millis = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(file.path)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                }
            }.getOrNull()
        }
        synchronized(cache) { cache[file.path] = millis }
        return millis
    }
}
