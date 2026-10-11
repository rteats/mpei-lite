package com.rteats.mpeineo.ui

/**
 * Each script execution receives its own ID. An obsolete JavaScript callback
 * cannot replace grades from a newer navigation or manual refresh.
 */
internal class BarsExtractionGate {
    private var generation = 0
    private var pendingId: Int? = null

    fun begin(): Int = (++generation).also { pendingId = it }

    fun invalidate() {
        generation++
        pendingId = null
    }

    fun consume(id: Int): Boolean {
        if (pendingId != id) return false
        pendingId = null
        return true
    }
}

/**
 * Refresh stale grades on returning to BARS, but do not repeatedly retry a
 * failing server on rapid tab switches. Never interfere with an open browser,
 * initial session verification, or an in-flight request.
 */
internal class BarsAutoRefreshGate(private val minimumIntervalMs: Long = 60_000L) {
    private var lastAttemptElapsedMs: Long? = null

    fun shouldRefresh(
        nowElapsedMs: Long,
        sessionReady: Boolean,
        hasCachedGrades: Boolean,
        cacheFresh: Boolean,
        isLoading: Boolean,
        browserVisible: Boolean,
    ): Boolean {
        if (!sessionReady || !hasCachedGrades || cacheFresh || isLoading || browserVisible) {
            return false
        }
        val previous = lastAttemptElapsedMs
        if (previous != null && nowElapsedMs - previous in 0 until minimumIntervalMs) {
            return false
        }
        lastAttemptElapsedMs = nowElapsedMs
        return true
    }
}
