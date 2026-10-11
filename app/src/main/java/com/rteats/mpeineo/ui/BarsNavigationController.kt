package com.rteats.mpeineo.ui

import android.os.SystemClock
import android.webkit.WebView
import java.net.URI

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

    val requestId: Int get() = attempt?.id ?: generation

    fun belongsTo(other: WebView): Boolean = view === other

    fun load(target: String, reason: String, fallback: String? = null, isRetry: Boolean = false) {
        if (closed) return
        if (!isRetry) retryCount = 0
        cancelCallbacks()
        val next = Attempt(++generation, target, fallback)
        attempt = next
        lastLoadStartedAt = SystemClock.elapsedRealtime()
        lastProgressBucket = -1
        log("load requested id=" + next.id + " reason=" + reason +
            " path=" + safeLocation(target) + " priorPath=" + safeLocation(view.url.orEmpty()) +
            " progress=" + view.progress)
        view.stopLoading()
        view.loadUrl(target)

        val task = Runnable {
            if (closed || attempt !== next) return@Runnable
            log("load watchdog fired id=" + next.id + " path=" + safeLocation(target) +
                " started=" + next.started +
                " elapsedMs=" + (SystemClock.elapsedRealtime() - lastLoadStartedAt) +
                " currentPath=" + safeLocation(view.url.orEmpty()) +
                " progress=" + view.progress + " lastProgressBucket=" + lastProgressBucket)
            probe("watchdog-id-" + next.id)
            view.stopLoading()
            if (fallback != null) {
                load(fallback, "watchdog fallback")
            } else {
                attempt = null
                onTimeout()
            }
        }
        watchdog = task
        view.postDelayed(task, 12_000L)
    }

    fun onStarted(url: String) {
        if (BarsDocumentPolicy.isTrusted(url)) attempt?.started = true
        lastLoadStartedAt = SystemClock.elapsedRealtime()
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
        lastProgressBucket = bucket
        return true
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
