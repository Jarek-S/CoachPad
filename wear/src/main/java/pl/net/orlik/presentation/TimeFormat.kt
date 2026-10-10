package pl.net.orlik.presentation

import pl.net.orlik.timer.ShiftTimerState

/** Formats play time as minutes:seconds, e.g. 95:07. Minutes are not wrapped at 60. */
fun formatPlayTime(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/**
 * Time to the next substitution, e.g. 2:14, or how late it is once the interval is over, e.g. +1:20.
 * The countdown is rounded up, so it starts at the full interval and shows 0:00 only at the alarm.
 */
fun formatShiftTime(state: ShiftTimerState): String = when (state) {
    is ShiftTimerState.Running -> formatPlayTime((state.remainingMillis + 999) / 1000 * 1000)
    is ShiftTimerState.AwaitingConfirmation -> "+" + formatPlayTime(state.overdueMillis)
    ShiftTimerState.Idle -> ""
}
