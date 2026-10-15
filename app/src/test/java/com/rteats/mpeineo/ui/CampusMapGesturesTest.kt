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
            4.8f, Offset.Zero, 3f, Offset.Zero, Offset(500f, 500f),
            viewport, roomyWorld,
        )
        val shrunk = applyCampusGesture(
            .8f, Offset.Zero, .1f, Offset.Zero, Offset(500f, 500f),
            viewport, roomyWorld,
        )
        assertEquals(5f, enlarged.zoom, .001f)
        assertEquals(.65f, shrunk.zoom, .001f)
    }

    @Test fun draggingDoesNotExposeEmptySpaceOnSmallMap() {
        val result = applyCampusGesture(
            1f, Offset.Zero, 1f, Offset(250f, 250f), Offset(500f, 500f),
            viewport, Rect(-20f, -20f, 20f, 20f),
        )
        assertEquals(Offset.Zero, result.pan)
    }
}
