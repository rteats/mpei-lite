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
}
