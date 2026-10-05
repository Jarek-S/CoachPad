package pl.net.orlik.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun zero() = assertEquals("0:00", formatPlayTime(0))

    @Test
    fun secondsArePadded_andTruncated() = assertEquals("5:07", formatPlayTime(5 * 60_000L + 7_999))

    @Test
    fun minutesAreNotWrappedAtAnHour() = assertEquals("95:00", formatPlayTime(95 * 60_000L))
}
