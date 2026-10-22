package com.rteats.mpeineo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsSessionCoordinatorTest {
    @Test fun browserTakeoverInvalidatesDiscoveryAndSummaryCallbacks() {
        val coordinator = BarsSessionCoordinator()
        val generation = coordinator.startSummaryDiscovery()
        assertTrue(coordinator.canNavigateToSummary(generation, false, true))
        assertTrue(coordinator.canAcceptSummaryPayload(false))
        assertFalse(coordinator.canNavigateToSummary(generation, true, true))
        assertFalse(coordinator.canNavigateToSummary(generation, false, false))

        assertTrue(coordinator.enterInteractiveBrowser())
        assertFalse(coordinator.summaryFetchAttempted)
        assertFalse(coordinator.canAcceptSummaryPayload(false))
        assertFalse(coordinator.canNavigateToSummary(generation, false, true))

        val newer = coordinator.startSummaryDiscovery()
        assertTrue(coordinator.canNavigateToSummary(newer, false, true))
        assertFalse(coordinator.canNavigateToSummary(generation, false, true))
    }

    @Test fun summaryExtractionIsOncePerCaptureAndRetriesAreExplicit() {
        val coordinator = BarsSessionCoordinator()
        assertFalse(coordinator.beginSummaryExtraction(true))
        coordinator.startSummaryDiscovery()
        assertFalse(coordinator.beginSummaryExtraction(false))
        assertTrue(coordinator.beginSummaryExtraction(true))
        assertFalse(coordinator.beginSummaryExtraction(true))
        coordinator.retrySummaryExtraction()
        assertTrue(coordinator.beginSummaryExtraction(true))
        coordinator.prepareCheckRetry()
        assertFalse(coordinator.beginSummaryExtraction(true))
    }

    @Test fun marksAreExtractedOncePerDocumentIncludingLoginBrowser() {
        val coordinator = BarsSessionCoordinator()
        val page = BarsPageState(url = BARS_MARKS_URL, isMarksPage = true)
        assertTrue(coordinator.beginMarksExtraction(page))
        assertFalse(coordinator.beginMarksExtraction(page))
        coordinator.onDocumentStarted()
        assertTrue(coordinator.beginMarksExtraction(page))
    }

    @Test fun redirectToMarksIsIdempotentUntilRouteObserved() {
        val coordinator = BarsSessionCoordinator()
        assertTrue(coordinator.beginRedirectToMarks())
        assertFalse(coordinator.beginRedirectToMarks())
        coordinator.onMarksRouteObserved()
        assertTrue(coordinator.beginRedirectToMarks())
        coordinator.prepareMarkRefresh()
        assertTrue(coordinator.beginRedirectToMarks())
    }

    @Test fun refreshCancelsOldSummaryButDoesNotPreventNewCapture() {
        val coordinator = BarsSessionCoordinator()
        val original = coordinator.startSummaryDiscovery()
        coordinator.prepareMarkRefresh()
        assertFalse(coordinator.canNavigateToSummary(original, false, true))
        assertFalse(coordinator.enterInteractiveBrowser())
        assertTrue(coordinator.canNavigateToSummary(
            coordinator.startSummaryDiscovery(), false, true))
    }
}
