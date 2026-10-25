package com.rteats.mpeineo.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class CampusMapGesturesTest {
    private val viewport = IntSize(1000, 1000)
    private val roomyWorld = Rect(-1000f, -1000f, 1000f, 1000f)

    @Test fun pinchKeepsCentroidWorldPointStationary() {
        val result = applyCampusGesture(
            zoom = 1f,
            pan = Offset(50f, 20f),
            zoomChange = 2f,
            panChange = Offset.Zero,
            centroid = Offset(700f, 600f),
            viewport = viewport,
            world = roomyWorld,
        )
        assertEquals(2f, result.zoom, .001f)
        assertEquals(-100f, result.pan.x, .001f)
        assertEquals(-60f, result.pan.y, .001f)
    }

    @Test fun oneFingerDragMovesMapInBothDirections() {
        val result = applyCampusGesture(
            zoom = 2f,
            pan = Offset.Zero,
            zoomChange = 1f,
            panChange = Offset(-80f, 65f),
            centroid = Offset(500f, 500f),
            viewport = viewport,
            world = roomyWorld,
        )
        assertEquals(2f, result.zoom, .001f)
        assertEquals(-80f, result.pan.x, .001f)
        assertEquals(65f, result.pan.y, .001f)
    }

    @Test fun zoomIsClampedToSupportedRange() {
        val enlarged = applyCampusGesture(
            17f, Offset.Zero, 3f, Offset.Zero, Offset(500f, 500f),
            viewport, roomyWorld,
        )
        val shrunk = applyCampusGesture(
            .8f, Offset.Zero, .1f, Offset.Zero, Offset(500f, 500f),
            viewport, roomyWorld,
        )
        assertEquals(18f, enlarged.zoom, .001f)
        assertEquals(.65f, shrunk.zoom, .001f)
    }

    @Test fun releaseVelocityPreservesLastPanDirection() {
        val velocity = campusFlingVelocity(Offset(-1400f, 750f), 20)
        assertEquals(-1400f, velocity.x, .001f)
        assertEquals(750f, velocity.y, .001f)
    }

    @Test fun slowOrStoppedDragDoesNotFling() {
        assertEquals(Offset.Zero, campusFlingVelocity(Offset(80f, 0f), 0))
        assertEquals(Offset.Zero, campusFlingVelocity(Offset(1600f, 0f), 150))
    }

    @Test fun excessivelyFastFlingIsCapped() {
        val velocity = campusFlingVelocity(Offset(9000f, 0f), 15)
        assertEquals(3600f, velocity.x, .001f)
        assertEquals(0f, velocity.y, .001f)
    }

    @Test fun draggingDoesNotExposeEmptySpaceOnSmallMap() {
        val result = applyCampusGesture(
            1f, Offset.Zero, 1f, Offset(250f, 250f), Offset(500f, 500f),
            viewport, Rect(-20f, -20f, 20f, 20f),
        )
        assertEquals(0f, result.pan.x, .001f)
        assertEquals(0f, result.pan.y, .001f)
    }
}
