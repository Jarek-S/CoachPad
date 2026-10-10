package pl.net.orlik.presentation

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.net.orlik.timer.ShiftTimerState

class TimeFormatTest {

    @Test
    fun zero() = assertEquals("0:00", formatPlayTime(0))

    @Test
    fun secondsArePadded_andTruncated() = assertEquals("5:07", formatPlayTime(5 * 60_000L + 7_999))

    @Test
    fun minutesAreNotWrappedAtAnHour() = assertEquals("95:00", formatPlayTime(95 * 60_000L))

    @Test
    fun shiftCountdown_startsAtFullInterval() =
        assertEquals("5:00", formatShiftTime(ShiftTimerState.Running(5 * 60_000L)))

    @Test
    fun shiftCountdown_isRoundedUp() =
        assertEquals("2:14", formatShiftTime(ShiftTimerState.Running(2 * 60_000L + 13_001)))

    @Test
    fun shiftOverdue_hasPlusSign() =
        assertEquals("+1:20", formatShiftTime(ShiftTimerState.AwaitingConfirmation(80_500)))
}
