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
 * A distant tab tap uses a temporary visual order. The chosen destination is
 * placed next to the source, so one full-width spring carries the content even
 * for Schedule -> Campus. The canonical order returns after settling.
 *
 * This is display order only: real routes, the swipe order, and service keys
 * never change. The same BARS key keeps its authenticated WebView alive.
 */
internal fun adjacentProgrammaticPages(from: AppTab, to: AppTab): List<AppTab> {
    val start = PRIMARY_TABS.indexOf(from)
    val end = PRIMARY_TABS.indexOf(to)
    if (start < 0 || end < 0 || abs(end - start) <= 1) return PRIMARY_TABS
    val adjacent = start + if (end > start) 1 else -1
    return PRIMARY_TABS.toMutableList().apply {
        this[end] = this[adjacent]
        this[adjacent] = to
    }
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
