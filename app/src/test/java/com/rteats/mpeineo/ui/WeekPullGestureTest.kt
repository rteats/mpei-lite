package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
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
}
