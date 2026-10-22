package com.rteats.mpeineo.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
) {
    Box(
        modifier = modifier.width(80.dp).fillMaxHeight()
            // Avoid the page header and the fixed browser toolbar.
            .padding(top = 112.dp, bottom = 112.dp)
            .testTag("embedded-browser-exit-zone")
            .pointerInput(onExit) {
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
                            if (delta > 0f) change.consume()
                        }
                    },
                    onDragEnd = {
                        if (shouldExitEmbeddedBrowserSwipe(
                                startedInsideZone, distancePx, exitThresholdPx,
                            )
                        ) onExit()
                        startedInsideZone = false
                    },
                    onDragCancel = { startedInsideZone = false },
                )
            },
    )
}
