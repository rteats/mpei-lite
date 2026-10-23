package com.rteats.mpeineo.ui

/**
 * Navigation policy for the four stable top-level services.
 *
 * This policy has no dependency on Compose or on a screen lifecycle.
 * A deliberate cross-service link may remember one origin. Ordinary tab
 * selection clears that origin and never creates a history of tab visits.
 */
internal data class FourTabRoute(
    val selected: AppTab = AppTab.SCHEDULE,
    val linkOrigin: AppTab? = null,
) {
    init {
        require(selected != AppTab.SETTINGS) { "Settings is not a primary tab" }
        require(linkOrigin != AppTab.SETTINGS) { "Settings cannot be a tab origin" }
    }

    fun selectTab(tab: AppTab): FourTabRoute {
        require(tab != AppTab.SETTINGS) { "Settings is not a primary tab" }
        return copy(selected = tab, linkOrigin = null)
    }

    fun followLink(tab: AppTab): FourTabRoute {
        require(tab != AppTab.SETTINGS) { "Settings is not a primary tab" }
        return if (tab == selected) this else FourTabRoute(tab, selected)
    }

    /** Null means that system Back can exit from Schedule root. */
    fun backDestination(): AppTab? =
        linkOrigin ?: selected.takeUnless { it == AppTab.SCHEDULE }?.let { AppTab.SCHEDULE }

    fun back(): FourTabRoute? =
        backDestination()?.let { FourTabRoute(selected = it) }

    companion object {
        fun restore(savedTab: String?): FourTabRoute =
            FourTabRoute(
                selected = AppTab.entries.firstOrNull {
                    it.name == savedTab && it != AppTab.SETTINGS
                } ?: AppTab.SCHEDULE,
            )
    }
}

/** One small, common vocabulary; feature-specific animation state remains local. */
internal enum class AppMotionRole {
    TAB_SWITCH,
    DETAIL_PUSH,
    DETAIL_POP,
    SEARCH_EXPAND,
    SEARCH_COLLAPSE,
    LOCAL_CONTENT,
    DESTINATION_FOCUS,
    GESTURE_FEEDBACK,
    NONE,
}

internal object AppMotionTiming {
    const val TAB_SWITCH_MS = 200
    const val DETAIL_PUSH_MS = 290
    const val DETAIL_POP_MS = 220
    const val SEARCH_EXPAND_MS = 300
    const val LOCAL_CONTENT_MS = 160
}

/** Root tab changes always fade BARS in place; only a linked BARS page slides. */
internal fun barsLayerTranslationX(
    selected: AppTab?,
    nested: Boolean,
    reveal: Float,
    viewportWidth: Float,
): Float = if (selected == AppTab.BARS && nested) {
    viewportWidth * (1f - reveal.coerceIn(0f, 1f))
} else {
    0f
}

/** Park the retained WebView off-screen only once its exit fade is fully invisible. */
internal fun barsHiddenOffsetX(
    isOnScene: Boolean,
    opacity: Float,
    viewportWidth: Float,
): Float = if (!isOnScene && opacity <= 0f) viewportWidth else 0f
