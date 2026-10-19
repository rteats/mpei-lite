package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SentRecipientPriorityTest {
    @Test fun prioritizesVisibleMessagesAndPreservesRemainingOrder() {
        val ids = listOf(1L, 2L, 3L, 4L, 5L)
        assertEquals(listOf(4L, 2L, 1L),
            prioritizedSentIds(ids, setOf(4L, 2L), batchSize = 3))
    }

    @Test fun fallsBackToNewestFirstWhenViewportUnknown() {
        assertEquals(listOf(1L, 2L, 3L),
            prioritizedSentIds(listOf(1L, 2L, 3L, 4L), emptySet(), 3))
    }

    @Test fun deduplicatesAndBoundsBatches() {
        assertEquals(listOf(2L, 1L),
            prioritizedSentIds(listOf(1L, 2L, 1L, 3L), setOf(2L), 2))
        assertEquals(emptyList<Long>(),
            prioritizedSentIds(listOf(1L), setOf(1L), 0))
    }
}
