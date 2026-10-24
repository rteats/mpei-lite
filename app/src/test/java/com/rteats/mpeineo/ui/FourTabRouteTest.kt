package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FourTabRouteTest {
    @Test fun coldLaunchStartsOnScheduleAndAllowsSystemBack() {
        val route = FourTabRoute()
        assertEquals(AppTab.SCHEDULE, route.selected)
        assertNull(route.back())
    }

    @Test fun switchingTabsDoesNotAccumulateHistory() {
        val route = FourTabRoute().selectTab(AppTab.BARS)
            .selectTab(AppTab.MAIL).selectTab(AppTab.MAPS)
        assertEquals(AppTab.SCHEDULE, route.back()?.selected)
        assertNull(route.back()?.back())
    }

    @Test fun explicitLinkReturnsToItsOriginOnlyOnce() {
        val route = FourTabRoute().followLink(AppTab.MAPS)
        assertEquals(AppTab.SCHEDULE, route.back()?.selected)
        assertNull(route.back()?.back())
    }

    @Test fun manualTabSelectionClearsLinkOrigin() {
        val route = FourTabRoute().followLink(AppTab.MAPS).selectTab(AppTab.BARS)
        assertEquals(AppTab.SCHEDULE, route.back()?.selected)
    }

    @Test fun invalidAndLegacyHomeRestoreSafely() {
        for (name in listOf(null, "", "HOME", "SETTINGS", "unknown")) {
            assertEquals(AppTab.SCHEDULE, FourTabRoute.restore(name).selected)
        }
        assertEquals(AppTab.MAIL, FourTabRoute.restore("MAIL").selected)
    }

    @Test(expected = IllegalArgumentException::class)
    fun settingsCannotBecomePrimaryTab() {
        FourTabRoute().selectTab(AppTab.SETTINGS)
    }
}
