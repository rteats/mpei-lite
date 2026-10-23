package com.rteats.mpeineo.ui

/** Four primary destinations share one root. Only explicit links and Settings nest. */
internal fun serviceRouteAfterOpen(
    current: List<String>,
    target: AppTab,
    viaClassroom: Boolean,
): ArrayList<String> {
    val active = current.lastOrNull()?.let { runCatching { AppTab.valueOf(it) }.getOrNull() }
        ?: AppTab.SCHEDULE
    val previousTab = if (active == AppTab.SETTINGS) {
        current.dropLast(1).lastOrNull()?.let { runCatching { AppTab.valueOf(it) }.getOrNull() }
            ?: AppTab.SCHEDULE
    } else active
    return when {
        target == AppTab.SETTINGS -> arrayListOf(previousTab.name, AppTab.SETTINGS.name)
        viaClassroom && target != previousTab ->
            arrayListOf(previousTab.name, target.name)
        else -> arrayListOf(target.name)
    }
}

internal fun serviceRouteAfterBack(current: List<String>): ArrayList<String> {
    if (current.size > 1) return ArrayList(current.dropLast(1))
    return arrayListOf(AppTab.SCHEDULE.name)
}

internal enum class AppTab { SCHEDULE, BARS, MAIL, MAPS, SETTINGS }

/** Destination used by AppBar actions at the beginning of a back transition. */
internal fun searchAppBarDestinationAfterBack(routes: List<String>): AppTab? =
    serviceRouteAfterBack(routes).lastOrNull()?.let(AppTab::valueOf)

/** Preserve the existing pager-policy unit tests while using stack navigation. */
internal fun lockedBrowserTab(
    intended: AppTab,
    mailWebVisible: Boolean,
    barsWebVisible: Boolean,
): AppTab? = when (intended) {
    AppTab.MAIL -> AppTab.MAIL.takeIf { mailWebVisible }
    AppTab.BARS -> AppTab.BARS.takeIf { barsWebVisible }
    else -> null
}

internal fun scheduleDockRevealProgress(
    currentPage: Int,
    pageOffsetFraction: Float,
): Float = (1f - kotlin.math.abs(currentPage + pageOffsetFraction)).coerceIn(0f, 1f)

/** Complete an intentional rightward swipe, otherwise spring the page back. */
internal fun shouldCommitServiceBackSwipe(distancePx: Float, widthPx: Float): Boolean =
    widthPx > 0f && distancePx >= widthPx * BACK_SWIPE_COMMIT_FRACTION

/**
 * The BARS detail is a nested destination, just like a Mail message:
 * its rightward swipe should invoke its own BackHandler, never switch
 * primary tabs. Browser, IME and transitions block gestures.
 */
internal enum class BarsSwipeTarget { DISABLED, DISCIPLINE_OVERVIEW }

internal fun barsSwipeTarget(
    foreground: Boolean,
    isTransitioning: Boolean,
    imeVisible: Boolean,
    browserVisible: Boolean,
    authenticated: Boolean,
    detailOpen: Boolean,
): BarsSwipeTarget {
    if (!foreground || isTransitioning || imeVisible || browserVisible || !authenticated) {
        return BarsSwipeTarget.DISABLED
    }
    return if (detailOpen) BarsSwipeTarget.DISCIPLINE_OVERVIEW else BarsSwipeTarget.DISABLED
}

/** Map-only back target is narrow; regular service pages retain full-width swipes. */
internal fun acceptsServiceBackStart(
    x: Float,
    width: Float,
    systemEdge: Float,
    mapEdgeZone: Boolean,
): Boolean = x >= systemEdge && (mapEdgeZone || x <= width - systemEdge)

/** Derive all visible route destinations from one route snapshot. */
internal data class ServiceRouteSnapshot(val names: List<String>) {
    val current: AppTab? get() = names.lastOrNull()?.let(AppTab::valueOf)
    val beneath: AppTab? get() = names.dropLast(1).lastOrNull()?.let(AppTab::valueOf)
    fun open(target: AppTab, viaClassroom: Boolean = false): ArrayList<String> =
        serviceRouteAfterOpen(names, target, viaClassroom)
    fun back(): ArrayList<String> = serviceRouteAfterBack(names)
}

