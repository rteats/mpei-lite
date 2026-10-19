package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleEntrySoftCacheTest {
    @Test fun reusesSamePalette() {
        val cache = SingleEntrySoftCache<String, Any>()
        val first = Any()
        val initial = cache.getOrCreate("dark") { first }
        assertFalse(initial.second)
        val reused = cache.getOrCreate("dark") { error("duplicate bitmap allocation") }
        assertTrue(reused.second)
        assertSame(first, reused.first)
    }

    @Test fun themeChangeInvalidatesPriorRaster() {
        val cache = SingleEntrySoftCache<String, Int>()
        cache.getOrCreate("light") { 1 }
        assertEquals(null, cache.peek("dark"))
        val newRaster = cache.getOrCreate("dark") { 2 }
        assertFalse(newRaster.second)
        assertEquals(2, newRaster.first)
        assertEquals(null, cache.peek("light"))
    }

    @Test fun trimDropsCachedRasterButNotCallersReferences() {
        val cache = SingleEntrySoftCache<String, Any>()
        val bitmap = Any()
        cache.getOrCreate("light") { bitmap }
        cache.clear()
        assertEquals(null, cache.peek("light"))
        assertSame(bitmap, cache.getOrCreate("light") { bitmap }.first)
    }
}
