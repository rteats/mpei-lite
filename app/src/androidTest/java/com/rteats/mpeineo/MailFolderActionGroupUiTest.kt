package com.rteats.mpeineo

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rteats.mpeineo.data.OwaMailFolder
import com.rteats.mpeineo.ui.MailFolderActionGroup
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MailFolderActionGroupUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun narrowWidthRetainsBothActionsInOverflow() {
        var web = 0
        var signOut = 0
        rule.setContent { MpeiNeoTheme {
            MailFolderActionGroup(
                selectedFolder = OwaMailFolder.INBOX,
                onSelectFolder = {},
                onOpenPortal = { web++ },
                onRequestLogout = { signOut++ },
                modifier = Modifier.width(320.dp),
            )
        } }
        rule.onNodeWithTag("mail-folder-inbox").assertIsDisplayed()
        rule.onNodeWithTag("mail-folder-sent").assertIsDisplayed()
        rule.onNodeWithTag("mail-actions-overflow").performClick()
        rule.onNodeWithText("Открыть ОСЭП").performClick()
        rule.onNodeWithTag("mail-actions-overflow").performClick()
        rule.onNodeWithText("Выйти из ОСЭП").performClick()
        rule.runOnIdle {
            assertEquals(1, web)
            assertEquals(1, signOut)
        }
    }

    @Test fun compactGroupExposesBothFoldersWebViewAndLogout() {
        var folder by mutableStateOf(OwaMailFolder.INBOX)
        var webOpens = 0
        var logoutRequests = 0
        rule.setContent { MpeiNeoTheme {
            MailFolderActionGroup(
                selectedFolder = folder,
                onSelectFolder = { folder = it },
                onOpenPortal = { webOpens++ },
                onRequestLogout = { logoutRequests++ },
            )
        } }

        rule.onNodeWithTag("mail-folder-button-group").assertIsDisplayed()
        rule.onNodeWithTag("mail-folder-inbox").assertIsDisplayed()
        rule.onNodeWithTag("mail-folder-sent").performClick()
        rule.onNodeWithTag("portal-header-ОСЭП").performClick()
        rule.onNodeWithTag("mail-logout-open").performClick()
        rule.runOnIdle {
            assertEquals(OwaMailFolder.SENT, folder)
            assertEquals(1, webOpens)
            assertEquals(1, logoutRequests)
        }
    }
}
