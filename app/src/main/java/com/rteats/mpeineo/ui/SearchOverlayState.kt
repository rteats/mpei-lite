package com.rteats.mpeineo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Contextual search state; no intermediate animation or geometry state. */
internal class SearchOverlayState(
    initiallyOpen: Boolean = false,
    initialQuery: String = "",
    initialScope: SearchScope = SearchScope.SCHEDULE,
) {
    var isOpen by mutableStateOf(initiallyOpen)
        private set
    var query by mutableStateOf(initialQuery)
        private set
    var scope by mutableStateOf(initialScope)
        private set

    fun activate(nextScope: SearchScope): Boolean {
        if (isOpen) return false
        scope = nextScope
        query = ""
        isOpen = true
        return true
    }

    fun updateQuery(value: String) { query = value }

    fun close() {
        isOpen = false
        query = ""
    }
}

private val searchOverlaySaver = listSaver<SearchOverlayState, String>(
    save = { listOf(if (it.isOpen) "true" else "false", it.query, it.scope.name) },
    restore = {
        SearchOverlayState(
            initiallyOpen = it.getOrNull(0) == "true",
            initialQuery = it.getOrNull(1).orEmpty(),
            initialScope = it.getOrNull(2)
                ?.let { name -> SearchScope.entries.firstOrNull { scope -> scope.name == name } }
                ?: SearchScope.SCHEDULE,
        )
    },
)

@Composable
internal fun rememberSearchOverlayState(): SearchOverlayState =
    rememberSaveable(saver = searchOverlaySaver) { SearchOverlayState() }
