package com.rteats.mpeineo.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRevealMotionTest {
    private val entry = Rect(16f, 50f, 264f, 106f)
    private val viewport = Size(360f, 800f)

    @Test fun startsAtTheActualCollapsedFieldNotAnOffscreenPosition() {
        assertEquals(entry, searchRevealRect(entry, viewport, 0f))
    }

    @Test fun expandsToTheFullScreenAndRetracesTheSameBounds() {
        assertEquals(Rect(0f, 0f, 360f, 800f),
            searchRevealRect(entry, viewport, 1f))
        val halfway = searchRevealRect(entry, viewport, 0.5f)
        assertEquals(Rect(8f, 25f, 312f, 453f), halfway)
        assertEquals(halfway, searchRevealRect(entry, viewport, 0.5f))
    }

    @Test fun missingMeasurementsSafelyFallbackToFullScreen() {
        assertEquals(Rect(0f, 0f, 360f, 800f),
            searchRevealRect(Rect.Zero, viewport, 0f))
    }
}
