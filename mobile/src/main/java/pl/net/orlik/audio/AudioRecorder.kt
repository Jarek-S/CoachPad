package pl.net.orlik.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Max length of a single voice recording, as on the watch (design doc, section 8). */
const val MAX_RECORDING_MILLIS = 15_000

/**
 * Records voice notes as AAC in an M4A container, mono, 16 kHz: the same as the watch's recorder,
 * so the phone's recordings play and get recognised alike.
 * Must be used from the main thread.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null

    /**
     * Starts recording to [file]. [onLimitReached] is called on the main thread when
     * [maxDurationMillis] elapses; the caller should then call [stop].
     * Throws if the microphone cannot be opened.
     */
    fun start(file: File, maxDurationMillis: Int = MAX_RECORDING_MILLIS, onLimitReached: () -> Unit) {
        check(recorder == null) { "Already recording" }
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(32_000)
                setMaxDuration(maxDurationMillis)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimitReached()
                }
                setOutputFile(file)
                prepare()
                start()
            }
        } catch (e: Exception) {
            mediaRecorder.release()
            throw e
        }
        recorder = mediaRecorder
    }

    /** Stops recording. Returns false if nothing usable was recorded (e.g. stopped right after start). */
    fun stop(): Boolean {
        val mediaRecorder = recorder ?: return false
        recorder = null
        return try {
            mediaRecorder.stop()
            true
        } catch (e: RuntimeException) {
            false
        } finally {
            mediaRecorder.release()
        }
    }
}
