package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpatialMotionUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sectionExpansionKeepsContentAccessible() {
        rule.setContent {
            var expanded by remember { mutableStateOf(false) }
            MpeiNeoTheme {
                SettingsExpansion(
                    title = "Section",
                    subtitle = "Preferences",
                    iconRes = AppIcons.Appearance,
                    expanded = expanded,
                    onExpand = { expanded = !expanded },
                ) {
                    Text("Expanded content")
                }
            }
        }
        rule.onNodeWithText("Expanded content").assertDoesNotExist()
        rule.onNodeWithText("Section").performClick()
        rule.onNodeWithText("Expanded content").assertIsDisplayed()
        rule.onNodeWithText("Section").performClick()
        rule.onNodeWithText("Expanded content").assertDoesNotExist()
    }

    @Test fun fullScreenOverlayCompletesBothDirections() {
        rule.setContent {
            var visible by remember { mutableStateOf(false) }
            MpeiNeoTheme {
                Box(Modifier.fillMaxSize()) {
                    Text("Root content")
                    SpatialScreenOverlay(visible = visible) {
                        Text("Detail content")
                    }
                    // An action outside the overlay remains available.
                    androidx.compose.material3.TextButton(
                        onClick = { visible = !visible },
                    ) { Text("Toggle overlay") }
                }
            }
        }
        rule.onNodeWithText("Toggle overlay").performClick()
        rule.onNodeWithText("Detail content").assertIsDisplayed()
        rule.onNodeWithText("Toggle overlay").performClick()
        rule.onNodeWithText("Detail content").assertDoesNotExist()
    }
}
