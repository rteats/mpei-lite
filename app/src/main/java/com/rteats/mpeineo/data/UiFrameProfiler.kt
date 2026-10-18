package com.rteats.mpeineo.data

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.ceil
import java.util.Locale

/** A finite, opt-in Window-level frame sample, not a continuous diagnostic. */
internal data class UiFrameSummary(
    val screen: String,
    val frames: Int,
    val p50Ms: Double,
    val p95Ms: Double,
    val maxMs: Double,
    val overBudget: Int,
    val callbackDrops: Int,
)

internal class UiFrameStats(private val frameBudgetNs: Long) {
    private class Bucket {
        val durations = ArrayList<Long>()
        var overBudget = 0
        var callbackDrops = 0
    }

    private val buckets = linkedMapOf<String, Bucket>()

    @Synchronized
    fun record(screen: String, durationNs: Long, callbackDrops: Int = 0) {
        if (durationNs <= 0L) return
        val bucket = buckets.getOrPut(screen) { Bucket() }
        // 60 seconds at 120 Hz is ~7,200 frames. Cap accidental longer runs.
        if (bucket.durations.size >= 10_000) return
        bucket.durations.add(durationNs)
        if (durationNs > frameBudgetNs) bucket.overBudget++
        bucket.callbackDrops += callbackDrops.coerceAtLeast(0)
    }

    @Synchronized
    fun summaries(): List<UiFrameSummary> = buckets.map { (screen, bucket) ->
        val sorted = bucket.durations.sorted()
        fun percentile(fraction: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val index = (ceil(sorted.size * fraction).toInt() - 1)
                .coerceIn(0, sorted.lastIndex)
            return sorted[index] / 1_000_000.0
        }
        UiFrameSummary(
            screen = screen,
            frames = sorted.size,
            p50Ms = percentile(.50),
            p95Ms = percentile(.95),
            maxMs = percentile(1.0),
            overBudget = bucket.overBudget,
            callbackDrops = bucket.callbackDrops,
        )
    }
}

/**
 * Captures Android Window frame times only on explicit user request. Frame times
 * can flag suspect interactions but do not diagnose Compose/WebView internals.
 */
class UiFrameProfiler(private val diagnostics: DiagnosticLog) {
    var active by mutableStateOf(false)
        private set

    @Volatile private var screen = "HOME"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var measuredWindow: Window? = null
    private var timeout: Runnable? = null
    private var stats: UiFrameStats? = null
    private var startedMs = 0L

    /** Called on the main thread; labels are fixed and contain no student data. */
    fun markScreen(route: String) {
        val next = route.takeIf {
            it in setOf("HOME", "SCHEDULE", "BARS", "MAIL", "MAPS", "SETTINGS", "SEARCH")
        } ?: return
        if (screen != next) {
            screen = next
            if (active) diagnostics.log("PERF", "frame_profile phase=" + next)
        }
    }

    /** Main thread only. No listener or handler thread exists while disabled. */
    fun start(window: Window, durationMs: Long = 60_000L): Boolean {
        if (active) return false
        val refreshRate = window.decorView.display?.refreshRate
            ?.takeIf { it in 30f..240f } ?: 60f
        val budgetNs = (1_000_000_000.0 / refreshRate).toLong()
        val collector = UiFrameStats(budgetNs)
        val handlerThread = HandlerThread("NeoFrameProfile").apply { start() }
        val callback = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            collector.record(screen, metrics.getMetric(FrameMetrics.TOTAL_DURATION), dropped)
        }
        try {
            window.addOnFrameMetricsAvailableListener(callback, Handler(handlerThread.looper))
        } catch (_: Exception) {
            handlerThread.quitSafely()
            diagnostics.log("PERF", "frame_profile unsupported")
            return false
        }
        stats = collector
        listener = callback
        thread = handlerThread
        measuredWindow = window
        startedMs = SystemClock.elapsedRealtime()
        active = true
        val stopTask = Runnable { stop("timeout") }
        timeout = stopTask
        mainHandler.postDelayed(stopTask, durationMs)
        diagnostics.log(
            "PERF",
            "frame_profile started durationMs=" + durationMs +
                " refreshHz=" + refreshRate.toInt() +
                " budgetMs=" + (budgetNs / 1_000_000.0).formatMs(),
        )
        return true
    }

    fun stop(reason: String = "manual") {
        if (!active) return
        active = false
        timeout?.let(mainHandler::removeCallbacks)
        timeout = null
        val elapsed = SystemClock.elapsedRealtime() - startedMs
        listener?.let { callback ->
            runCatching { measuredWindow?.removeOnFrameMetricsAvailableListener(callback) }
        }
        listener = null
        measuredWindow = null
        thread?.quitSafely()
        thread = null
        val summaries = stats?.summaries().orEmpty()
        stats = null
        diagnostics.log("PERF", "frame_profile ended reason=" + reason + " elapsedMs=" + elapsed)
        if (summaries.isEmpty()) {
            diagnostics.log("PERF", "frame_profile no_frames")
        } else {
            summaries.forEach { sample ->
                diagnostics.log("PERF",
                    "frame_profile screen=" + sample.screen + " frames=" + sample.frames +
                        " p50Ms=" + sample.p50Ms.formatMs() +
                        " p95Ms=" + sample.p95Ms.formatMs() +
                        " maxMs=" + sample.maxMs.formatMs() +
                        " overBudget=" + sample.overBudget +
                        " callbackDrops=" + sample.callbackDrops)
            }
        }
    }

    private fun Double.formatMs(): String = "%.1f".format(Locale.US, this)
}
