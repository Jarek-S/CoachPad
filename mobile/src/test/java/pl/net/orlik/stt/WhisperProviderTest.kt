package pl.net.orlik.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperProviderTest {

    @Test
    fun whisperPromptOf_listsDistinctWords() {
        assertEquals("gol, asysta, Marcin", whisperPromptOf(listOf("gol", " asysta ", "", "Marcin", "gol")))
        assertNull(whisperPromptOf(listOf(" ", "")))
    }

    @Test
    fun whisperPromptOf_staysShort() {
        val prompt = whisperPromptOf(List(200) { "Zawodnik$it" })!!
        assertTrue(prompt.length <= 600)
    }

    @Test
    fun whisperTextOf_readsText() {
        assertEquals("Gol Marcin, asysta Jurek", whisperTextOf("""{"text": " Gol Marcin, asysta Jurek "}"""))
        assertEquals("", whisperTextOf("""{"text": ""}"""))
    }

    @Test
    fun whisperTextOf_nullWithoutText() {
        assertNull(whisperTextOf("""{"error": {"message": "x"}}"""))
        assertNull(whisperTextOf("not json"))
    }

    @Test
    fun sttErrorOf_mapsStatus() {
        assertEquals(SttError.UNAUTHORIZED, sttErrorOf(401))
        assertEquals(SttError.RATE_LIMITED, sttErrorOf(429))
        assertEquals(SttError.SERVICE, sttErrorOf(500))
        assertEquals(SttError.SERVICE, sttErrorOf(400))
    }
}
