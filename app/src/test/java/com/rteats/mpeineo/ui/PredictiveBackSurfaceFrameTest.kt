package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveBackSurfaceFrameTest {
    @Test fun previewShrinksAndLeavesAnEightDpGapAtTheGestureEdge() {
        val start = predictiveBackSurfaceFrame(0f, widthPx = 1080, density = 3f,
            direction = 1f)
        assertEquals(1f, start.scale, 0.0001f)
        assertEquals(0f, start.translationXPx, 0.0001f)

        val middle = predictiveBackSurfaceFrame(0.5f, 1080, 3f, 1f)
        assertTrue(middle.scale in 0.9f..1f)
        assertTrue(middle.translationXPx in 0f..30f)

        val left = predictiveBackSurfaceFrame(1f, 1080, 3f, 1f)
        val right = predictiveBackSurfaceFrame(1f, 1080, 3f, -1f)
        assertEquals(0.9f, left.scale, 0.0001f)
        assertEquals(30f, left.translationXPx, 0.0001f)
        assertEquals(-30f, right.translationXPx, 0.0001f)
        assertEquals(28f, left.cornerRadiusDp, 0.0001f)
    }

    @Test fun smallWindowsAndOutOfRangeProgressRemainBounded() {
        val small = predictiveBackSurfaceFrame(1f, widthPx = 240, density = 3f,
            direction = 1f)
        assertEquals(0f, small.translationXPx, 0.0001f)
        assertEquals(1f, predictiveBackSurfaceFrame(-1f, 1080, 3f, 1f).scale,
            0.0001f)
        assertEquals(0.9f, predictiveBackSurfaceFrame(2f, 1080, 3f, 1f).scale,
            0.0001f)
    }
}
