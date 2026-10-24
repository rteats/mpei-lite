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
internal fun shouldComposePrimaryPage(page: Int, current: Int, target: Int): Boolean =
    page == primaryPageFor(AppTab.BARS) ||
        abs(page - current) <= 1 || abs(page - target) <= 1

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
