package com.rteats.mpeineo.ui

/** Four primary destinations share one root. Only explicit links and Settings nest. */
internal fun serviceRouteAfterOpen(
    current: List<String>,
    target: AppTab,
    viaClassroom: Boolean,
): ArrayList<String> {
    val active = current.lastOrNull()?.let { runCatching { AppTab.valueOf(it) }.getOrNull() }
        ?: AppTab.HOME
    val previousTab = if (active == AppTab.SETTINGS) {
        current.dropLast(1).lastOrNull()?.let { runCatching { AppTab.valueOf(it) }.getOrNull() }
            ?: AppTab.HOME
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
    return arrayListOf(AppTab.HOME.name)
}

internal enum class AppTab { HOME, SCHEDULE, BARS, MAIL, MAPS, SETTINGS }

/** Destination used by AppBar actions at the beginning of a back transition. */
internal fun searchAppBarDestinationAfterBack(routes: List<String>): AppTab? =
    serviceRouteAfterBack(routes).lastOrNull()?.let(AppTab::valueOf)

/** Derive all visible route destinations from one route snapshot. */
internal data class ServiceRouteSnapshot(val names: List<String>) {
    val current: AppTab? get() = names.lastOrNull()?.let(AppTab::valueOf)
    val beneath: AppTab? get() = names.dropLast(1).lastOrNull()?.let(AppTab::valueOf)
    fun open(target: AppTab, viaClassroom: Boolean = false): ArrayList<String> =
        serviceRouteAfterOpen(names, target, viaClassroom)
    fun back(): ArrayList<String> = serviceRouteAfterBack(names)
}

