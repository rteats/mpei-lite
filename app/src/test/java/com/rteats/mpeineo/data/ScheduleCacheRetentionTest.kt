package com.rteats.mpeineo.data

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCacheRetentionTest {
    private val monday = LocalDate.of(2026, 10, 12)

    @Test fun keepsRecentAndUpcomingWeeks() {
        assertFalse(shouldPruneScheduleCacheWeek("group-123-2026-10-12.json", monday))
        assertFalse(shouldPruneScheduleCacheWeek("teacher-4-2026-07-20.json", monday))
        assertFalse(shouldPruneScheduleCacheWeek("classroom-7-2027-04-12.json", monday))
    }

    @Test fun prunesOnlyWeeksOutsideTheRetentionWindow() {
        assertTrue(shouldPruneScheduleCacheWeek("group-123-2026-07-13.json", monday))
        assertTrue(shouldPruneScheduleCacheWeek("group-123-2027-04-19.json", monday))
    }

    @Test fun ignoresUnknownFilesAndCleansMalformedDates() {
        assertFalse(shouldPruneScheduleCacheWeek("notes.json", monday))
        assertFalse(shouldPruneScheduleCacheWeek("group-123-2026-09-28.json.tmp", monday))
        assertTrue(shouldPruneScheduleCacheWeek("group-123-2026-99-99.json", monday))
    }
}
