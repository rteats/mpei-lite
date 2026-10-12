package com.rteats.mpeineo.ui

import org.junit.Assert.assertFalse
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

    @Test fun nativePagerRemainsUnlocked() {
        assertFalse(shouldRestoreLockedBrowserPager(null, false, 0, 0, 2))
        assertFalse(shouldRestoreLockedBrowserPager(null, false, 2, 2, 0))
    }
}
