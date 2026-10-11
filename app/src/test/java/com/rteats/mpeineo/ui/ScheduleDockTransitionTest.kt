package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleDockTransitionTest {
    @Test fun visibleWhenScheduleIsSelected() {
        assertEquals(1f, scheduleDockRevealProgress(0, 0f), 0.0001f)
    }

    @Test fun swipeOutTracksFingerProgress() {
        assertEquals(0.75f, scheduleDockRevealProgress(0, 0.25f), 0.0001f)
        assertEquals(0.5f, scheduleDockRevealProgress(0, 0.5f), 0.0001f)
        assertEquals(0.25f, scheduleDockRevealProgress(1, -0.25f), 0.0001f)
        assertEquals(0f, scheduleDockRevealProgress(1, 0f), 0.0001f)
    }

    @Test fun reverseSwipeRevealsDockSmoothly() {
        assertEquals(0.3f, scheduleDockRevealProgress(1, -0.3f), 0.0001f)
        assertEquals(0.7f, scheduleDockRevealProgress(0, 0.3f), 0.0001f)
    }

    @Test fun scheduleDockStaysHiddenOnOtherPages() {
        assertEquals(0f, scheduleDockRevealProgress(2, 0f), 0.0001f)
        assertEquals(0f, scheduleDockRevealProgress(3, 0f), 0.0001f)
    }
}
