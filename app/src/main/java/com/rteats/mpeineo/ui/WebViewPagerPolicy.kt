package com.rteats.mpeineo.ui

/**
 * A WebView has to remain on the page selected before the IME opened.
 * Transient pager remeasurement and HTML bring-into-view requests are not
 * navigation intent. Only explicit navbar/edge actions may switch tabs.
 */
internal fun shouldRestoreLockedBrowserPager(
    lockedTab: AppTab?,
    explicitNavigation: Boolean,
    currentPage: Int,
    targetPage: Int,
    settledPage: Int,
): Boolean =
    lockedTab != null && !explicitNavigation &&
        (currentPage != lockedTab.ordinal ||
            targetPage != lockedTab.ordinal ||
            settledPage != lockedTab.ordinal)

/**
 * Closing a focused AndroidView may trigger a pager bring-into-view scroll
 * AFTER the browser state becomes false. Hold the last tab as the navigation
 * owner until an explicit navbar action or a real pager drag begins.
 */
internal fun effectiveBrowserNavigationLock(
    intended: AppTab,
    mailWebVisible: Boolean,
    barsWebVisible: Boolean,
    recentlyClosedBrowser: AppTab?,
): AppTab? =
    lockedBrowserTab(intended, mailWebVisible, barsWebVisible)
        ?: recentlyClosedBrowser?.takeIf { it == intended }
