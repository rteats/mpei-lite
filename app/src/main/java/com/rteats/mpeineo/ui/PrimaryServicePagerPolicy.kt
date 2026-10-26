package com.rteats.mpeineo.ui

import kotlin.math.abs

/** Fixed physical order of four root pages. Settings is not a page. */
internal val PRIMARY_TABS =
    listOf(AppTab.SCHEDULE, AppTab.BARS, AppTab.MAIL, AppTab.MAPS)

internal fun primaryPageFor(tab: AppTab?): Int =
    PRIMARY_TABS.indexOf(tab).takeIf { it >= 0 } ?: 0

/**
 * Keep four cheap Pager slots laid out, but mount only nearby feature UI.
 * BARS must stay composed: its one authenticated WebView holds the 2FA session.
 */
internal fun shouldComposePrimaryPage(
    page: Int,
    current: Int,
    target: Int,
    isBarsPage: Boolean = page == primaryPageFor(AppTab.BARS),
): Boolean =
    isBarsPage || abs(page - current) <= 1 || abs(page - target) <= 1

/**
 * A disabled or interrupted pager cannot display a route other than the one
 * selected in the navigation bar. A pending real user swipe is allowed to
 * settle only while the root pager owns touch input.
 */
internal fun pageToRestoreForRoute(
    route: AppTab,
    visiblePage: Int,
    offsetFraction: Float,
    pendingUserSwipe: Boolean,
    swipeEnabled: Boolean,
    programmatic: Boolean,
): Int? {
    if (programmatic || (pendingUserSwipe && swipeEnabled)) return null
    val expected = primaryPageFor(route)
    return expected.takeIf { visiblePage != it || offsetFraction != 0f }
}

/** Embedded browsers, nested details, Settings and IME own touch gestures. */
internal fun canSwipePrimaryPages(
    atRoot: Boolean,
    searchOpen: Boolean,
    imeVisible: Boolean,
    mailBrowser: Boolean,
    barsBrowser: Boolean,
    mailDetail: Boolean,
    barsDetail: Boolean,
): Boolean = atRoot && !searchOpen && !imeVisible &&
    !mailBrowser && !barsBrowser && !mailDetail && !barsDetail
