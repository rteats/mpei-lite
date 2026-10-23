package com.rteats.mpeineo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedBackSwipeTest {
    @Test fun nestedPopCommitsAfterRightSwipeThreshold() {
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

    @Test fun barsDisciplineSwipeDoesNotNavigateRootTab() {
        assertTrue(
            barsSwipeTarget(
                foreground = true, isTransitioning = false, imeVisible = false,
                browserVisible = false, authenticated = true, detailOpen = true,
            ) == BarsSwipeTarget.DISCIPLINE_OVERVIEW,
        )
        assertTrue(
            barsSwipeTarget(
                foreground = true, isTransitioning = false, imeVisible = false,
                browserVisible = false, authenticated = true, detailOpen = false,
            ) == BarsSwipeTarget.DISABLED,
        )
    }

    @Test fun barsGestureNeverInterceptsPortalImeTransitionOrBackground() {
        fun target(
            foreground: Boolean = true,
            transitioning: Boolean = false,
            keyboard: Boolean = false,
            browser: Boolean = false,
            authenticated: Boolean = true,
        ): BarsSwipeTarget = barsSwipeTarget(
            foreground = foreground,
            isTransitioning = transitioning,
            imeVisible = keyboard,
            browserVisible = browser,
            authenticated = authenticated,
            detailOpen = true,
        )
        assertTrue(target(foreground = false) == BarsSwipeTarget.DISABLED)
        assertTrue(target(transitioning = true) == BarsSwipeTarget.DISABLED)
        assertTrue(target(keyboard = true) == BarsSwipeTarget.DISABLED)
        assertTrue(target(browser = true) == BarsSwipeTarget.DISABLED)
        assertTrue(target(authenticated = false) == BarsSwipeTarget.DISABLED)
    }

    @Test fun nestedBarsBackUsesNormalFullWidthGestureThreshold() {
        assertTrue(acceptsServiceBackStart(
            540f, 1080f, 24f, mapEdgeZone = false,
        ))
        assertTrue(shouldCommitServiceBackSwipe(216f, 1080f))
        assertFalse(shouldCommitServiceBackSwipe(215f, 1080f))
    }

    @Test fun invalidWidthCannotTriggerNavigation() {
        assertFalse(shouldCommitServiceBackSwipe(300f, 0f))
    }
}
