package com.rteats.mpeineo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Presentation-only state for the persistent embedded browser.
 * BarsSessionCoordinator owns all extraction and navigation decisions.
 * Keep this state with the single, long-lived WebView across Home/service navigation.
 */
internal class BarsWebFlowState {
    var officialMainUrl by mutableStateOf<String?>(null)
    var needsFullViewport by mutableStateOf(true)
    var lastPageStateSignature by mutableStateOf<String?>(null)
    var mainFrameFailed by mutableStateOf(false)
    var manuallyRequestedConnection by mutableStateOf(false)
    var browserWasVisible by mutableStateOf(false)

    /** Restore the same browser flags before a normal mark refresh. */
    fun prepareMarkRefresh() {
        mainFrameFailed = false
        needsFullViewport = true
    }

    /** A retry of the login/initial readiness check must leave marks untouched. */
    fun prepareCheckRetry() {
        mainFrameFailed = false
        needsFullViewport = true
    }

}
