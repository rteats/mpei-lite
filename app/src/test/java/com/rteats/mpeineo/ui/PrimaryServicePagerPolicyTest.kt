package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryServicePagerPolicyTest {
    @Test fun fivePagesHaveStableOrderAndSettingsIsNotAPage() {
        assertEquals(listOf(AppTab.HOME, AppTab.SCHEDULE, AppTab.BARS, AppTab.MAIL, AppTab.MAPS), PRIMARY_TABS)
        assertEquals(0, primaryPageFor(null))
        assertEquals(0, primaryPageFor(AppTab.SETTINGS))
        assertEquals(2, primaryPageFor(AppTab.BARS))
        assertEquals(4, primaryPageFor(AppTab.MAPS))
    }

    @Test fun barsRemainsMountedButNonAdjacentPagesAreLazy() {
        for (current in PRIMARY_TABS.indices) {
            assertTrue(shouldComposePrimaryPage(2, current, current))
        }
        assertFalse(shouldComposePrimaryPage(4, 0, 0))
        assertTrue(shouldComposePrimaryPage(4, 0, 4))
        assertTrue(shouldComposePrimaryPage(3, 2, 2))
    }

    @Test fun disabledPagerRestoresMailInsteadOfShowingMaps() {
        assertEquals(
            3,
            pageToRestoreForRoute(
                route = AppTab.MAIL,
                visiblePage = 4,
                offsetFraction = 0f,
                pendingUserSwipe = false,
                swipeEnabled = false,
                programmatic = false,
            ),
        )
        assertEquals(
            3,
            pageToRestoreForRoute(
                route = AppTab.MAIL,
                visiblePage = 3,
                offsetFraction = 0.3f,
                pendingUserSwipe = true,
                swipeEnabled = false,
                programmatic = false,
            ),
        )
    }

    @Test fun committedRouteIsStableWithoutAPendingGesture() {
        assertEquals(
            3,
            pageToRestoreForRoute(AppTab.MAIL, 3, 0f, false, true, false),
        )
        assertEquals(
            null,
            pageToRestoreForRoute(AppTab.MAIL, 2, 0f, false, true, false),
        )
    }

    @Test fun realSwipeMaySettleButProgrammaticMovementMustNotBeRepaired() {
        assertEquals(
            null,
            pageToRestoreForRoute(AppTab.MAIL, 4, 0f, true, true, false),
        )
        assertEquals(
            null,
            pageToRestoreForRoute(AppTab.MAIL, 4, 0f, false, false, true),
        )
    }

    @Test fun serviceTitlesRemainScopedToTheirDestinations() {
        assertEquals(SearchScope.SCHEDULE, searchAppBarContext(AppTab.SCHEDULE).scope)
        assertEquals(SearchScope.BARS, searchAppBarContext(AppTab.BARS).scope)
        assertEquals(SearchScope.MAIL, searchAppBarContext(AppTab.MAIL).scope)
        assertEquals(SearchScope.MAPS, searchAppBarContext(AppTab.MAPS).scope)
    }

    @Test fun nestedAndImeGesturesDoNotChangeRootTab() {
        fun allowed(
            root: Boolean = true, search: Boolean = false, ime: Boolean = false,
            mailWeb: Boolean = false, barsWeb: Boolean = false,
            mailDetail: Boolean = false, barsDetail: Boolean = false,
        ) = canSwipePrimaryPages(root, search, ime, mailWeb, barsWeb, mailDetail, barsDetail)
        assertTrue(allowed())
        assertFalse(allowed(root = false))
        assertFalse(allowed(search = true))
        assertFalse(allowed(ime = true))
        assertFalse(allowed(mailWeb = true))
        assertFalse(allowed(barsWeb = true))
        assertFalse(allowed(mailDetail = true))
        assertFalse(allowed(barsDetail = true))
    }
}
