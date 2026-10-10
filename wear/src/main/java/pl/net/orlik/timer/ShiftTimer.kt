package pl.net.orlik.timer

/** Shift timer state, see docs section 8 ("Timer zmian"). */
sealed interface ShiftTimerState {
    /** No match in progress. */
    data object Idle : ShiftTimerState

    /** Counting down to the next substitution. */
    data class Running(val remainingMillis: Long) : ShiftTimerState

    /** Interval elapsed; the alarm repeats until the substitution is confirmed. */
    data class AwaitingConfirmation(val overdueMillis: Long) : ShiftTimerState
}

/**
 * Substitution timer of a match in progress. Counts match **play time**, so it stops
 * during a pause together with the match clock, and an alarm state survives the pause.
 *
 * [lastResetPlayMillis] is the play time of the last confirmed substitution (0 at match start).
 * The interval is fixed for the whole match (settings are read once, at match start).
 */
data class ShiftTimer(
    val intervalMillis: Long,
    val lastResetPlayMillis: Long = 0,
) {
    init {
        require(intervalMillis > 0) { "Interval must be positive" }
    }

    /** State at match play time [playMillis]. Never [ShiftTimerState.Idle]. */
    fun stateAt(playMillis: Long): ShiftTimerState {
        val remaining = intervalMillis - (playMillis - lastResetPlayMillis)
        return if (remaining > 0) {
            ShiftTimerState.Running(remaining)
        } else {
            ShiftTimerState.AwaitingConfirmation(overdueMillis = -remaining)
        }
    }

    /**
     * Confirms the substitution at play time [playMillis]: the full interval starts again
     * from now (not from the moment the alarm went off — a late substitution delays the next one).
     * Only allowed while awaiting confirmation; otherwise returns this timer unchanged.
     */
    fun confirm(playMillis: Long): ShiftTimer =
        if (stateAt(playMillis) is ShiftTimerState.AwaitingConfirmation) {
            copy(lastResetPlayMillis = playMillis)
        } else {
            this
        }
}
