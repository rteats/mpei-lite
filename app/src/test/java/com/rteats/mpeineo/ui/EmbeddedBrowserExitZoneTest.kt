package com.rteats.mpeineo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedBrowserExitZoneTest {
    @Test fun intentionalRightSwipeExitsRegardlessOfBrowserHistory() {
        assertTrue(shouldExitEmbeddedBrowserSwipe(true, 64f, 64f))
        assertTrue(shouldExitEmbeddedBrowserSwipe(true, 200f, 64f))
    }

    @Test fun leftSwipesTinyDragsAndUnrelatedTouchStartsAreIgnored() {
        assertFalse(shouldExitEmbeddedBrowserSwipe(true, -85f, 64f))
        assertFalse(shouldExitEmbeddedBrowserSwipe(true, 63f, 64f))
        assertFalse(shouldExitEmbeddedBrowserSwipe(false, 120f, 64f))
        assertFalse(shouldExitEmbeddedBrowserSwipe(true, 120f, 0f))
    }
}
