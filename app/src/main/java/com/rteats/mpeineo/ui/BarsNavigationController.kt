package com.rteats.mpeineo.ui

import android.os.SystemClock
import android.webkit.WebView
import java.net.URI

/**
 * The hard limit prevents redirects or noisy progress callbacks from extending
 * a genuinely stalled load indefinitely. The idle limit respects actual
 * document starts and forward progress.
 */
internal object BarsNavigationDeadline {
    const val CONNECT_IDLE_MS = 12_000L
    const val DOCUMENT_IDLE_MS = 18_000L
    const val HARD_LIMIT_MS = 45_000L

    fun remainingMs(started: Boolean, elapsedTotalMs: Long, elapsedIdleMs: Long): Long =
        minOf(
            HARD_LIMIT_MS - elapsedTotalMs,
            (if (started) DOCUMENT_IDLE_MS else CONNECT_IDLE_MS) - elapsedIdleMs,
        )
}

/**
 * Single owner for BARS navigation watchdogs and delayed retries.
 * Replacing a request, or destroying its WebView, cancels outstanding callbacks.
 */
internal class BarsNavigationController(
    private val view: WebView,
    private val log: (String) -> Unit,
    private val probe: (String) -> Unit,
    private val onTimeout: () -> Unit,
) {
    private data class Attempt(
        val id: Int,
        val target: String,
        val fallback: String?,
        var started: Boolean = false,
        val requestedAtMs: Long = SystemClock.elapsedRealtime(),
        var lastActivityAtMs: Long = requestedAtMs,
    )

    private var generation = 0
    private var attempt: Attempt? = null
    private var watchdog: Runnable? = null
    private var delayedRetry: Runnable? = null
    private var closed = false

    var lastLoadStartedAt: Long = SystemClock.elapsedRealtime()
        private set
    var lastProgressBucket: Int = -1
        private set
    var retryCount: Int = 0
        private set

    // Prevent foreground auto-refresh from interrupting a newly-created WebView.
    var hasCompletedDocument: Boolean = false
        private set

    val requestId: Int get() = attempt?.id ?: generation

    fun belongsTo(other: WebView): Boolean = view === other

    fun load(target: String, reason: String, fallback: String? = null, isRetry: Boolean = false) {
        if (closed) return
        if (!isRetry) retryCount = 0
        cancelCallbacks()
        val next = Attempt(++generation, target, fallback)
        attempt = next
        lastLoadStartedAt = next.requestedAtMs
        lastProgressBucket = -1
        log("load requested id=" + next.id + " reason=" + reason +
            " path=" + safeLocation(target) + " priorPath=" + safeLocation(view.url.orEmpty()) +
            " progress=" + view.progress)
        view.stopLoading()
        view.loadUrl(target)

        scheduleWatchdog(next)
    }

    fun onStarted(url: String) {
        if (!BarsDocumentPolicy.isTrusted(url)) return
        val now = SystemClock.elapsedRealtime()
        // Interactive BARS navigation may occur without a tracked request;
        // still record a correct start timestamp for page-finished logs.
        lastLoadStartedAt = now
        val current = attempt ?: return
        current.started = true
        current.lastActivityAtMs = now
        lastProgressBucket = -1
        scheduleWatchdog(current)
    }

    /**
     * WebView callbacks have no navigation ID. A finish must at least match
     * the currently displayed page and have an observed start.
     */
    fun onFinished(url: String): Boolean {
        if (!BarsDocumentPolicy.isCurrent(url, view.url.orEmpty())) return false
        val current = attempt
        if (current != null) {
            if (!current.started) return false
            attempt = null
            cancelCallbacks()
        }
        retryCount = 0
        hasCompletedDocument = true
        return true
    }

    /** True when a bounded main-frame retry has been scheduled. */
    fun onMainFrameFailure(url: String, retryable: Boolean): Boolean {
        val current = attempt ?: return false
        cancelWatchdog()
        if (!retryable || !BarsDocumentPolicy.isTrusted(url) || retryCount >= 1) {
            attempt = null
            return false
        }
        retryCount++
        val task = Runnable {
            if (!closed && attempt === current) {
                log("retry fired target=" + safeLocation(url))
                load(url, "main-frame retry", isRetry = true)
            }
        }
        delayedRetry = task
        view.postDelayed(task, 1_500L)
        return true
    }

    fun recordProgress(bucket: Int): Boolean {
        if (lastProgressBucket == bucket) return false
        val previous = lastProgressBucket
        lastProgressBucket = bucket
        // A 100 -> 0 WebView reset is not forward progress and must not
        // repeatedly extend a stalled navigation.
        val current = attempt
        if (current != null && current.started && bucket > previous && bucket >= 25) {
            current.lastActivityAtMs = SystemClock.elapsedRealtime()
            scheduleWatchdog(current)
        }
        return true
    }

    private fun scheduleWatchdog(current: Attempt) {
        cancelWatchdog()
        if (closed || attempt !== current) return
        val now = SystemClock.elapsedRealtime()
        val left = BarsNavigationDeadline.remainingMs(
            current.started, now - current.requestedAtMs, now - current.lastActivityAtMs,
        )
        val task = Runnable {
            if (closed || attempt !== current) return@Runnable
            val checkedAt = SystemClock.elapsedRealtime()
            val total = checkedAt - current.requestedAtMs
            val idle = checkedAt - current.lastActivityAtMs
            // A stale scheduled callback must never preempt fresh progress.
            if (BarsNavigationDeadline.remainingMs(current.started, total, idle) > 0L) {
                scheduleWatchdog(current)
                return@Runnable
            }
            log("load watchdog fired id=" + current.id +
                " path=" + safeLocation(current.target) +
                " stage=" + (if (current.started) "document" else "connect") +
                " idleMs=" + idle + " totalMs=" + total +
                " limit=" + (if (total >= BarsNavigationDeadline.HARD_LIMIT_MS)
                    "hard" else "idle") +
                " currentPath=" + safeLocation(view.url.orEmpty()) +
                " progress=" + view.progress + " lastProgressBucket=" + lastProgressBucket)
            probe("watchdog-id-" + current.id)
            view.stopLoading()
            if (current.fallback != null) {
                load(current.fallback, "watchdog fallback")
            } else {
                attempt = null
                onTimeout()
            }
        }
        watchdog = task
        view.postDelayed(task, left.coerceAtLeast(1L))
    }

    fun close() {
        closed = true
        generation++
        attempt = null
        cancelCallbacks()
    }

    private fun cancelWatchdog() {
        watchdog?.let(view::removeCallbacks)
        watchdog = null
    }

    private fun cancelCallbacks() {
        cancelWatchdog()
        delayedRetry?.let(view::removeCallbacks)
        delayedRetry = null
    }

    private fun safeLocation(url: String): String =
        runCatching {
            val uri = URI(url)
            uri.host.orEmpty() + uri.path.orEmpty()
        }.getOrDefault("unparseable")
}
