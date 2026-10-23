package com.rteats.mpeineo.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRevealMotionTest {
    private val entry = Rect(16f, 50f, 264f, 106f)

    @Test fun fieldMovesFromCollapsedEntryToSearchInput() {
        val destination = Rect(72f, 44f, 344f, 100f)
        val start = searchFieldTransform(entry, destination, 0f)
        assertEquals(-68f, start.translationX, 0.001f)
        assertEquals(6f, start.translationY, 0.001f)
        assertEquals(entry.width / destination.width, start.scaleX, 0.001f)
        assertEquals(1f, start.scaleY, 0.001f)
        assertEquals(SearchFieldTransform(), searchFieldTransform(entry, destination, 1f))
    }

    @Test fun unavailableBoundsNeverCauseAnInvalidTransform() {
        assertEquals(SearchFieldTransform(),
            searchFieldTransform(Rect.Zero, entry, 0f))
        assertEquals(SearchFieldTransform(),
            searchFieldTransform(entry, Rect.Zero, 0.5f))
    }

    @Test fun stagesAssembleWithInputFirstAndContentLast() {
        val start = searchElementsProgress(0f)
        assertEquals(0f, start.field, 0f)
        assertEquals(0f, start.background, 0f)
        assertEquals(0f, start.controls, 0f)
        assertEquals(0f, start.content, 0f)

        val halfway = searchElementsProgress(0.5f)
        assertEquals(0.5f, halfway.field, 0f)
        assertEquals(0.0f, searchElementsProgress(0.15f).content, 0f)
        assertTrue(halfway.background > halfway.controls)
        assertTrue(halfway.controls > halfway.content)

        val end = searchElementsProgress(1f)
        assertEquals(1f, end.field, 0f)
        assertEquals(1f, end.background, 0f)
        assertEquals(1f, end.controls, 0f)
        assertEquals(1f, end.content, 0f)
    }

    @Test fun stageProgressIsMonotonicAndClamped() {
        val checkpoints = (0..20).map { searchElementsProgress(it / 20f) }
        for ((previous, next) in checkpoints.zipWithNext()) {
            assertTrue(next.field >= previous.field)
            assertTrue(next.background >= previous.background)
            assertTrue(next.controls >= previous.controls)
            assertTrue(next.content >= previous.content)
        }
        assertEquals(0f, searchElementsProgress(-0.5f).background, 0f)
        assertEquals(1f, searchElementsProgress(1.5f).content, 0f)
    }

    @Test fun closingRetracesTheSameStageValues() {
        val progress = searchElementsProgress(0.42f)
        assertEquals(progress, searchElementsProgress(0.42f))
        assertEquals(searchFieldTransform(entry, entry, 0.42f),
            searchFieldTransform(entry, entry, 0.42f))
        assertEquals(searchElementsProgress(0f), searchElementsProgress(-5f))
        assertEquals(searchElementsProgress(1f), searchElementsProgress(5f))
    }
}
