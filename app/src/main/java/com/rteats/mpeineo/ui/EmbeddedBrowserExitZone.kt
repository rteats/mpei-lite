package com.rteats.mpeineo.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * A deliberately narrow, transparent edge zone over an Android WebView.
 * Native WebViews claim touch streams before a Compose ancestor can
 * reliably detect them. Only this reserved left strip intercepts gestures.
 *
 * Unlike browser Back, this exits the embedded portal in one gesture,
 * without walking through its page history. Leave the toolbar and most of
 * the website completely untouched.
 */
internal fun shouldExitEmbeddedBrowserSwipe(
    startedInsideZone: Boolean,
    distancePx: Float,
    thresholdPx: Float,
): Boolean = startedInsideZone && distancePx >= thresholdPx && thresholdPx > 0f

@Composable
internal fun EmbeddedBrowserExitZone(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onDrag: (Float) -> Unit = {},
    onRelease: (() -> Unit)? = null,
    onCancel: () -> Unit = {},
) {
    // Gesture progress recomposes the parent on every frame. Do not restart
    // pointerInput on each new lambda instance or drag detection is cancelled.
    val currentExit by rememberUpdatedState(onExit)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentCancel by rememberUpdatedState(onCancel)
    Box(
        modifier = modifier.width(80.dp).fillMaxHeight()
            // Avoid the page header and the fixed browser toolbar.
            .padding(top = 112.dp, bottom = 112.dp)
            .testTag("embedded-browser-exit-zone")
            .pointerInput(Unit) {
                var startedInsideZone = false
                var distancePx = 0f
                val systemEdgePx = 20.dp.toPx()
                val exitThresholdPx = 64.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        startedInsideZone = start.x >= systemEdgePx
                        distancePx = 0f
                    },
                    onHorizontalDrag = { change, delta ->
                        if (startedInsideZone) {
                            distancePx = (distancePx + delta).coerceAtLeast(0f)
                            currentDrag(delta)
                            if (delta > 0f) change.consume()
                        }
                    },
                    onDragEnd = {
                        if (startedInsideZone) {
                            if (currentRelease != null) currentRelease?.invoke()
                            else if (shouldExitEmbeddedBrowserSwipe(
                                    startedInsideZone, distancePx, exitThresholdPx,
                                )
                            ) currentExit()
                        }
                        startedInsideZone = false
                    },
                    onDragCancel = {
                        if (startedInsideZone) currentCancel()
                        startedInsideZone = false
                    },
                )
            },
    )
}
