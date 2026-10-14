package pl.net.orlik.audio

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoudnessTest {

    private val rate = 16_000

    /** A tone whose RMS is [dbfs]. */
    private fun tone(dbfs: Double, seconds: Double): ShortArray {
        val amplitude = 32_768 * 10.0.pow(dbfs / 20) * kotlin.math.sqrt(2.0)
        return ShortArray((rate * seconds).toInt()) { i -> (amplitude * sin(2 * PI * 440 * i / rate)).toInt().toShort() }
    }

    @Test
    fun level_ofAToneIsItsRms() {
        assertEquals(-50.0, speechLevelDbfs(tone(-50.0, 2.0), rate)!!, 0.5)
    }

    @Test
    fun level_ignoresLeadingSilenceAndASingleClick() {
        val samples = ShortArray(rate / 2) + tone(-50.0, 3.0)
        // A tap on the watch: 50 ms at full scale
        for (i in rate until rate + rate / 20) samples[i] = if (i % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE
        assertEquals(-50.0, speechLevelDbfs(samples, rate)!!, 1.0)
    }

    @Test
    fun level_ofSilenceIsNone() {
        assertNull(speechLevelDbfs(ShortArray(rate), rate))
        assertNull(speechLevelDbfs(ShortArray(10), rate))
    }

    @Test
    fun gain_raisesQuietOnly_upToTheLimit() {
        assertEquals(3_000, playbackGainMillibels(-50.0))
        assertEquals(1_000, playbackGainMillibels(-30.0))
        // Loud recordings are left as they are
        assertEquals(0, playbackGainMillibels(-12.0))
        // Beyond the limit: noise rather than speech
        assertEquals(3_000, playbackGainMillibels(-70.0))
        assertEquals(0, playbackGainMillibels(null))
    }
}
