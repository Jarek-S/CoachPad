package pl.net.orlik.stt

import java.io.File

/**
 * Turns a recording into text (design doc, section 4, level 3). An implementation calls an external
 * service straight from the phone, with no server of our own in between.
 */
interface SpeechToTextProvider {

    /**
     * [language]: ISO 639-1 code of the speech, e.g. "pl". [hints]: words likely to be said
     * (event words, lineup names), so the service spells them as the lineup does.
     */
    suspend fun transcribe(audio: File, language: String, hints: List<String>): TranscriptResult
}

sealed interface TranscriptResult {
    data class Success(val text: String) : TranscriptResult

    data class Failure(val error: SttError) : TranscriptResult
}

enum class SttError {
    /** The API key is missing, wrong or revoked. */
    UNAUTHORIZED,

    /** Too many requests, or the account has no credit left. */
    RATE_LIMITED,

    /** No connection, or no answer in time. */
    NETWORK,

    /** The service failed or answered something unexpected. */
    SERVICE,
}
