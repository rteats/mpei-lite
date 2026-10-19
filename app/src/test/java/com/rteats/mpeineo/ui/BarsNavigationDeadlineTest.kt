package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BarsNavigationDeadlineTest {
    @Test fun connectionWithoutDocumentExpiresAtTwelveSeconds() {
        assertEquals(1L, BarsNavigationDeadline.remainingMs(false, 11_999, 11_999))
        assertEquals(0L, BarsNavigationDeadline.remainingMs(false, 12_000, 12_000))
    }

    @Test fun documentStartAndProgressAllowAdditionalIdleTime() {
        assertEquals(18_000L, BarsNavigationDeadline.remainingMs(true, 8_000, 0))
        assertEquals(12_000L, BarsNavigationDeadline.remainingMs(true, 14_000, 6_000))
        assertEquals(0L, BarsNavigationDeadline.remainingMs(true, 26_000, 18_000))
    }

    @Test fun redirectsCannotKeepRequestAliveForever() {
        assertEquals(1L, BarsNavigationDeadline.remainingMs(true, 44_999, 0))
        assertEquals(0L, BarsNavigationDeadline.remainingMs(true, 45_000, 0))
        assertEquals(-1L, BarsNavigationDeadline.remainingMs(true, 45_001, 0))
    }
}
