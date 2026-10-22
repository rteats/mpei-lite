package com.rteats.mpeineo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.rteats.mpeineo.ui.EmbeddedBrowserState
import com.rteats.mpeineo.ui.EmbeddedBrowserToolbar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EmbeddedBrowserToolbarUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun backForwardAndReloadSendOnlyIntendedActions() {
        var back = 0
        var forward = 0
        var reload = 0
        rule.setContent {
            MaterialTheme {
                EmbeddedBrowserToolbar(
                    state = EmbeddedBrowserState(
                        url = "https://bars.mpei.ru/bars_web/",
                        canGoBack = true,
                        canGoForward = true,
                    ),
                    onBack = { back++ },
                    onForward = { forward++ },
                    onReload = { reload++ },
                )
            }
        }
        rule.onNodeWithTag("browser-back").performClick()
        rule.onNodeWithTag("browser-forward").performClick()
        rule.onNodeWithTag("browser-reload").performClick()
        rule.runOnIdle {
            assertEquals(1, back)
            assertEquals(1, forward)
            assertEquals(1, reload)
        }
    }

    @Test fun historyButtonsDisabledWithoutHistory() {
        rule.setContent {
            MaterialTheme {
                EmbeddedBrowserToolbar(
                    state = EmbeddedBrowserState(url = "about:blank"),
                    onBack = {},
                    onForward = {},
                    onReload = {},
                )
            }
        }
        rule.onNodeWithTag("browser-back").assertIsNotEnabled()
        rule.onNodeWithTag("browser-forward").assertIsNotEnabled()
        rule.onNodeWithTag("browser-copy-url").assertIsNotEnabled()
        rule.onNodeWithTag("browser-reload").assertIsEnabled()
    }
}
