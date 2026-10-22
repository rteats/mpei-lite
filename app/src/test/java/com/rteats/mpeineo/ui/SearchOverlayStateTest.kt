package com.rteats.mpeineo.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchOverlayStateTest {
    @Test fun activationFreezesSourceAndChoosesCurrentServiceScope() {
        val search = SearchOverlayState()
        val original = Rect(4f, 8f, 180f, 55f)
        search.entryBounds = original
        assertTrue(search.activate(SearchScope.MAIL))
        search.entryBounds = Rect(20f, 20f, 90f, 60f)
        assertEquals(original, search.anchoredBounds())
        assertEquals(SearchScope.MAIL, search.scope)
        assertFalse(search.activate(SearchScope.BARS))
    }

    @Test fun reverseAnimationKeepsResultsUntilSettled() {
        val search = SearchOverlayState()
        search.activate(SearchScope.BARS)
        search.updateQuery("Физика")
        search.close()
        assertTrue(search.overlayMounted)
        assertEquals("Физика", search.query)
        search.onRevealSettled(0.5f)
        assertTrue(search.overlayMounted)
        search.onRevealSettled(0f)
        assertFalse(search.overlayMounted)
        assertEquals("", search.query)
    }

    @Test fun restoredSearchFallsBackToNewLayoutGeometry() {
        val restored = SearchOverlayState(
            initiallyOpen = true,
            initialQuery = "Электротехника",
            initialScope = SearchScope.SCHEDULE,
        )
        restored.entryBounds = Rect(10f, 10f, 200f, 65f)
        assertEquals(restored.entryBounds, restored.anchoredBounds())
        assertTrue(restored.isOpen)
    }
}
