package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryServicePagerPolicyTest {
    @Test fun fourPagesHaveStableOrderAndSettingsIsNotAPage() {
        assertEquals(listOf(AppTab.SCHEDULE, AppTab.BARS, AppTab.MAIL, AppTab.MAPS), PRIMARY_TABS)
        assertEquals(0, primaryPageFor(null))
        assertEquals(0, primaryPageFor(AppTab.SETTINGS))
        assertEquals(1, primaryPageFor(AppTab.BARS))
        assertEquals(3, primaryPageFor(AppTab.MAPS))
    }

    @Test fun barsRemainsMountedButNonAdjacentPagesAreLazy() {
        for (current in PRIMARY_TABS.indices) {
            assertTrue(shouldComposePrimaryPage(1, current, current))
        }
        assertFalse(shouldComposePrimaryPage(3, 0, 0))
        assertTrue(shouldComposePrimaryPage(3, 0, 3))
        assertTrue(shouldComposePrimaryPage(2, 1, 1))
    }

    @Test fun distantTabTapsTravelOneVirtualPageInEitherDirection() {
        val forward = adjacentProgrammaticPages(AppTab.SCHEDULE, AppTab.MAPS)
        assertEquals(AppTab.SCHEDULE, forward[0])
        assertEquals(AppTab.MAPS, forward[1])
        assertEquals(PRIMARY_TABS.toSet(), forward.toSet())
        val backward = adjacentProgrammaticPages(AppTab.MAPS, AppTab.SCHEDULE)
        assertEquals(AppTab.MAPS, backward[3])
        assertEquals(AppTab.SCHEDULE, backward[2])
        assertEquals(PRIMARY_TABS.toSet(), backward.toSet())
        assertEquals(PRIMARY_TABS, adjacentProgrammaticPages(AppTab.BARS, AppTab.MAIL))
        assertEquals(PRIMARY_TABS, adjacentProgrammaticPages(AppTab.MAIL, AppTab.MAIL))
    }

    @Test fun barsCompositionUsesItsKeyAfterVirtualReordering() {
        val order = adjacentProgrammaticPages(AppTab.SCHEDULE, AppTab.MAPS)
        val barsIndex = order.indexOf(AppTab.BARS)
        assertTrue(shouldComposePrimaryPage(barsIndex, 0, 1, isBarsPage = true))
        assertFalse(shouldComposePrimaryPage(barsIndex, 0, 1, isBarsPage = false))
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
