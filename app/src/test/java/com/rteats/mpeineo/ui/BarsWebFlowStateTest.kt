package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsWebFlowStateTest {
    @Test fun markRefreshResetsOnlyTransientExtractionFlags() {
        val state = BarsWebFlowState()
        state.redirectingToMarks = true
        state.summaryFetchAttempted = true
        state.summaryExtractionStarted = true
        state.mainFrameFailed = true
        state.needsFullViewport = false
        state.officialMainUrl = "https://bars.mpei.ru/bars_web/ST_Study/Main/Main"
        state.manuallyRequestedConnection = true

        state.prepareMarkRefresh()

        assertFalse(state.redirectingToMarks)
        assertFalse(state.summaryFetchAttempted)
        assertFalse(state.summaryExtractionStarted)
        assertFalse(state.mainFrameFailed)
        assertTrue(state.needsFullViewport)
        assertTrue(state.manuallyRequestedConnection)
        assertEquals("https://bars.mpei.ru/bars_web/ST_Study/Main/Main",
            state.officialMainUrl)
    }

    @Test fun checkRetryPreservesAuthenticatedPageAndConnectionIntent() {
        val state = BarsWebFlowState()
        state.redirectingToMarks = true
        state.summaryFetchAttempted = true
        state.mainFrameFailed = true
        state.needsFullViewport = false

        state.prepareCheckRetry()

        assertTrue(state.redirectingToMarks)
        assertFalse(state.summaryFetchAttempted)
        assertFalse(state.mainFrameFailed)
        assertTrue(state.needsFullViewport)
    }
    @Test fun openingInteractiveBrowserInvalidatesPendingSummaryDiscovery() {
        val state = BarsWebFlowState()
        state.summaryFetchAttempted = true
        state.summaryExtractionStarted = true
        state.redirectingToMarks = true
        state.needsFullViewport = false
        state.officialMainUrl = "https://bars.mpei.ru/bars_web/ST_Study/Main/Main"
        val generation = ++state.summaryRouteDiscoveryGeneration

        assertTrue(state.canNavigateToSummary(
            generation, browserVisible = false, marksDocumentVisible = true))
        assertFalse(state.canNavigateToSummary(
            generation, browserVisible = true, marksDocumentVisible = true))
        assertFalse(state.canNavigateToSummary(
            generation, browserVisible = false, marksDocumentVisible = false))

        assertTrue(state.enterInteractiveBrowser())
        assertFalse(state.summaryFetchAttempted)
        assertFalse(state.summaryExtractionStarted)
        assertFalse(state.redirectingToMarks)
        assertTrue(state.needsFullViewport)
        assertFalse(state.canNavigateToSummary(
            generation, browserVisible = false, marksDocumentVisible = true))
        assertEquals("https://bars.mpei.ru/bars_web/ST_Study/Main/Main",
            state.officialMainUrl)
    }

    @Test fun lateJavascriptCallbacksCannotReuseCancelledGeneration() {
        val state = BarsWebFlowState()
        state.summaryFetchAttempted = true
        val first = ++state.summaryRouteDiscoveryGeneration
        assertTrue(state.enterInteractiveBrowser())
        // A new automatic capture may start only after returning to native mode.
        state.summaryFetchAttempted = true
        val second = ++state.summaryRouteDiscoveryGeneration
        assertFalse(state.canNavigateToSummary(first, false, true))
        assertTrue(state.canNavigateToSummary(second, false, true))
        assertTrue(state.enterInteractiveBrowser())
    }

}
