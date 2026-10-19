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

    @Test fun frameStagesReportIndependentPercentiles() {
        val stats = UiFrameStats(16_666_667L)
        stats.record("SCHEDULE/LESSON_TOGGLE", 35_000_000L,
            inputNs = 2_000_000L, layoutNs = 15_000_000L,
            drawNs = 8_000_000L, gpuNs = 4_000_000L)
        stats.record("SCHEDULE/LESSON_TOGGLE", 50_000_000L,
            inputNs = 3_000_000L, layoutNs = 25_000_000L,
            drawNs = 12_000_000L, gpuNs = 7_000_000L)
        val sample = stats.summaries().single()
        assertEquals("SCHEDULE/LESSON_TOGGLE", sample.screen)
        assertEquals(25.0, sample.layoutP95Ms, 0.001)
        assertEquals(12.0, sample.drawP95Ms, 0.001)
        assertEquals(7.0, sample.gpuP95Ms, 0.001)
        assertEquals(3.0, sample.inputP95Ms, 0.001)
        assertEquals(2, sample.inputSamples)
        assertEquals(2, sample.layoutSamples)
        assertEquals(2, sample.drawSamples)
        assertEquals(2, sample.gpuSamples)
    }

    @Test fun renderThreadAndUnknownDelayStagesStaySeparateFromDraw() {
        val stats = UiFrameStats(16_666_667L)
        stats.record("MAIL/MAIL_OPEN", 41_000_000L,
            drawNs = 5_000_000L, syncNs = 12_000_000L,
            swapNs = 7_000_000L, unknownNs = 9_000_000L)
        val sample = stats.summaries().single()
        assertEquals(5.0, sample.drawP95Ms, 0.001)
        assertEquals(12.0, sample.syncP95Ms, 0.001)
        assertEquals(7.0, sample.swapP95Ms, 0.001)
        assertEquals(9.0, sample.unknownP95Ms, 0.001)
        assertEquals(0, sample.gpuSamples)
    }

    @Test fun unsupportedStageMetricsRemainZero() {
        val stats = UiFrameStats(16_666_667L)
        stats.record("MAPS", 20_000_000L, inputNs = -1L)
        val sample = stats.summaries().single()
        assertEquals(0.0, sample.inputP95Ms, 0.001)
        assertEquals(0.0, sample.gpuP95Ms, 0.001)
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
