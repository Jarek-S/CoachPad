package pl.net.orlik.settings

import pl.net.orlik.timer.ShiftTimer

/** Shift timer settings, read at match start (docs section 9); on/off also switched during a match. */
data class ShiftTimerSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
) {
    /** Timer for a new match, or null when the shift timer is disabled. */
    fun toShiftTimerOrNull(): ShiftTimer? =
        if (enabled) ShiftTimer(intervalMillis = intervalMinutes.coerceIn(INTERVAL_MINUTES_RANGE) * 60_000L) else null

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 5
        val INTERVAL_MINUTES_RANGE = 1..15
    }
}

/** Current user settings. */
interface SettingsStore {
    fun shiftTimerSettings(): ShiftTimerSettings

    fun saveShiftTimerSettings(settings: ShiftTimerSettings)
}
