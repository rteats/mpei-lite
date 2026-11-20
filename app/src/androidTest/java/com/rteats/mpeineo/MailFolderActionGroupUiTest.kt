package com.rteats.mpeineo

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rteats.mpeineo.data.OwaMailFolder
import com.rteats.mpeineo.ui.MailFolderTabs
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun scrollableRowCentersTheIndicatorUnderTheSelectedFolder() {
        var folder by mutableStateOf(OwaMailFolder.INBOX)
        var indicator = Color.Unspecified
        rule.setContent { MpeiNeoTheme {
            indicator = MaterialTheme.colorScheme.primary
            MailFolderTabs(
                selectedFolder = folder,
                onSelectFolder = { folder = it },
                availableFolders = listOf(OwaMailFolder.INBOX, OwaMailFolder.SENT,
                    OwaMailFolder.JUNK, OwaMailFolder.DELETED, OwaMailFolder.DRAFTS, OwaMailFolder.NOTES),
                modifier = Modifier.width(320.dp),
            )
        } }

        val row = rule.onNodeWithTag("mail-folder-tabs")
        val sent = rule.onNodeWithTag("mail-folder-sent").performClick()
        val image = row.captureToImage().toPixelMap()
        val lines = with(rule.density) { 3.dp.roundToPx() }
        val coloredColumns = (0 until image.width).filter { x ->
            (image.height - lines until image.height).any { y -> image[x, y] == indicator }
        }
        assertTrue("Primary indicator is visible", coloredColumns.isNotEmpty())
        val tabCenter = sent.fetchSemanticsNode().boundsInRoot.center.x -
            row.fetchSemanticsNode().boundsInRoot.left
        assertEquals(tabCenter, (coloredColumns.first() + coloredColumns.last()) / 2f, 2f)
    }

    @Test fun iconTabsContainOnlyDiscoveredFolders() {
        var folder by mutableStateOf(OwaMailFolder.INBOX)
        val custom = OwaMailFolder("", "CUSTOM_TEST", "Projects", folderId = "opaque")
        rule.setContent { MpeiNeoTheme {
            MailFolderTabs(
                selectedFolder = folder,
                availableFolders = listOf(OwaMailFolder.INBOX, OwaMailFolder.SENT,
                    OwaMailFolder.DRAFTS, custom),
                onSelectFolder = { folder = it },
            )
        } }
        rule.onNodeWithTag("mail-folder-more").assertDoesNotExist()
        rule.onNodeWithTag("mail-folder-deleted").assertDoesNotExist()
        rule.onNodeWithTag("mail-folder-junk").assertDoesNotExist()
        rule.onNodeWithTag("mail-folder-drafts").assertIsDisplayed()
            .assertContentDescriptionEquals("Черновики").performClick()
        rule.onNodeWithTag("mail-folder-drafts").assertIsSelected()
        rule.runOnIdle { assertEquals(OwaMailFolder.DRAFTS, folder) }
    }
}
