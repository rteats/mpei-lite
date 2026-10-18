package com.rteats.mpeineo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiFrameStatsTest {
    @Test fun reportsPercentilesAndBudgetOverrunsPerScreen() {
        val stats = UiFrameStats(frameBudgetNs = 16_000_000L)
        stats.record("MAPS", 10_000_000L)
        stats.record("MAPS", 20_000_000L, callbackDrops = 2)
        stats.record("MAPS", 30_000_000L)
        stats.record("BARS", 15_000_000L)

        val map = stats.summaries().first { it.screen == "MAPS" }
        assertEquals(3, map.frames)
        assertEquals(20.0, map.p50Ms, 0.0001)
        assertEquals(30.0, map.p95Ms, 0.0001)
        assertEquals(30.0, map.maxMs, 0.0001)
        assertEquals(2, map.overBudget)
        assertEquals(2, map.callbackDrops)

        val bars = stats.summaries().first { it.screen == "BARS" }
        assertEquals(1, bars.frames)
        assertEquals(15.0, bars.p95Ms, 0.0001)
        assertEquals(0, bars.overBudget)
    }

    @Test fun invalidFrameDurationsAreNotCounted() {
        val stats = UiFrameStats(frameBudgetNs = 16_666_667L)
        stats.record("MAIL", -1L)
        stats.record("MAIL", 0L)
        assertTrue(stats.summaries().isEmpty())
        stats.record("MAIL", 17_000_000L, callbackDrops = -2)
        assertEquals(1, stats.summaries().single().overBudget)
        assertEquals(0, stats.summaries().single().callbackDrops)
    }

    @Test fun repeatedSnapshotIsStable() {
        val stats = UiFrameStats(frameBudgetNs = 16_000_000L)
        listOf(8L, 12L, 14L, 15L, 16L, 17L, 20L, 32L).forEach {
            stats.record("SCHEDULE", it * 1_000_000L)
        }
        val first = stats.summaries().single()
        val second = stats.summaries().single()
        assertEquals(first, second)
        assertEquals(3, first.overBudget)
        assertEquals(15.0, first.p50Ms, 0.0001)
        assertEquals(32.0, first.p95Ms, 0.0001)
    }
}
