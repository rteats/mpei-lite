package com.rteats.mpeineo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsBackgroundSyncTest {
    @Test fun homeStartsOnlyWhenAutomaticUpdatesAllowed() {
        assertTrue(shouldInitializeBarsWebView(
            connectionRequired = true, hasWebView = false,
            active = false, backgroundAllowed = true,
        ))
        assertFalse(shouldInitializeBarsWebView(
            connectionRequired = true, hasWebView = false,
            active = false, backgroundAllowed = false,
        ))
    }

    @Test fun enteringBarsStillCreatesInteractiveSession() {
        assertTrue(shouldInitializeBarsWebView(
            connectionRequired = true, hasWebView = false,
            active = true, backgroundAllowed = false,
        ))
    }

    @Test fun sessionIsNotDuplicatedWhenMovingBetweenServices() {
        assertTrue(shouldInitializeBarsWebView(
            connectionRequired = true, hasWebView = true,
            active = false, backgroundAllowed = false,
        ))
        assertFalse(shouldInitializeBarsWebView(
            connectionRequired = false, hasWebView = true,
            active = false, backgroundAllowed = true,
        ))
    }
}
