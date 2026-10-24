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
