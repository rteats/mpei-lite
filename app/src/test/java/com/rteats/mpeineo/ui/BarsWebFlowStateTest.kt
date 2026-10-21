package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsWebFlowStateTest {
    @Test fun markRefreshResetsOnlyTransientExtractionFlags() {
        val state = BarsWebFlowState()
        state.redirectingToMarks = true
        state.summaryFetchAttempted = true
        state.summaryExtractionStarted = true
        state.mainFrameFailed = true
        state.needsFullViewport = false
        state.officialMainUrl = "https://bars.mpei.ru/bars_web/ST_Study/Main/Main"
        state.manuallyRequestedConnection = true

        state.prepareMarkRefresh()

        assertFalse(state.redirectingToMarks)
        assertFalse(state.summaryFetchAttempted)
        assertFalse(state.summaryExtractionStarted)
        assertFalse(state.mainFrameFailed)
        assertTrue(state.needsFullViewport)
        assertTrue(state.manuallyRequestedConnection)
        assertEquals("https://bars.mpei.ru/bars_web/ST_Study/Main/Main",
            state.officialMainUrl)
    }

    @Test fun checkRetryPreservesAuthenticatedPageAndConnectionIntent() {
        val state = BarsWebFlowState()
        state.redirectingToMarks = true
        state.summaryFetchAttempted = true
        state.mainFrameFailed = true
        state.needsFullViewport = false

        state.prepareCheckRetry()

        assertTrue(state.redirectingToMarks)
        assertFalse(state.summaryFetchAttempted)
        assertFalse(state.mainFrameFailed)
        assertTrue(state.needsFullViewport)
    }
}
