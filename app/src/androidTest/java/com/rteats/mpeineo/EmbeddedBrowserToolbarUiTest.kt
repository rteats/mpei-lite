package com.rteats.mpeineo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.rteats.mpeineo.ui.EmbeddedBrowserState
import com.rteats.mpeineo.ui.EmbeddedBrowserAppBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EmbeddedBrowserAppBarUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun backForwardAndReloadSendOnlyIntendedActions() {
        var back = 0
        var forward = 0
        var reload = 0
        rule.setContent {
            MaterialTheme {
                EmbeddedBrowserAppBar(
                    state = EmbeddedBrowserState(
                        url = "https://bars.mpei.ru/bars_web/",
                        canGoBack = true,
                        canGoForward = true,
                    ),
                    onExit = {},
                    onBack = { back++ },
                    onForward = { forward++ },
                    onReload = { reload++ },
                )
            }
        }
        rule.onNodeWithTag("browser-overflow").performClick()
        rule.onNodeWithTag("browser-back").performClick()
        rule.onNodeWithTag("browser-overflow").performClick()
        rule.onNodeWithTag("browser-forward").performClick()
        rule.onNodeWithTag("browser-overflow").performClick()
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
                EmbeddedBrowserAppBar(
                    state = EmbeddedBrowserState(url = "about:blank"),
                    onExit = {},
                    onBack = {},
                    onForward = {},
                    onReload = {},
                )
            }
        }
        rule.onNodeWithTag("browser-overflow").performClick()
        rule.onNodeWithTag("browser-back").assertIsNotEnabled()
        rule.onNodeWithTag("browser-forward").assertIsNotEnabled()
        rule.onNodeWithTag("browser-copy-url").assertIsNotEnabled()
        rule.onNodeWithTag("browser-reload").assertIsEnabled()
        rule.onNodeWithTag("browser-external").assertIsNotEnabled()
    }
}
