package com.rteats.mpeineo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSearchImeDismissTest {
    @Test fun searchRemainsOpenWhileKeyboardIsStarting() {
        assertFalse(shouldCollapseSearchOnImeDismiss(
            active = true,
            expanded = true,
            wasKeyboardVisible = false,
            keyboardVisible = false,
        ))
    }

    @Test fun dismissesAfterKeyboardWasVisible() {
        assertTrue(shouldCollapseSearchOnImeDismiss(
            active = true,
            expanded = true,
            wasKeyboardVisible = true,
            keyboardVisible = false,
        ))
    }

    @Test fun doesNotReactWhenKeyboardRemainsVisible() {
        assertFalse(shouldCollapseSearchOnImeDismiss(
            active = true,
            expanded = true,
            wasKeyboardVisible = true,
            keyboardVisible = true,
        ))
    }

    @Test fun doesNotReactOutsideActiveSearch() {
        assertFalse(shouldCollapseSearchOnImeDismiss(
            active = false,
            expanded = true,
            wasKeyboardVisible = true,
            keyboardVisible = false,
        ))
        assertFalse(shouldCollapseSearchOnImeDismiss(
            active = true,
            expanded = false,
            wasKeyboardVisible = true,
            keyboardVisible = false,
        ))
    }
    @Test fun openingFavoritePopupDoesNotCollapseOnImeLoss() {
        assertFalse(shouldCollapseSearchOnImeDismiss(
            active = true,
            expanded = true,
            wasKeyboardVisible = true,
            keyboardVisible = false,
            childPopupActive = true,
        ))
    }

    @Test fun keyboardDismissReturnsToNormalAfterPopupCloses() {
        assertTrue(shouldCollapseSearchOnImeDismiss(
            active = true,
            expanded = true,
            wasKeyboardVisible = true,
            keyboardVisible = false,
            childPopupActive = false,
        ))
    }

}
