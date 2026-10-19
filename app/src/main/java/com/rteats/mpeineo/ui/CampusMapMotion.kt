package com.rteats.mpeineo.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.PI
import kotlin.math.abs

private const val MIN_MAP_FLING_SPEED = 160f // px/s
private const val MAX_MAP_FLING_SPEED = 3600f // px/s
private const val FLING_RELEASE_WINDOW_MS = 100L

/** Ignore a slow release, a pause before lifting, or an invalid velocity. */
internal fun campusFlingVelocity(velocity: Offset, timeSinceLastMoveMs: Long): Offset {
    val speed = velocity.getDistance()
    if (!speed.isFinite() || speed < MIN_MAP_FLING_SPEED ||
        timeSinceLastMoveMs > FLING_RELEASE_WINDOW_MS
    ) return Offset.Zero
    return velocity * (MAX_MAP_FLING_SPEED / speed).coerceAtMost(1f)
}

/**
 * A transform detector with release velocity. It keeps Compose's touch-slop,
 * centroid-anchored pinch and event consumption semantics, but only flings
 * after a one-finger pan. A pinch never starts a fling on release.
 */
internal suspend fun PointerInputScope.detectCampusGestures(
    onDown: () -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onFling: (velocity: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onDown()

        val tracker = VelocityTracker().apply {
            addPosition(down.uptimeMillis, down.position)
        }
        var accumulatedPan = Offset.Zero
        var accumulatedZoom = 1f
        var accumulatedRotation = 0f
        var pastTouchSlop = false
        var usedMultipleFingers = false
        var panned = false
        var canceled = false
        var lastMoveTime = down.uptimeMillis
        var lastEventTime = down.uptimeMillis

        do {
            val event = awaitPointerEvent()
            lastEventTime = event.changes.maxOf { it.uptimeMillis }
            // Give priority to the native back gesture or other recognizers
            // if they consumed movement before the map took ownership.
            if (event.changes.any { it.isConsumed }) {
                canceled = true
                break
            }
            if (event.changes.count { it.pressed || it.previousPressed } > 1) {
                usedMultipleFingers = true
            }
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()

            if (!pastTouchSlop) {
                accumulatedPan += panChange
                accumulatedZoom *= zoomChange
                accumulatedRotation += event.calculateRotation()
                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotion = abs(1f - accumulatedZoom) * centroidSize
                val rotationMotion =
                    abs(accumulatedRotation * PI.toFloat() * centroidSize / 180f)
                pastTouchSlop = zoomMotion > viewConfiguration.touchSlop ||
                    rotationMotion > viewConfiguration.touchSlop ||
                    accumulatedPan.getDistance() > viewConfiguration.touchSlop
            }

            if (pastTouchSlop) {
                if (panChange != Offset.Zero || zoomChange != 1f) {
                    onTransform(
                        event.calculateCentroid(useCurrent = false),
                        panChange,
                        zoomChange,
                    )
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }

                if (!usedMultipleFingers) {
                    val finger = event.changes.singleOrNull {
                        it.id == down.id && it.pressed && it.previousPressed
                    }
                    if (finger != null) {
                        tracker.addPosition(finger.uptimeMillis, finger.position)
                        if (panChange != Offset.Zero) {
                            panned = true
                            lastMoveTime = finger.uptimeMillis
                        }
                    }
                }
            }
        } while (event.changes.any { it.pressed })

        if (!canceled && pastTouchSlop && panned && !usedMultipleFingers) {
            val trackedVelocity = tracker.calculateVelocity()
            val velocity = campusFlingVelocity(
                Offset(trackedVelocity.x, trackedVelocity.y),
                lastEventTime - lastMoveTime,
            )
            if (velocity != Offset.Zero) onFling(velocity)
        }
    }
}
