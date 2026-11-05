package com.rteats.mpeineo.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.rteats.mpeineo.data.OwaMailFolder
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the Android Accessibility Test Framework, not only semantic assertions. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class Material3AccessibilityUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun mailFolderTabsAreAccessibleAndStateful() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(OwaMailFolder.INBOX) }
            MpeiNeoTheme {
                MailFolderTabs(
                    selectedFolder = selected,
                    onSelectFolder = { selected = it },
                )
            }
        }
        composeRule.enableAccessibilityChecks()
        composeRule.onRoot().tryPerformAccessibilityChecks()
        composeRule.onNodeWithTag("mail-folder-inbox").assertIsSelected()
        composeRule.onNodeWithTag("mail-folder-sent").assertIsDisplayed()
        composeRule.onNodeWithTag("mail-folder-sent").performClick()
        composeRule.onNodeWithTag("mail-folder-sent").assertIsSelected()
    }
}
