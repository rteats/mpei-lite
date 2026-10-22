package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BARS presentation-only flags must not reset session data. */
class BarsWebFlowStateTest {
    @Test fun markRefreshResetsPresentationFlagsButPreservesKnownRoute() {
        val state = BarsWebFlowState()
        state.mainFrameFailed = true
        state.needsFullViewport = false
        state.officialMainUrl = "https://bars.mpei.ru/bars_web/ST_Study/Main/Main"
        state.manuallyRequestedConnection = true

        state.prepareMarkRefresh()

        assertFalse(state.mainFrameFailed)
        assertTrue(state.needsFullViewport)
        assertTrue(state.manuallyRequestedConnection)
        assertEquals("https://bars.mpei.ru/bars_web/ST_Study/Main/Main",
            state.officialMainUrl)
    }

    @Test fun checkRetryLeavesManualConnectionIntentUntouched() {
        val state = BarsWebFlowState()
        state.mainFrameFailed = true
        state.manuallyRequestedConnection = true
        state.needsFullViewport = false

        state.prepareCheckRetry()

        assertFalse(state.mainFrameFailed)
        assertTrue(state.needsFullViewport)
        assertTrue(state.manuallyRequestedConnection)
    }
}
