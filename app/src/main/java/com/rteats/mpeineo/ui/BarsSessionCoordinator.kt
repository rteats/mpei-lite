package com.rteats.mpeineo.ui

/**
 * Single owner of BARS Main -> Summary extraction decisions.
 *
 * This class has no Compose or Android dependencies: the persistent WebView is
 * an adapter that reports events and performs the requested navigation. Neither
 * the screen nor a delayed JavaScript callback may change an active operation's
 * generation directly.
 */
internal class BarsSessionCoordinator {
    var summaryFetchAttempted: Boolean = false
        private set
    var summaryExtractionStarted: Boolean = false
        private set
    private var marksExtractionStarted = false
    private var redirectingToMarks = false
    private var discoveryGeneration = 0

    /** Restarting a marks operation also invalidates pending Summary discovery. */
    fun prepareMarkRefresh() {
        redirectingToMarks = false
        resetSummaryCapture()
    }

    /** Retrying session readiness cannot leave an automatic report running. */
    fun prepareCheckRetry() = resetSummaryCapture()

    fun resetSummaryCapture() {
        discoveryGeneration++
        summaryFetchAttempted = false
        summaryExtractionStarted = false
    }

    fun startSummaryDiscovery(): Int {
        summaryFetchAttempted = true
        summaryExtractionStarted = false
        return ++discoveryGeneration
    }

    fun canNavigateToSummary(
        generation: Int,
        browserVisible: Boolean,
        marksDocumentVisible: Boolean,
    ): Boolean = summaryFetchAttempted && !browserVisible &&
        marksDocumentVisible && generation == discoveryGeneration

    fun canAcceptSummaryPayload(browserVisible: Boolean): Boolean =
        summaryFetchAttempted && !browserVisible

    /** Only the first ready notification for this capture starts extraction. */
    fun beginSummaryExtraction(ready: Boolean): Boolean {
        if (!ready || !summaryFetchAttempted || summaryExtractionStarted) return false
        summaryExtractionStarted = true
        return true
    }

    fun retrySummaryExtraction() {
        summaryExtractionStarted = false
    }

    /** The user controls all navigation once the official browser is opened. */
    fun enterInteractiveBrowser(): Boolean {
        val interrupted = summaryFetchAttempted
        resetSummaryCapture()
        redirectingToMarks = false
        return interrupted
    }

    fun onDocumentStarted() {
        marksExtractionStarted = false
    }

    fun beginMarksExtraction(page: BarsPageState): Boolean {
        if (!shouldExtractBarsMarks(page, marksExtractionStarted)) return false
        marksExtractionStarted = true
        return true
    }

    fun onMarksRouteObserved() {
        redirectingToMarks = false
    }

    /** One automatic redirect per authenticated non-Main document. */
    fun beginRedirectToMarks(): Boolean {
        if (redirectingToMarks) return false
        redirectingToMarks = true
        return true
    }
}
