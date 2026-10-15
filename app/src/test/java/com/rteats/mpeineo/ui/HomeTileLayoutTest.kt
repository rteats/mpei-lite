package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTileLayoutTest {
    @Test fun emptyLayoutPreservesExistingLauncher() {
        val loaded = HomeTileLayout.decode(null)
        assertEquals(listOf(AppTab.SCHEDULE, AppTab.BARS, AppTab.MAIL, AppTab.MAPS),
            loaded.map { it.tab })
        assertTrue(loaded.all { it.size == HomeTileSize.REGULAR })
    }

    @Test fun customOrderAndSizeRoundTrip() {
        val customized = HomeTileLayout.move(
            HomeTileLayout.resize(HomeTileLayout.defaults, AppTab.MAPS, HomeTileSize.EXPANDED),
            AppTab.MAPS, -3,
        )
        assertEquals(AppTab.MAPS, customized.first().tab)
        assertEquals(HomeTileSize.EXPANDED, customized.first().size)
        assertEquals(customized, HomeTileLayout.decode(HomeTileLayout.encode(customized)))
    }

    @Test fun corruptDuplicateOrUnknownEntriesCannotHideServices() {
        val decoded = HomeTileLayout.decode(
            "MAPS:COMPACT,MAPS:EXPANDED,SETTINGS:COMPACT,BARS:INVALID,garbage")
        assertEquals(4, decoded.size)
        assertEquals(AppTab.MAPS, decoded.first().tab)
        assertEquals(HomeTileSize.COMPACT, decoded.first().size)
        assertEquals(HomeTileSize.REGULAR, decoded.first { it.tab == AppTab.BARS }.size)
    }

    @Test fun moveRespectsBoundsAndKeepsSizes() {
        val tiles = HomeTileLayout.resize(HomeTileLayout.defaults, AppTab.MAIL, HomeTileSize.COMPACT)
        assertEquals(tiles, HomeTileLayout.move(tiles, AppTab.SCHEDULE, -1))
        val moved = HomeTileLayout.move(tiles, AppTab.MAIL, -1)
        assertEquals(AppTab.MAIL, moved[1].tab)
        assertEquals(HomeTileSize.COMPACT, moved[1].size)
    }
}
