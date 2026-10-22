package com.rteats.mpeineo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * UI-observable state for the embedded BARS browser's extraction flow.
 * Authentication and source data remain owned by BarsViewModel.
 * Keep this state with the single, long-lived WebView across Home/service navigation.
 */
internal class BarsWebFlowState {
    var redirectingToMarks by mutableStateOf(false)
    var marksExtractionStarted by mutableStateOf(false)
    var summaryFetchAttempted by mutableStateOf(false)
    var summaryExtractionStarted by mutableStateOf(false)
    var officialMainUrl by mutableStateOf<String?>(null)
    var summaryRouteDiscoveryGeneration by mutableStateOf(0)
    var needsFullViewport by mutableStateOf(true)
    var lastPageStateSignature by mutableStateOf<String?>(null)
    var mainFrameFailed by mutableStateOf(false)
    var manuallyRequestedConnection by mutableStateOf(false)
    var browserWasVisible by mutableStateOf(false)

    /** Restore the same browser flags before a normal mark refresh. */
    fun prepareMarkRefresh() {
        redirectingToMarks = false
        summaryFetchAttempted = false
        summaryExtractionStarted = false
        mainFrameFailed = false
        needsFullViewport = true
    }

    /** A retry of the login/initial readiness check must leave marks untouched. */
    fun prepareCheckRetry() {
        mainFrameFailed = false
        summaryFetchAttempted = false
        summaryExtractionStarted = false
        needsFullViewport = true
    }

    /**
     * Browser navigation has priority over background Main -> Summary capture.
     * Invalidating the generation makes outstanding asynchronous DOM discovery
     * and its delayed retries no-ops even if they report after the user enters.
     *
     * @return true if a Summary capture was underway and needs to be cancelled
     * in the state exposed to the user.
     */
    fun enterInteractiveBrowser(): Boolean {
        val interruptedSummary = summaryFetchAttempted
        summaryRouteDiscoveryGeneration++
        summaryFetchAttempted = false
        summaryExtractionStarted = false
        redirectingToMarks = false
        needsFullViewport = true
        return interruptedSummary
    }

    /** A late Summary JS result is invalid after browser takeover. */
    fun canAcceptSummaryPayload(browserVisible: Boolean): Boolean =
        summaryFetchAttempted && !browserVisible

    /** A discovery result may navigate only in its original document/mode. */
    fun canNavigateToSummary(
        generation: Int,
        browserVisible: Boolean,
        marksDocumentVisible: Boolean,
    ): Boolean = summaryFetchAttempted &&
        !browserVisible && marksDocumentVisible &&
        generation == summaryRouteDiscoveryGeneration
}
