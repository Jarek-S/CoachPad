package pl.net.orlik.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.net.orlik.timer.ShiftTimer

class ShiftTimerSettingsTest {

    @Test
    fun defaults_disabled_fiveMinutes() {
        val defaults = ShiftTimerSettings()
        assertEquals(false, defaults.enabled)
        assertEquals(5, defaults.intervalMinutes)
        assertNull(defaults.toShiftTimerOrNull())
    }

    @Test
    fun enabled_givesTimerWithInterval() {
        assertEquals(ShiftTimer(12 * 60_000L), ShiftTimerSettings(enabled = true, intervalMinutes = 12).toShiftTimerOrNull())
    }

    @Test
    fun intervalOutsideRange_isClamped() {
        assertEquals(ShiftTimer(60_000L), ShiftTimerSettings(enabled = true, intervalMinutes = 0).toShiftTimerOrNull())
        assertEquals(ShiftTimer(15 * 60_000L), ShiftTimerSettings(enabled = true, intervalMinutes = 99).toShiftTimerOrNull())
    }
}
