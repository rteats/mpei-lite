package com.rteats.mpeineo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperAppBackSwipeTest {
    @Test fun returnsHomeAfterIntentionalRightSwipe() {
        assertTrue(shouldCommitServiceBackSwipe(216f, 1080f))
        assertTrue(shouldCommitServiceBackSwipe(300f, 1080f))
    }

    @Test fun cancelledOrOppositeDragsDoNotNavigate() {
        assertFalse(shouldCommitServiceBackSwipe(214f, 1080f))
        assertFalse(shouldCommitServiceBackSwipe(-300f, 1080f))
        assertFalse(shouldCommitServiceBackSwipe(50f, 1080f))
    }

    @Test fun invalidWidthCannotTriggerNavigation() {
        assertFalse(shouldCommitServiceBackSwipe(300f, 0f))
    }
}
