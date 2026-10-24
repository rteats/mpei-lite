package com.rteats.mpeineo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Horizontal detail-page gesture, independent from the four-service pager.
 *
 * A horizontal drag exposes the already-mounted parent list. Vertical drags
 * belong to the detail's scrollable content. On release, a qualifying right
 * swipe completes to the right with a spatial spring before invoking Back;
 * an incomplete swipe springs to the origin.
 *
 * Do not use on WebView or composer: both own their own navigation/input.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SwipeBackDetail(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val latestBack by rememberUpdatedState(onBack)
    val scope = rememberCoroutineScope()
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val threshold = with(LocalDensity.current) { 92.dp.toPx() }
    var widthPx by remember { mutableIntStateOf(0) }
    var offsetPx by remember { mutableFloatStateOf(0f) }
    var settling by remember { androidx.compose.runtime.mutableStateOf(false) }
    var settleJob by remember { androidx.compose.runtime.mutableStateOf<Job?>(null) }

    Box(
        modifier = modifier.fillMaxSize()
            .onSizeChanged { widthPx = it.width }
            .offset { IntOffset(offsetPx.roundToInt(), 0) }
            .pointerInput(widthPx, threshold) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        settleJob?.cancel()
                        settling = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!settling) {
                            offsetPx = (offsetPx + dragAmount)
                                .coerceIn(0f, widthPx.toFloat())
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (!settling) {
                            val finish = offsetPx >= threshold
                            settling = true
                            settleJob = scope.launch {
                                val motion = Animatable(offsetPx)
                                motion.animateTo(
                                    targetValue = if (finish) widthPx.toFloat() else 0f,
                                    animationSpec = spring,
                                ) { offsetPx = value }
                                if (finish) latestBack() else settling = false
                            }
                        }
                    },
                    onDragCancel = {
                        settleJob?.cancel()
                        settling = true
                        settleJob = scope.launch {
                            val motion = Animatable(offsetPx)
                            motion.animateTo(0f, animationSpec = spring) {
                                offsetPx = value
                            }
                            settling = false
                        }
                    },
                )
            },
    ) { content() }
}
