package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchOverlayStateTest {
    @Test fun activationSetsContextWithoutIntermediateMotion() {
        val search = SearchOverlayState()
        assertTrue(search.activate(SearchScope.MAIL))
        assertEquals(SearchScope.MAIL, search.scope)
        assertTrue(search.isOpen)
        assertFalse(search.activate(SearchScope.BARS))
    }

    @Test fun closingClearsResultsImmediatelyAndCanReopenInAnotherService() {
        val search = SearchOverlayState()
        search.activate(SearchScope.BARS)
        search.updateQuery("Физика")
        search.close()
        assertFalse(search.isOpen)
        assertEquals("", search.query)
        assertTrue(search.activate(SearchScope.SCHEDULE))
        assertEquals(SearchScope.SCHEDULE, search.scope)
    }

    @Test fun restoredSearchDoesNotDependOnLayoutCoordinates() {
        val restored = SearchOverlayState(
            initiallyOpen = true,
            initialQuery = "Электротехника",
            initialScope = SearchScope.SCHEDULE,
        )
        assertTrue(restored.isOpen)
        assertEquals("Электротехника", restored.query)
    }
}
