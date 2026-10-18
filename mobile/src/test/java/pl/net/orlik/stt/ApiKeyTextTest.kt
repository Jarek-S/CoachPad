package pl.net.orlik.stt

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiKeyTextTest {

    @Test
    fun cleanApiKey_dropsSpacesAndLineBreaks() {
        assertEquals("sk-proj-abc123", cleanApiKey("  sk-proj-\nabc 123\r\n"))
    }

    @Test
    fun maskedApiKey_showsStartAndLastFour() {
        assertEquals("sk-…a1B2", maskedApiKey("sk-proj-0123456789a1B2"))
        assertEquals("…ef", maskedApiKey("abcdef"))
    }
}
