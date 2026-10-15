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

    @Test fun mapSwipesBeginOnlyInReservedEdgeStrip() {
        assertFalse(acceptsServiceBackStart(10f, 96f, 24f, mapEdgeZone = true))
        assertTrue(acceptsServiceBackStart(36f, 96f, 24f, mapEdgeZone = true))
        assertTrue(acceptsServiceBackStart(85f, 96f, 24f, mapEdgeZone = true))
        assertTrue(acceptsServiceBackStart(500f, 1080f, 24f, mapEdgeZone = false))
        assertFalse(acceptsServiceBackStart(1070f, 1080f, 24f, mapEdgeZone = false))
    }

    @Test fun invalidWidthCannotTriggerNavigation() {
        assertFalse(shouldCommitServiceBackSwipe(300f, 0f))
    }
}
