package pl.net.orlik.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import java.io.File
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays one recording at a time; starting another stops the current one. Quiet recordings are
 * raised to a common level on playback ([AudioLevels]); the files stay as recorded.
 */
class AudioPlayer(private val scope: CoroutineScope) {

    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var starting: Job? = null

    private val _playingId = MutableStateFlow<String?>(null)

    /** Id of the event whose recording is playing, or null. */
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    /** Plays [file] for event [id], or stops it when it is the one already playing. */
    fun toggle(id: String, file: File) {
        val wasPlaying = _playingId.value == id
        stop()
        if (wasPlaying) return
        // Shown as playing at once; the gain is measured first (once per file, a few milliseconds)
        _playingId.value = id
        starting = scope.launch {
            val gain = AudioLevels.gainMillibels(file)
            try {
                val mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.path)
                    setOnCompletionListener { stop() }
                    prepare()
                }
                player = mediaPlayer
                if (gain > 0) {
                    enhancer = runCatching {
                        LoudnessEnhancer(mediaPlayer.audioSessionId).apply {
                            setTargetGain(gain)
                            enabled = true
                        }
                    }.onFailure { Log.w(TAG, "No loudness enhancer", it) }.getOrNull()
                }
                mediaPlayer.start()
            } catch (e: Exception) {
                Log.w(TAG, "Cannot play $file", e)
                stop()
            }
        }
    }

    /** How far the playing recording has got, in milliseconds; 0 when nothing plays. */
    fun positionMillis(): Long = runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0

    fun stop() {
        starting?.cancel()
        starting = null
        enhancer?.release()
        enhancer = null
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

/** Playback gain of each recording, measured once and kept for the life of the process. */
object AudioLevels {

    private val cache = mutableMapOf<String, Int>()

    /** Gain in millibels for [file]; 0 when it cannot be decoded. */
    suspend fun gainMillibels(file: File): Int {
        synchronized(cache) { cache[file.path]?.let { return it } }
        val gain = withContext(Dispatchers.Default) {
            runCatching { decodePcm(file) }
                .onFailure { Log.w("AudioLevels", "Cannot decode $file", it) }
                .getOrNull()
                ?.let { (samples, rate) ->
                    val level = speechLevelDbfs(samples, rate)
                    playbackGainMillibels(level).also { Log.d("AudioLevels", "${file.name}: speech $level dBFS, gain $it mB") }
                }
                ?: 0
        }
        synchronized(cache) { cache[file.path] = gain }
        return gain
    }
}

/** Decodes the first audio track of [file] to 16-bit mono samples and their sample rate. */
private fun decodePcm(file: File): Pair<ShortArray, Int> {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.path)
        val track = (0 until extractor.trackCount).first {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
        val format = extractor.getTrackFormat(track)
        extractor.selectTrack(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            return drain(extractor, codec, format)
        } finally {
            codec.release()
        }
    } finally {
        extractor.release()
    }
}

private fun drain(extractor: MediaExtractor, codec: MediaCodec, input: MediaFormat): Pair<ShortArray, Int> {
    var rate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    val out = ArrayList<Short>()
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    while (true) {
        if (!inputDone) {
            val index = codec.dequeueInputBuffer(TIMEOUT_MICROS)
            if (index >= 0) {
                val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                if (size < 0) {
                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }
        val index = codec.dequeueOutputBuffer(info, TIMEOUT_MICROS)
        when {
            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
            index >= 0 -> {
                val bytes = codec.getOutputBuffer(index)!!
                bytes.position(info.offset)
                bytes.limit(info.offset + info.size)
                val shorts = bytes.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                // Only the first channel: the watch records mono anyway
                for (i in 0 until shorts.limit() step channels) out += shorts.get(i)
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return out.toShortArray() to rate
            }
        }
    }
}

private const val TIMEOUT_MICROS = 10_000L
