package com.rteats.mpeineo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsExtractionLifecycleTest {
    @Test fun rejectsCallbacksFromPreviousNavigation() {
        val gate = BarsExtractionGate()
        val first = gate.begin()
        gate.invalidate()
        val second = gate.begin()
        assertFalse(gate.consume(first))
        assertTrue(gate.consume(second))
        assertFalse(gate.consume(second))
    }

    @Test fun repeatedExtractionInSamePageCannotOverwriteNewerResult() {
        val gate = BarsExtractionGate()
        val initial = gate.begin()
        val newest = gate.begin()
        assertFalse(gate.consume(initial))
        assertTrue(gate.consume(newest))
    }

    @Test fun autoRefreshRequiresStaleCachedGradesAndCompletedSession() {
        val gate = BarsAutoRefreshGate()
        fun refresh(
            now: Long = 100_000L,
            ready: Boolean = true,
            grades: Boolean = true,
            fresh: Boolean = false,
            loading: Boolean = false,
            browser: Boolean = false,
        ) = gate.shouldRefresh(now, ready, grades, fresh, loading, browser)

        assertFalse(refresh(ready = false))
        assertFalse(refresh(grades = false))
        assertFalse(refresh(fresh = true))
        assertFalse(refresh(loading = true))
        assertFalse(refresh(browser = true))
        assertTrue(refresh())
        assertFalse(refresh(now = 110_000L))
        assertTrue(refresh(now = 160_000L))
    }

    @Test fun failedRefreshDoesNotCauseRapidRetryLoop() {
        val gate = BarsAutoRefreshGate()
        assertTrue(gate.shouldRefresh(10_000L, true, true, false, false, false))
        assertFalse(gate.shouldRefresh(10_001L, true, true, false, false, false))
        assertFalse(gate.shouldRefresh(69_999L, true, true, false, false, false))
        assertTrue(gate.shouldRefresh(70_000L, true, true, false, false, false))
    }
}
