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

    @Test fun searchFieldStartsOnTappedInputAndEndsInItsNewSlot() {
        val destination = Rect(72f, 44f, 344f, 100f)
        val start = searchFieldTransform(entry, destination, 0f)
        assertEquals(-68f, start.translationX, 0.001f)
        assertEquals(6f, start.translationY, 0.001f)
        assertEquals(entry.width / destination.width, start.scaleX, 0.001f)
        assertEquals(1f, start.scaleY, 0.001f)

        assertEquals(SearchFieldTransform(), searchFieldTransform(entry, destination, 1f))
        assertEquals(searchFieldTransform(entry, destination, 0.4f),
            searchFieldTransform(entry, destination, 0.4f))
    }

    @Test fun fieldMotionUsesIdentityUntilBothFieldsAreMeasured() {
        assertEquals(SearchFieldTransform(),
            searchFieldTransform(entry, Rect.Zero, 0f))
        assertEquals(SearchFieldTransform(),
            searchFieldTransform(Rect.Zero, entry, 0.5f))
    }

    @Test fun missingMeasurementsSafelyFallbackToFullScreen() {
        assertEquals(Rect(0f, 0f, 360f, 800f),
            searchRevealRect(Rect.Zero, viewport, 0f))
    }
}
