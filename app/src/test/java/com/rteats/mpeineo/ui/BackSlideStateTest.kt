package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackSlideStateTest {
    @Test fun dragFollowsTheFingerAndClampsToViewport() {
        assertEquals(0.25f, backSwipeProgress(0f, 250f, 1000f), 0.001f)
        assertEquals(0.10f, backSwipeProgress(0.25f, -150f, 1000f), 0.001f)
        assertEquals(0f, backSwipeProgress(0.10f, -500f, 1000f), 0.001f)
        assertEquals(1f, backSwipeProgress(0.10f, 1500f, 1000f), 0.001f)
        assertEquals(0.25f, backSwipeProgress(0.25f, 100f, 0f), 0.001f)
    }

    @Test fun nestedBrowserMatchesServiceSwipeThreshold() {
        assertFalse(shouldCompleteBackSwipe(0.19f))
        assertTrue(shouldCompleteBackSwipe(0.20f))
        assertTrue(shouldCompleteBackSwipe(0.85f))
        assertTrue(shouldCommitServiceBackSwipe(200f, 1000f))
        assertFalse(shouldCommitServiceBackSwipe(190f, 1000f))
    }

    @Test fun animationsShareRootServiceDurations() {
        assertEquals(260, BACK_SLIDE_OUT_MS)
        assertEquals(310, BACK_SLIDE_IN_MS)
    }
}
