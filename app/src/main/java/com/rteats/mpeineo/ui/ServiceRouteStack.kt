package com.rteats.mpeineo.ui

/**
 * Service routes are kept as stable enum names for rememberSaveable.
 * The only nested stack is Schedule → Maps when a classroom is selected.
 */
internal fun serviceRouteAfterOpen(
    current: List<String>,
    target: AppTab,
    viaClassroom: Boolean,
): ArrayList<String> = if (
    viaClassroom && current.lastOrNull() == AppTab.SCHEDULE.name &&
    target == AppTab.MAPS
) {
    arrayListOf(AppTab.SCHEDULE.name, AppTab.MAPS.name)
} else {
    arrayListOf(target.name)
}

internal fun serviceRouteAfterBack(current: List<String>): ArrayList<String> =
    ArrayList(current.dropLast(1))
