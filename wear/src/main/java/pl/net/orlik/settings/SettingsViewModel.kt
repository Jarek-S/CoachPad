package pl.net.orlik.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Edits the settings; every change is saved at once. A match in progress keeps its own snapshot. */
class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    private val _settings = MutableStateFlow(store.shiftTimerSettings())
    val settings: StateFlow<ShiftTimerSettings> = _settings.asStateFlow()

    fun setShiftTimerEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    /** Changes the interval by [deltaMinutes], staying within the allowed range. */
    fun changeInterval(deltaMinutes: Int) = update {
        it.copy(intervalMinutes = (it.intervalMinutes + deltaMinutes).coerceIn(ShiftTimerSettings.INTERVAL_MINUTES_RANGE))
    }

    private fun update(change: (ShiftTimerSettings) -> ShiftTimerSettings) {
        val updated = change(_settings.value)
        _settings.value = updated
        store.saveShiftTimerSettings(updated)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(PreferencesSettingsStore(this[APPLICATION_KEY]!!)) }
        }
    }
}
