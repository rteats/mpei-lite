package com.rteats.mpeineo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * Search owns its activation, query, scope and source bounds. The shell owns
 * only the animation that presents it and routes selected results.
 *
 * Source geometry is transient (not Bundle-saveable); reopening after activity
 * recreation falls back to the currently measured entry field.
 */
internal class SearchOverlayState(
    initiallyOpen: Boolean = false,
    initialQuery: String = "",
    initialScope: SearchScope = SearchScope.SCHEDULE,
) {
    var isOpen by mutableStateOf(initiallyOpen)
        private set
    var overlayMounted by mutableStateOf(initiallyOpen)
        private set
    var query by mutableStateOf(initialQuery)
        private set
    var scope by mutableStateOf(initialScope)
        private set

    var entryBounds by mutableStateOf(Rect.Zero)
    var activatedBounds by mutableStateOf(Rect.Zero)

    fun activate(nextScope: SearchScope): Boolean {
        if (isOpen) return false
        scope = nextScope
        activatedBounds = entryBounds
        overlayMounted = true
        isOpen = true
        query = ""
        return true
    }

    fun updateQuery(value: String) {
        query = value
    }

    fun close() {
        isOpen = false
    }

    /** Keep results mounted throughout the reverse reveal. */
    fun onRevealSettled(progress: Float) {
        if (progress == 0f && !isOpen) {
            overlayMounted = false
            query = ""
        }
    }

    fun anchoredBounds(): Rect =
        activatedBounds.takeIf { it.width > 0f } ?: entryBounds
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
