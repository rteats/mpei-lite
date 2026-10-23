package com.rteats.mpeineo.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Shared timing and threshold for native service and nested browser Back. */
internal const val BACK_SLIDE_OUT_MS = AppMotionTiming.DETAIL_POP_MS
internal const val BACK_SLIDE_IN_MS = AppMotionTiming.DETAIL_PUSH_MS
internal const val BACK_SWIPE_COMMIT_FRACTION = 0.20f

/** Pure progress policy; gesture and button exits share the same finish animation. */
internal fun backSwipeProgress(current: Float, deltaPx: Float, viewportPx: Float): Float =
    if (viewportPx <= 0f) current
    else (current + deltaPx / viewportPx).coerceIn(0f, 1f)

internal fun shouldCompleteBackSwipe(progress: Float): Boolean =
    progress >= BACK_SWIPE_COMMIT_FRACTION

/**
 * Tracks a page's rightward exit without releasing its WebView until the slide
 * finishes. The reverse animation restores a cancelled gesture smoothly.
 */
internal class BackSlideState(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set
    private var animation: Job? = null
    private var generation = 0
    private var leaving = false

    fun drag(deltaPx: Float, viewportPx: Float) {
        if (leaving) return
        generation += 1
        animation?.cancel()
        animation = null
        progress = backSwipeProgress(progress, deltaPx, viewportPx)
    }

    private fun animateProgress(target: Float, duration: Int, after: () -> Unit = {}) {
        val current = ++generation
        animation?.cancel()
        animation = scope.launch {
            try {
                animate(
                    initialValue = progress,
                    targetValue = target,
                    animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                ) { value, _ ->
                    if (generation == current) progress = value
                }
                if (generation == current) {
                    progress = target
                    after()
                }
            } finally {
                if (generation == current) animation = null
            }
        }
    }

    fun cancel() {
        if (!leaving) animateProgress(0f, BACK_SLIDE_OUT_MS)
    }

    fun release(onExit: () -> Unit) {
        if (shouldCompleteBackSwipe(progress)) exit(onExit) else cancel()
    }

    fun exit(onExit: () -> Unit) {
        if (leaving) return
        leaving = true
        animateProgress(1f, BACK_SLIDE_OUT_MS, onExit)
    }

    /** Only used for a new browser overlay over the already visible native UI. */
    fun enter() {
        // Opening another message/browser cancels any pending close callback
        // instead of letting that older exit close the newly opened page.
        leaving = false
        generation += 1
        animation?.cancel()
        animation = null
        progress = 1f
        animateProgress(0f, BACK_SLIDE_IN_MS)
    }

    fun reset() {
        generation += 1
        animation?.cancel()
        animation = null
        leaving = false
        progress = 0f
    }
}

@Composable
internal fun rememberBackSlideState(): BackSlideState {
    val scope = rememberCoroutineScope()
    return remember(scope) { BackSlideState(scope) }
}
