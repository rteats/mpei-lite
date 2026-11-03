package com.rteats.mpeineo

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rteats.mpeineo.data.OwaMailFolder
import com.rteats.mpeineo.ui.MailFolderTabs
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MailFolderActionGroupUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun primaryTabsFitCompactWidth() {
        rule.setContent { MpeiNeoTheme {
            MailFolderTabs(
                selectedFolder = OwaMailFolder.INBOX,
                onSelectFolder = {},
                modifier = Modifier.width(320.dp),
            )
        } }
        rule.onNodeWithTag("mail-folder-tabs").assertIsDisplayed()
        rule.onNodeWithTag("mail-folder-inbox").assertIsDisplayed().assertIsSelected()
        rule.onNodeWithTag("mail-folder-sent").assertIsDisplayed().assertIsNotSelected()
    }

    @Test fun selectingFolderUpdatesPrimaryTab() {
        var folder by mutableStateOf(OwaMailFolder.INBOX)
        var changes = 0
        rule.setContent { MpeiNeoTheme {
            MailFolderTabs(
                selectedFolder = folder,
                onSelectFolder = { folder = it; changes++ },
            )
        } }
        rule.onNodeWithTag("mail-folder-sent").performClick()
        rule.onNodeWithTag("mail-folder-sent").assertIsSelected()
        rule.onNodeWithTag("mail-folder-inbox").assertIsNotSelected()
        rule.onNodeWithTag("mail-folder-sent").performClick()
        rule.runOnIdle {
            assertEquals(OwaMailFolder.SENT, folder)
            assertEquals(1, changes)
        }
    }
}
