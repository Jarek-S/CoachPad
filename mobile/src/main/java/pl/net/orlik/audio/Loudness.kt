package pl.net.orlik.audio

import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Evening out the loudness of recordings on playback: the watch's microphone picks up very
// different levels (a sleeve over it made speech ~30 dB quieter in the first field test).

/** Level quiet recordings are raised to, in dBFS. */
const val TARGET_SPEECH_DBFS = -20.0

/** Most a recording is raised by: more would mostly amplify noise. */
const val MAX_GAIN_DB = 30.0

private const val WINDOW_MILLIS = 50

/** Windows quieter than this are digital silence (e.g. the start of the file), not sound. */
private const val SILENCE_DBFS = -90.0

/**
 * Speech level of 16-bit [samples] in dBFS: the 90th percentile of the RMS of 50 ms windows.
 * Speech is the loud part of a voice note; a percentile rather than the peak keeps a single
 * click (a tap on the watch) from passing for loud speech. Null when there is no sound at all.
 */
fun speechLevelDbfs(samples: ShortArray, sampleRate: Int): Double? {
    val window = sampleRate * WINDOW_MILLIS / 1_000
    if (window == 0 || samples.size < window) return null
    val levels = (0..samples.size - window step window)
        .map { start -> windowDbfs(samples, start, window) }
        .filter { it > SILENCE_DBFS }
        .sorted()
    if (levels.isEmpty()) return null
    return levels[((levels.size - 1) * 0.9).roundToInt()]
}

/** Playback gain in millibels (1/100 dB) bringing [levelDbfs] up to the target; never turns down. */
fun playbackGainMillibels(levelDbfs: Double?): Int {
    if (levelDbfs == null) return 0
    val gainDb = (TARGET_SPEECH_DBFS - levelDbfs).coerceIn(0.0, MAX_GAIN_DB)
    return (gainDb * 100).roundToInt()
}

private fun windowDbfs(samples: ShortArray, start: Int, length: Int): Double {
    var sum = 0.0
    for (i in start until start + length) {
        val s = samples[i].toDouble()
        sum += s * s
    }
    val rms = sqrt(sum / length)
    return if (rms == 0.0) Double.NEGATIVE_INFINITY else 20 * log10(rms / 32_768)
}
