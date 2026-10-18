package com.rteats.mpeineo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsAccountCacheScopeTest {
    @Test fun stableAcrossWhitespaceAndCase() {
        val first = barsSnapshotAccountKey("  Test Student ", " A-12 ")
        val second = barsSnapshotAccountKey("test student", "a-12")
        assertEquals(first, second)
        assertEquals(64, first?.length)
    }

    @Test fun differentStudentsAndGroupsDoNotShareRawCacheIdentity() {
        val base = barsSnapshotAccountKey("Student A", "A-12")
        assertNotEquals(base, barsSnapshotAccountKey("Student B", "A-12"))
        assertNotEquals(base, barsSnapshotAccountKey("Student A", "B-12"))
        assertFalse(base.orEmpty().contains("Student A"))
    }

    @Test fun missingIdentityDoesNotEnableRawCacheReuse() {
        assertNull(barsSnapshotAccountKey("", "  "))
        assertTrue(barsSnapshotAccountKey("Student", "") != null)
    }
}
