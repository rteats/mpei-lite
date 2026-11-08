package com.rteats.mpeineo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailChromeVisibilityTest {
    @Test fun messageDetailKeepsSearchBarInTheShell() {
        assertFalse(mailHidesShellSearchBar(
            loginPageOpen = false,
            messageOpen = true,
            composing = false,
        ))
        assertFalse(mailHidesShellSearchBar(
            loginPageOpen = false,
            messageOpen = false,
            composing = false,
        ))
    }

    @Test fun loginAndComposerOccupyTheShellHeader() {
        assertTrue(mailHidesShellSearchBar(
            loginPageOpen = true,
            messageOpen = true,
            composing = false,
        ))
        assertTrue(mailHidesShellSearchBar(
            loginPageOpen = false,
            messageOpen = false,
            composing = true,
        ))
    }
}
