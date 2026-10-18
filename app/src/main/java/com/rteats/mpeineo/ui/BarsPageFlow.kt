package com.rteats.mpeineo.ui

/** Pure BARS page classification; do not infer authentication from cookies alone. */
internal enum class BarsPageKind {
    MARKS_READY,
    AUTH_FLOW,
    MARKS_LOADING,
    OTHER_BARS_PAGE,
    UNTRUSTED,
}

internal fun classifyBarsPage(page: BarsPageState): BarsPageKind {
    if (!BarsDocumentPolicy.isTrusted(page.url)) return BarsPageKind.UNTRUSTED
    return when {
        page.isMarksPage -> BarsPageKind.MARKS_READY
        page.isLoginPage || page.isAuthFlow || page.isStudentList -> BarsPageKind.AUTH_FLOW
        BarsDocumentPolicy.isCurrent(page.url, BARS_MARKS_URL) -> BarsPageKind.MARKS_LOADING
        else -> BarsPageKind.OTHER_BARS_PAGE
    }
}

/**
 * Reading grades is safe in an interactive WebView: it does not navigate or
 * alter BARS, unlike the separate automated Summary capture.
 *
 * A completed extraction is scoped to the current document. The WebView
 * lifecycle resets [alreadyStarted] when a new page starts.
 */
internal fun shouldExtractBarsMarks(
    page: BarsPageState,
    alreadyStarted: Boolean,
): Boolean = !alreadyStarted && classifyBarsPage(page) == BarsPageKind.MARKS_READY

/**
 * One readiness timeout per document, not repeated JS polling counters.
 * The first missing-table deadline retries via the official BARS root; if
 * the next marks document still has no table, the second deadline reports an
 * error. All pending callbacks are removed when the document changes.
 *
 * Scheduler injection keeps the transitions JVM-testable without a WebView.
 */
internal class BarsMarksReadiness(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val onRecover: () -> Unit,
    private val onFailure: () -> Unit,
) {
    private var pending: Runnable? = null
    private var recoveryAttempted = false
    private var failed = false
    private var disposed = false
    private var generation = 0L

    /**
     * @return true if this call started a new wait; repeated page observations
     * do not extend its deadline or schedule duplicate recovery.
     */
    fun waitForMarks(): Boolean {
        if (disposed || failed || pending != null) return false
        val ticket = ++generation
        val task = Runnable {
            if (disposed || generation != ticket) return@Runnable
            pending = null
            if (!recoveryAttempted) {
                recoveryAttempted = true
                onRecover()
            } else {
                failed = true
                onFailure()
            }
        }
        pending = task
        schedule(task, if (recoveryAttempted) 10_000L else 5_000L)
        return true
    }

    fun navigationStarted() {
        clearPending()
    }

    fun reset() {
        clearPending()
        recoveryAttempted = false
        failed = false
    }

    fun dispose() {
        disposed = true
        clearPending()
    }

    private fun clearPending() {
        generation++
        pending?.let(cancel)
        pending = null
    }
}
