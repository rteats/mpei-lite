package com.rteats.mpeineo.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val predictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

internal data class PredictiveBackSurfaceFrame(
    val scale: Float,
    val translationXPx: Float,
    val cornerRadiusDp: Float,
)

/** Android's full-screen back preview: 90% scale and an 8dp edge gap. */
internal fun predictiveBackSurfaceFrame(
    progress: Float,
    widthPx: Int,
    density: Float,
    direction: Float,
): PredictiveBackSurfaceFrame {
    val eased = predictiveBackEasing.transform(progress.coerceIn(0f, 1f))
    val maxShiftPx = (widthPx * 0.05f - 8f * density).coerceAtLeast(0f)
    return PredictiveBackSurfaceFrame(
        scale = 1f - 0.1f * eased,
        translationXPx = direction * maxShiftPx * eased,
        cornerRadiusDp = 28f * eased,
    )
}

/**
 * Detail page driven by the system back gesture.
 *
 * The page scales and shifts with predictive-back progress, exposing the
 * already-mounted parent. A committed gesture fades the detail away before
 * invoking [onBack]; a cancelled one springs back to full size. Back
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
    val density = LocalDensity.current.density
    var widthPx by remember { mutableIntStateOf(0) }
    var direction by remember { mutableFloatStateOf(1f) }
    val progress = remember { Animatable(0f) }
    val opacity = remember { Animatable(1f) }

    // A reopened detail always starts at rest, even if this composable was kept.
    LaunchedEffect(enabled) {
        if (enabled) {
            progress.snapTo(0f)
            opacity.snapTo(1f)
        }
    }

    PredictiveBackHandler(enabled = enabled) { backEvents ->
        var moved = false
        try {
            backEvents.collect { event ->
                moved = true
                direction = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                progress.snapTo(event.progress)
            }
            if (moved) {
                coroutineScope {
                    launch { progress.animateTo(1f, spring) }
                    launch { opacity.animateTo(0f, spring) }
                }
                latestGestureCommit()
            }
            latestBack()
        } catch (cancelled: CancellationException) {
            scope.launch {
                coroutineScope {
                    launch { progress.animateTo(0f, spring) }
                    launch { opacity.animateTo(1f, spring) }
                }
            }
        }
    }

    val frame = predictiveBackSurfaceFrame(progress.value, widthPx, density, direction)
    Box(
        modifier = modifier.fillMaxSize()
            .onSizeChanged { widthPx = it.width }
            .graphicsLayer {
                scaleX = frame.scale
                scaleY = frame.scale
                translationX = frame.translationXPx
                alpha = opacity.value
                shape = RoundedCornerShape(frame.cornerRadiusDp.dp)
                clip = progress.value > 0f
            },
    ) { content() }
}
