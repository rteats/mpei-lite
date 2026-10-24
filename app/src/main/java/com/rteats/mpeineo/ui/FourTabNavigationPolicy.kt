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
