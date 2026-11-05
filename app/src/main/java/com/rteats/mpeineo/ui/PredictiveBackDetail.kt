package com.rteats.mpeineo.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Detail page driven by the system back gesture.
 *
 * The page follows the predictive-back progress, exposing the already-mounted
 * parent list. A committed gesture completes the slide with a spatial spring
 * before invoking [onBack]; a cancelled one springs back to the origin. Back
 * without a gesture (button or three-button navigation) calls [onBack] at once
 * and leaves the exit animation to the surrounding overlay.
 *
 * This is the only back handler for the detail: do not also register a
 * BackHandler for the same condition in a parent, because parent handlers
 * registered after this one take priority and would hide the gesture.
 * Do not use on WebView or composer: both own their own navigation/input.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PredictiveBackDetail(
    onBack: () -> Unit,
    onGestureCommit: () -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val latestBack by rememberUpdatedState(onBack)
    val latestGestureCommit by rememberUpdatedState(onGestureCommit)
    val scope = rememberCoroutineScope()
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    var widthPx by remember { mutableIntStateOf(0) }
    var direction by remember { mutableFloatStateOf(1f) }
    val slide = remember { Animatable(0f) }

    // A reopened detail always starts at rest, even if this composable was kept.
    LaunchedEffect(enabled) { if (enabled) slide.snapTo(0f) }

    PredictiveBackHandler(enabled = enabled) { progress ->
        var moved = false
        try {
            progress.collect { event ->
                moved = true
                direction = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                slide.snapTo(event.progress)
            }
            if (moved) {
                slide.animateTo(1f, spring)
                latestGestureCommit()
            }
            latestBack()
        } catch (cancelled: CancellationException) {
            scope.launch { slide.animateTo(0f, spring) }
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
            .onSizeChanged { widthPx = it.width }
            .offset { IntOffset((direction * slide.value * widthPx).roundToInt(), 0) },
    ) { content() }
}
