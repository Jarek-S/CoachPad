package pl.net.orlik.settings

import android.content.Context

/** [SettingsStore] backed by SharedPreferences (a small key-value file in app storage). */
class PreferencesSettingsStore(context: Context) : SettingsStore {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun shiftTimerSettings() = ShiftTimerSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        intervalMinutes = prefs.getInt(KEY_INTERVAL_MINUTES, ShiftTimerSettings.DEFAULT_INTERVAL_MINUTES),
    )

    override fun saveShiftTimerSettings(settings: ShiftTimerSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_INTERVAL_MINUTES, settings.intervalMinutes)
            .apply()
    }

    private companion object {
        const val KEY_ENABLED = "shift_timer_enabled"
        const val KEY_INTERVAL_MINUTES = "shift_timer_interval_minutes"
    }
}
