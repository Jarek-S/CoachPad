package pl.net.orlik.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayTimeTest {

    private val start = 0L

    private fun min(m: Int) = m * 60_000L

    @Test
    fun noPauses_playTimeEqualsElapsed() {
        assertEquals(min(40), playTimeMillis(at = min(40), matchStart = start, pauses = emptyList()))
    }

    @Test
    fun fiveMinutePause_reducesMinuteByFive() {
        // Example from docs: start 14:00, pause 14:30–14:35, goal at 14:40 → 35:00, not 40:00
        val pauses = listOf(Pause(pausedAt = min(30), resumedAt = min(35)))

        assertEquals(min(35), playTimeMillis(at = min(40), matchStart = start, pauses = pauses))
        assertEquals(35, playMinute(at = min(40), matchStart = start, pauses = pauses))
    }

    @Test
    fun multiplePauses_areAllSubtracted() {
        val pauses = listOf(
            Pause(pausedAt = min(10), resumedAt = min(12)),
            Pause(pausedAt = min(20), resumedAt = min(23)),
        )
        assertEquals(min(25), playTimeMillis(at = min(30), matchStart = start, pauses = pauses))
    }

    @Test
    fun openPause_countsUpToMoment() {
        val pauses = listOf(Pause(pausedAt = min(30)))
        // Clock is frozen during the pause
        assertEquals(min(30), playTimeMillis(at = min(33), matchStart = start, pauses = pauses))
    }

    @Test
    fun pauseOngoingAtMoment_countsOnlyUpToMoment() {
        val pauses = listOf(Pause(pausedAt = min(30), resumedAt = min(40)))
        assertEquals(min(30), playTimeMillis(at = min(35), matchStart = start, pauses = pauses))
    }

    @Test
    fun pauseAfterMoment_isIgnored() {
        val pauses = listOf(Pause(pausedAt = min(50), resumedAt = min(55)))
        assertEquals(min(40), playTimeMillis(at = min(40), matchStart = start, pauses = pauses))
    }

    @Test
    fun momentBeforeStart_returnsZero() {
        assertEquals(0, playTimeMillis(at = 1_000, matchStart = 5_000, pauses = emptyList()))
    }

    @Test
    fun playMinute_roundsDown() {
        val at = min(35) + 59_000 // 35:59
        assertEquals(35, playMinute(at = at, matchStart = start, pauses = emptyList()))
    }

    @Test
    fun realTimestamps_workWithNonZeroStart() {
        val matchStart = 1_791_000_000_000L
        val pauses = listOf(Pause(matchStart + min(30), matchStart + min(35)))
        assertEquals(min(35), playTimeMillis(matchStart + min(40), matchStart, pauses))
    }

    @Test
    fun pausesFrom_pairsPauseWithNextResume_andKeepsOpenPause() {
        val events = listOf(
            EventType.MATCH_START to 0L,
            EventType.MATCH_PAUSE to min(10),
            null to min(11),
            EventType.MATCH_RESUME to min(12),
            EventType.MATCH_PAUSE to min(20),
        )

        assertEquals(
            listOf(Pause(min(10), min(12)), Pause(min(20), null)),
            pausesFrom(events, type = { it.first }, at = { it.second }),
        )
    }

    @Test
    fun pausesFrom_ignoresResumeWithoutPause() {
        val events = listOf(EventType.MATCH_START to 0L, EventType.MATCH_RESUME to min(5))

        assertEquals(emptyList<Pause>(), pausesFrom(events, type = { it.first }, at = { it.second }))
    }
}
