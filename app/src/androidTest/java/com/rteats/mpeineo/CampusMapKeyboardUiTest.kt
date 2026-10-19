package com.rteats.mpeineo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampusMapKeyboardUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun keyboardHighlightsBuildingDirectlyWithoutModal() {
        rule.onNodeWithTag("service-maps").performClick()
        rule.onNodeWithTag("campus-building-keyboard").assertIsDisplayed()
        rule.onNodeWithTag("campus-keyboard-toggle").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("campus-key-А").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("campus-key-А").performClick()
        rule.onNodeWithText("Корпус А").assertIsDisplayed()
        rule.onNodeWithTag("campus-keyboard-toggle").performClick()
        rule.onNodeWithTag("campus-key-В").performClick()
        rule.onNodeWithText("Корпус В").assertIsDisplayed()
    }
}
