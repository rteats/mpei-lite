package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPullGestureTest {
    private val max = 180f
    private val threshold = 76f

    @Test fun insideListScrollsNormally() {
        assertEquals(
            WeekPullStep(0f, 0f),
            advanceWeekPull(0f, 40f, atStart = false, atEnd = false, maxDistance = max),
        )
    }

    @Test fun previousWeekOnlyArmsAtTop() {
        val pull = advanceWeekPull(0f, 80f, atStart = true, atEnd = false, maxDistance = max)
        assertEquals(WeekPullStep(80f, 80f), pull)
        assertEquals(-1, weekDeltaForPull(pull.distance, threshold))
        assertEquals(0, weekDeltaForPull(75.99f, threshold))
    }

    @Test fun nextWeekOnlyArmsAtBottom() {
        val pull = advanceWeekPull(0f, -80f, atStart = false, atEnd = true, maxDistance = max)
        assertEquals(WeekPullStep(-80f, -80f), pull)
        assertEquals(1, weekDeltaForPull(pull.distance, threshold))
        assertEquals(0, weekDeltaForPull(-75.99f, threshold))
    }

    @Test fun reversingPullDisarmsBeforeListScrolls() {
        val cancel = advanceWeekPull(90f, -50f, atStart = true, atEnd = false, maxDistance = max)
        assertEquals(WeekPullStep(40f, -50f), cancel)
        assertEquals(0, weekDeltaForPull(cancel.distance, threshold))
        val resume = advanceWeekPull(40f, -70f, atStart = true, atEnd = false, maxDistance = max)
        assertEquals(WeekPullStep(0f, -40f), resume)
        // Only the consumed portion collapses the pull; the remaining -30px
        // continues through nested scroll to scroll the day's list.
    }

    @Test fun reversingBottomPullPassesExcessToList() {
        val resume = advanceWeekPull(-30f, 50f, atStart = false, atEnd = true, maxDistance = max)
        assertEquals(WeekPullStep(0f, 30f), resume)
    }

    @Test fun pullingPastMaximumRemainsConsumedAndClamped() {
        assertEquals(
            WeekPullStep(180f, 300f),
            advanceWeekPull(0f, 300f, atStart = true, atEnd = false, maxDistance = max),
        )
        assertEquals(
            WeekPullStep(-180f, -300f),
            advanceWeekPull(0f, -300f, atStart = false, atEnd = true, maxDistance = max),
        )
    }

    @Test fun releasingUnderThresholdNeverSwitches() {
        assertEquals(0, weekDeltaForPull(0f, threshold))
        assertEquals(0, weekDeltaForPull(50f, threshold))
        assertEquals(0, weekDeltaForPull(-50f, threshold))
    }
    @Test fun crossingThresholdTriggersOneHapticPerGesture() {
        assertFalse(shouldTickWeekPull(0f, 75f, threshold, alreadyTicked = false))
        assertTrue(shouldTickWeekPull(75f, 76f, threshold, alreadyTicked = false))
        assertFalse(shouldTickWeekPull(76f, 120f, threshold, alreadyTicked = true))
        assertFalse(shouldTickWeekPull(110f, 50f, threshold, alreadyTicked = true))
        assertFalse(shouldTickWeekPull(50f, 90f, threshold, alreadyTicked = true))
        // Releasing starts a new gesture and permits one new threshold tick.
        assertTrue(shouldTickWeekPull(0f, -80f, threshold, alreadyTicked = false))
    }

    @Test fun mpeiStudyWeeksFollowSemesterRatherThanIsoWeeks() {
        assertEquals(1, studyWeekNumber(java.time.LocalDate.of(2026, 8, 31)))
        assertEquals(7, studyWeekNumber(java.time.LocalDate.of(2026, 10, 12)))
        assertEquals(1, studyWeekNumber(java.time.LocalDate.of(2027, 2, 1)))
        assertEquals(2, studyWeekNumber(java.time.LocalDate.of(2027, 2, 8)))
    }

    @Test fun weekAnimationDirectionMatchesVerticalScroll() {
        // Scroll forward: new week arrives below the viewport and the old
        // one exits above. Reverse the direction for the previous week.
        assertEquals(1, weekTravelDirection(0, 1))
        assertEquals(-1, weekTravelDirection(1, 0))
        assertEquals(1, weekTravelDirection(-2, 0))
        assertEquals(-1, weekTravelDirection(3, -1))
    }

    @Test fun adjacentWeekPreviewUsesRealMondaySundayBounds() {
        val monday = java.time.LocalDate.of(2026, 12, 28)
        assertEquals(
            java.time.LocalDate.of(2026, 12, 21) to
                java.time.LocalDate.of(2026, 12, 27),
            weekRange(monday, -1),
        )
        assertEquals(
            java.time.LocalDate.of(2027, 1, 4) to
                java.time.LocalDate.of(2027, 1, 10),
            weekRange(monday, 1),
        )
    }

}
