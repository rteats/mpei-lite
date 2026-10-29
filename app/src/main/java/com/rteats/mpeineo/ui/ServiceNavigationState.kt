package com.rteats.mpeineo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Single owner of the service back stack. The shell animates a navigation,
 * but never edits the stack, predicts its parent or manufactures route names.
 *
 * Stored entries are stable enum names so Android can save/restore them.
 */
internal class ServiceNavigationState(initialRoutes: List<String> = listOf(AppTab.HOME.name)) {
    var routes: List<String> by mutableStateOf(
        initialRoutes.filter { name -> AppTab.entries.any { it.name == name } }
            .takeLast(2)
            .let { valid ->
                when {
                    valid.isEmpty() -> listOf(AppTab.HOME.name)
                    valid.size == 1 && valid[0] == AppTab.SETTINGS.name ->
                        listOf(AppTab.HOME.name, AppTab.SETTINGS.name)
                    else -> valid
                }
            },
    )
        private set

    val current: AppTab?
        get() = ServiceRouteSnapshot(routes).current
    val beneath: AppTab?
        get() = ServiceRouteSnapshot(routes).beneath
    val canPop: Boolean get() = current != AppTab.HOME || routes.size > 1

    fun open(tab: AppTab, viaClassroom: Boolean = false) {
        routes = serviceRouteAfterOpen(routes, tab, viaClassroom)
    }

    fun peekBack(): AppTab? = ServiceRouteSnapshot(routes).back()
        .lastOrNull()?.let(AppTab::valueOf)

    fun pop(): Boolean {
        if (!canPop) return false
        routes = serviceRouteAfterBack(routes)
        return true
    }
}

private val serviceNavigationSaver = listSaver<ServiceNavigationState, String>(
    save = { it.routes.toList() },
    restore = { ServiceNavigationState(it) },
)

@Composable
internal fun rememberServiceNavigationState(
    initialMailTab: Boolean,
    restoredRoutes: List<String> = emptyList(),
): ServiceNavigationState =
    rememberSaveable(saver = serviceNavigationSaver) {
        ServiceNavigationState(
            when {
                initialMailTab -> listOf(AppTab.MAIL.name)
                restoredRoutes.isNotEmpty() -> restoredRoutes.filter { name ->
                    AppTab.entries.any { it.name == name }
                }.let { restored ->
                    val last = restored.lastOrNull() ?: AppTab.HOME.name
                    if (last == AppTab.SETTINGS.name && restored.size == 1)
                        listOf(AppTab.HOME.name, last)
                    else if (restored.size > 1) restored.takeLast(2)
                    else listOf(last)
                }
                else -> listOf(AppTab.HOME.name)
            },
        )
    }
