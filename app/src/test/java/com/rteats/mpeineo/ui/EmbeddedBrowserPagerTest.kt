package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddedBrowserPagerTest {
    @Test fun mailBrowserKeepsMailSelected() {
        assertEquals(
            AppTab.MAIL,
            lockedBrowserTab(AppTab.MAIL, mailWebVisible = true, barsWebVisible = false),
        )
    }

    @Test fun barsBrowserKeepsBarsSelected() {
        assertEquals(
            AppTab.BARS,
            lockedBrowserTab(AppTab.BARS, mailWebVisible = false, barsWebVisible = true),
        )
    }

    @Test fun lockDoesNotApplyAfterClosingBrowser() {
        assertNull(lockedBrowserTab(AppTab.MAIL, false, false))
        assertNull(lockedBrowserTab(AppTab.BARS, false, false))
    }

    @Test fun inactiveBrowserMustNotLockAnotherDestination() {
        assertNull(lockedBrowserTab(AppTab.SETTINGS, true, true))
        assertNull(lockedBrowserTab(AppTab.SCHEDULE, true, true))
        assertNull(lockedBrowserTab(AppTab.MAIL, false, true))
        assertNull(lockedBrowserTab(AppTab.BARS, true, false))
    }

    @Test fun mailAndBarsUseTheirOwnLockEvenWhenBothBrowsersExist() {
        assertEquals(AppTab.MAIL, lockedBrowserTab(AppTab.MAIL, true, true))
        assertEquals(AppTab.BARS, lockedBrowserTab(AppTab.BARS, true, true))
    }
}
