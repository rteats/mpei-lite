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

    @Test fun barsRootTabSwitchKeepsRetainedLayerStationary() {
        for (tab in listOf(AppTab.SCHEDULE, AppTab.BARS, AppTab.MAIL, AppTab.MAPS)) {
            assertEquals(0f, barsLayerTranslationX(tab, false, 0f, 480f), 0f)
            assertEquals(0f, barsLayerTranslationX(tab, false, 0.5f, 480f), 0f)
        }
    }

    @Test fun linkedBarsPageStillSlidesWithBackProgress() {
        assertEquals(0f, barsLayerTranslationX(AppTab.BARS, true, 1f, 480f), 0f)
        assertEquals(240f, barsLayerTranslationX(AppTab.BARS, true, 0.5f, 480f), 0f)
        assertEquals(480f, barsLayerTranslationX(AppTab.BARS, true, 0f, 480f), 0f)
        assertEquals(0f, barsLayerTranslationX(AppTab.MAIL, true, 0f, 480f), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun settingsCannotBecomePrimaryTab() {
        FourTabRoute().selectTab(AppTab.SETTINGS)
    }
}
