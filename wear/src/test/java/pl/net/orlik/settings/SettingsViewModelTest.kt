package pl.net.orlik.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {

    private val store = object : SettingsStore {
        var saved = ShiftTimerSettings()
        override fun shiftTimerSettings() = saved
        override fun saveShiftTimerSettings(settings: ShiftTimerSettings) {
            saved = settings
        }
    }
    private val viewModel = SettingsViewModel(store)

    @Test
    fun enabling_isSavedAtOnce() {
        viewModel.setShiftTimerEnabled(true)

        assertEquals(true, store.saved.enabled)
        assertEquals(store.saved, viewModel.settings.value)
    }

    @Test
    fun interval_staysWithinRange() {
        repeat(20) { viewModel.changeInterval(+1) }
        assertEquals(15, store.saved.intervalMinutes)

        repeat(20) { viewModel.changeInterval(-1) }
        assertEquals(1, store.saved.intervalMinutes)
    }
}
