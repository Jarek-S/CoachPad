package pl.net.orlik.stt

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * OpenAI Whisper over its REST API: the recording (M4A) goes up as a form upload, the text comes
 * back as JSON. [apiKey] is read when a recording is sent, so a key changed in settings applies at once.
 */
class WhisperProvider(
    private val apiKey: suspend () -> String?,
    private val endpoint: String = ENDPOINT,
) : SpeechToTextProvider {

    override suspend fun transcribe(audio: File, language: String, hints: List<String>): TranscriptResult =
        withContext(Dispatchers.IO) {
            val key = apiKey() ?: return@withContext TranscriptResult.Failure(SttError.UNAUTHORIZED)
            val boundary = "----orlik-${UUID.randomUUID()}"
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("Authorization", "Bearer $key")
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connection.outputStream.use { out ->
                    val form = MultipartWriter(out, boundary)
                    form.field("model", MODEL)
                    form.field("language", language)
                    form.field("response_format", "json")
                    whisperPromptOf(hints)?.let { form.field("prompt", it) }
                    form.file("file", audio.name, "audio/mp4", audio)
                    form.finish()
                }
                val status = connection.responseCode
                if (status !in 200..299) return@withContext TranscriptResult.Failure(sttErrorOf(status))
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                whisperTextOf(body)?.let { TranscriptResult.Success(it) } ?: TranscriptResult.Failure(SttError.SERVICE)
            } catch (_: IOException) {
                TranscriptResult.Failure(SttError.NETWORK)
            } finally {
                connection.disconnect()
            }
        }

    private class MultipartWriter(private val out: OutputStream, private val boundary: String) {
        fun field(name: String, value: String) {
            write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
        }

        fun file(name: String, fileName: String, contentType: String, file: File) {
            write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"\r\n")
            write("Content-Type: $contentType\r\n\r\n")
            file.inputStream().use { it.copyTo(out) }
            write("\r\n")
        }

        fun finish() = write("--$boundary--\r\n")

        private fun write(text: String) = out.write(text.toByteArray())
    }

    companion object {
        const val ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
        const val MODEL = "whisper-1"
        private const val CONNECT_TIMEOUT_MILLIS = 15_000
        private const val READ_TIMEOUT_MILLIS = 60_000
    }
}

/**
 * Checks a key before it is saved: listing the models costs nothing and answers 401 for a wrong
 * key. Null when the key works.
 */
suspend fun checkOpenAiKey(key: String, endpoint: String = MODELS_ENDPOINT): SttError? = withContext(Dispatchers.IO) {
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Authorization", "Bearer $key")
        val status = connection.responseCode
        if (status in 200..299) null else sttErrorOf(status)
    } catch (_: IOException) {
        SttError.NETWORK
    } finally {
        connection.disconnect()
    }
}

private const val MODELS_ENDPOINT = "https://api.openai.com/v1/models"

/**
 * Whisper takes a short text written "as if spoken before" the recording; listing the words makes
 * it spell them that way. Null when there is nothing to hint. The service reads only its last
 * ~224 tokens, so the list is cut well before that.
 */
fun whisperPromptOf(hints: List<String>): String? =
    hints.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        .joinToString(", ")
        .take(MAX_PROMPT_CHARS)
        .takeIf { it.isNotEmpty() }

private const val MAX_PROMPT_CHARS = 600

/** The recognised text from `{"text": "…"}`, trimmed; null when the answer has no text field. */
fun whisperTextOf(body: String): String? =
    runCatching { Json.parseToJsonElement(body).jsonObject["text"]?.jsonPrimitive?.content?.trim() }.getOrNull()

fun sttErrorOf(httpStatus: Int): SttError = when (httpStatus) {
    401, 403 -> SttError.UNAUTHORIZED
    429 -> SttError.RATE_LIMITED
    else -> SttError.SERVICE
}
