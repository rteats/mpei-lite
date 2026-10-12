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
