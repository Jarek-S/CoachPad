package pl.net.orlik.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import pl.net.orlik.shared.Pause
import pl.net.orlik.shared.playTimeMillis

class ShiftTimerTest {

    private fun min(m: Int) = m * 60_000L

    private val timer = ShiftTimer(intervalMillis = min(5))

    @Test
    fun atStart_fullIntervalRemains() {
        assertEquals(ShiftTimerState.Running(min(5)), timer.stateAt(0))
    }

    @Test
    fun countsDown_withPlayTime() {
        assertEquals(ShiftTimerState.Running(min(5) - 134_000), timer.stateAt(134_000))
    }

    @Test
    fun atZero_awaitsConfirmation() {
        assertEquals(ShiftTimerState.AwaitingConfirmation(0), timer.stateAt(min(5)))
    }

    @Test
    fun noAutomaticReset_overdueKeepsGrowing() {
        assertEquals(ShiftTimerState.AwaitingConfirmation(min(3)), timer.stateAt(min(8)))
    }

    @Test
    fun confirm_restartsFullIntervalFromConfirmationMoment() {
        // Alarm at 5:00, substitution confirmed late at 6:30 → next alarm at 11:30
        val confirmed = timer.confirm(min(6) + 30_000)

        assertEquals(ShiftTimerState.Running(min(5)), confirmed.stateAt(min(6) + 30_000))
        assertEquals(ShiftTimerState.Running(1_000), confirmed.stateAt(min(11) + 29_000))
        assertEquals(ShiftTimerState.AwaitingConfirmation(0), confirmed.stateAt(min(11) + 30_000))
    }

    @Test
    fun confirm_whileRunning_isIgnored() {
        assertSame(timer, timer.confirm(min(2)))
    }

    @Test
    fun pause_stopsTimer_becauseItFollowsPlayTime() {
        // Pause from 3:00 to 10:00 of wall time: the timer stands still meanwhile,
        // and the alarm fires at wall 12:00 (play 5:00), not at wall 5:00
        val pauses = listOf(Pause(pausedAt = min(3), resumedAt = min(10)))
        val play = { wall: Long -> playTimeMillis(wall, matchStart = 0, pauses = pauses) }

        assertEquals(ShiftTimerState.Running(min(2)), timer.stateAt(play(min(4))))
        assertEquals(ShiftTimerState.Running(min(2)), timer.stateAt(play(min(9))))
        assertEquals(ShiftTimerState.AwaitingConfirmation(0), timer.stateAt(play(min(12))))
    }

    @Test
    fun alarmState_survivesPause() {
        // Alarm at play 5:00, match paused at play 6:00 for 10 min, still awaiting after resume
        val pauses = listOf(Pause(pausedAt = min(6), resumedAt = min(16)))
        val play = { wall: Long -> playTimeMillis(wall, matchStart = 0, pauses = pauses) }

        assertEquals(ShiftTimerState.AwaitingConfirmation(min(1)), timer.stateAt(play(min(10))))
        assertEquals(ShiftTimerState.AwaitingConfirmation(min(2)), timer.stateAt(play(min(17))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroInterval_isRejected() {
        ShiftTimer(intervalMillis = 0)
    }
}
