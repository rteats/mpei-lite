package com.rteats.mpeineo.ui

import android.content.Context

/** Stable IDs, not translated labels, are persisted across app updates. */
internal enum class HomeTileSize { COMPACT, REGULAR, EXPANDED }

internal data class HomeTile(
    val tab: AppTab,
    val size: HomeTileSize = HomeTileSize.REGULAR,
)

internal object HomeTileLayout {
    private val tabs = listOf(AppTab.SCHEDULE, AppTab.BARS, AppTab.MAIL, AppTab.MAPS)
    val defaults: List<HomeTile> get() = tabs.map { HomeTile(it) }

    fun decode(raw: String?): List<HomeTile> {
        val tiles = raw.orEmpty().split(',').mapNotNull { record ->
            val parts = record.trim().split(':')
            if (parts.size != 2) return@mapNotNull null
            val tab = tabs.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
            val size = HomeTileSize.entries.firstOrNull { it.name == parts[1] }
                ?: HomeTileSize.REGULAR
            HomeTile(tab, size)
        }.distinctBy { it.tab }
        // Always retain all four services, including when a saved layout predates
        // a new button or contains corrupt/unknown values.
        return tiles + defaults.filter { default -> tiles.none { it.tab == default.tab } }
    }

    fun encode(layout: List<HomeTile>): String =
        normalize(layout).joinToString(",") { "${it.tab.name}:${it.size.name}" }

    fun normalize(layout: List<HomeTile>): List<HomeTile> =
        layout.filter { it.tab in tabs }.distinctBy { it.tab }.let { known ->
            known + defaults.filter { default -> known.none { it.tab == default.tab } }
        }

    fun resize(layout: List<HomeTile>, tab: AppTab, size: HomeTileSize): List<HomeTile> =
        normalize(layout).map { if (it.tab == tab) it.copy(size = size) else it }

    fun move(layout: List<HomeTile>, tab: AppTab, delta: Int): List<HomeTile> {
        val result = normalize(layout).toMutableList()
        val from = result.indexOfFirst { it.tab == tab }
        val to = from + delta
        if (from < 0 || to !in result.indices) return result
        val tile = result.removeAt(from)
        result.add(to, tile)
        return result
    }
}

internal class HomeLayoutPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("home_layout", Context.MODE_PRIVATE)
    fun read(): List<HomeTile> = HomeTileLayout.decode(preferences.getString("tiles", null))
    fun write(layout: List<HomeTile>) {
        preferences.edit().putString("tiles", HomeTileLayout.encode(layout)).apply()
    }
    fun reset() { preferences.edit().remove("tiles").apply() }
}
