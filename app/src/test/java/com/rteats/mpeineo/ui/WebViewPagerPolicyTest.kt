package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewPagerPolicyTest {
    @Test fun mailFocusMovementCannotChangeTab() {
        assertTrue(shouldRestoreLockedBrowserPager(AppTab.MAIL, false, 0, 0, 2))
        assertTrue(shouldRestoreLockedBrowserPager(AppTab.MAIL, false, 2, 0, 2))
        assertTrue(shouldRestoreLockedBrowserPager(AppTab.MAIL, false, 2, 2, 0))
        assertFalse(shouldRestoreLockedBrowserPager(AppTab.MAIL, false, 2, 2, 2))
    }

    @Test fun barsFocusMovementCannotChangeTab() {
        assertTrue(shouldRestoreLockedBrowserPager(AppTab.BARS, false, 0, 0, 1))
        assertFalse(shouldRestoreLockedBrowserPager(AppTab.BARS, false, 1, 1, 1))
    }

    @Test fun explicitNavigationBypassesTheLock() {
        assertFalse(shouldRestoreLockedBrowserPager(AppTab.MAIL, true, 0, 0, 2))
        assertFalse(shouldRestoreLockedBrowserPager(AppTab.BARS, true, 0, 0, 1))
    }

    @Test fun barsBackKeepsTabLockedAfterWebViewWasReleased() {
        val lock = effectiveBrowserNavigationLock(
            AppTab.BARS,
            mailWebVisible = false,
            barsWebVisible = false,
            recentlyClosedBrowser = AppTab.BARS,
        )
        assertEquals(AppTab.BARS, lock)
        // The real log: current=0 target=0 settled=1 immediately after closing.
        assertTrue(shouldRestoreLockedBrowserPager(lock, false, 0, 0, 1))
        // After the restoration BARS is still the intended page.
        assertFalse(shouldRestoreLockedBrowserPager(lock, false, 1, 1, 1))
    }

    @Test fun actualPagerDragClearsPostExitGuard() {
        assertNull(effectiveBrowserNavigationLock(AppTab.BARS, false, false, null))
        assertNull(effectiveBrowserNavigationLock(AppTab.SCHEDULE, false, false, AppTab.BARS))
    }

    @Test fun activeBrowserStillLocksWithoutExitGuard() {
        assertEquals(
            AppTab.BARS,
            effectiveBrowserNavigationLock(AppTab.BARS, false, true, null),
        )
        assertEquals(
            AppTab.MAIL,
            effectiveBrowserNavigationLock(AppTab.MAIL, true, false, null),
        )
    }

    @Test fun programmaticNavigationCanProceedDespiteGuard() {
        val lock = effectiveBrowserNavigationLock(AppTab.BARS, false, false, AppTab.BARS)
        assertFalse(shouldRestoreLockedBrowserPager(lock, true, 0, 0, 1))
    }

    @Test fun closesImeIfItReopensAfterBarsWebViewIsDestroyed() {
        assertTrue(shouldDismissImeAfterBarsExit(AppTab.BARS, AppTab.BARS, false, true))
    }

    @Test fun doesNotSuppressImeDuringActiveBrowserOrOtherDestinations() {
        assertFalse(shouldDismissImeAfterBarsExit(AppTab.BARS, AppTab.BARS, true, true))
        assertFalse(shouldDismissImeAfterBarsExit(AppTab.BARS, AppTab.BARS, false, false))
        assertFalse(shouldDismissImeAfterBarsExit(AppTab.BARS, AppTab.MAIL, false, true))
        assertFalse(shouldDismissImeAfterBarsExit(AppTab.MAIL, AppTab.MAIL, false, true))
        assertFalse(shouldDismissImeAfterBarsExit(null, AppTab.BARS, false, true))
    }

    @Test fun nativePagerRemainsUnlocked() {
        assertFalse(shouldRestoreLockedBrowserPager(null, false, 0, 0, 2))
        assertFalse(shouldRestoreLockedBrowserPager(null, false, 2, 2, 0))
    }
}
