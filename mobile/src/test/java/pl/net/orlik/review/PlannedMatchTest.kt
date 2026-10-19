package pl.net.orlik.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlannedMatchTest {

    @Test
    fun theOnlyPlannedMatch_isTakenOver() {
        assertEquals("p1", plannedMatchFor(5_000, listOf(PlannedMatch("p1", 1_000))))
    }

    @Test
    fun noPlannedMatch_takesNothing() {
        assertNull(plannedMatchFor(5_000, emptyList()))
    }

    @Test
    fun severalPlannedMatches_areLeftToChoose() {
        assertNull(plannedMatchFor(5_000, listOf(PlannedMatch("p1", 1_000), PlannedMatch("p2", 2_000))))
    }

    @Test
    fun matchPlannedAfterTheStart_isNotForThisMatch() {
        // An old match waiting on the watch must not take the lineup planned for the next one
        assertNull(plannedMatchFor(5_000, listOf(PlannedMatch("p1", 6_000))))
    }

    @Test
    fun onlyMatchesPlannedBeforeTheStart_count() {
        assertEquals("p1", plannedMatchFor(5_000, listOf(PlannedMatch("p1", 1_000), PlannedMatch("p2", 6_000))))
    }
}
