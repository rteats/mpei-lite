package com.rteats.mpeineo

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rteats.mpeineo.ui.MainUiState
import com.rteats.mpeineo.ui.BarsUiState
import com.rteats.mpeineo.ui.MailUiState
import com.rteats.mpeineo.ui.AppTab
import com.rteats.mpeineo.ui.HomeTileLayout
import com.rteats.mpeineo.ui.HomeTileSize
import com.rteats.mpeineo.ui.SuperAppHome
import com.rteats.mpeineo.ui.ContextSearchAppBar
import com.rteats.mpeineo.ui.searchAppBarContext
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeLayoutUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeEditorChangesSizeAndOrderWithoutOpeningServices() {
        var layout by mutableStateOf(HomeTileLayout.defaults)
        var editing by mutableStateOf(false)
        var opens = 0
        rule.setContent {
            MpeiNeoTheme {
                Column {
                    ContextSearchAppBar(
                        context = searchAppBarContext(null),
                        onSearch = {},
                        homeEditing = editing,
                        onHomeEditToggle = { editing = !editing },
                        onHomeSettings = {},
                    )
                    SuperAppHome(
                        schedule = MainUiState(),
                        bars = BarsUiState(),
                        mail = MailUiState(),
                        onOpenBarsDiscipline = {},
                        onOpenMailMessage = {},
                        tiles = layout,
                        onChangeLayout = { layout = it },
                        onOpen = { opens++ },
                        editing = editing,
                    )
                }
            }
        }

        rule.waitForIdle()
        // Home actions share one toolbar row, not the scrolling tile grid.
        val searchRect = rule.onNodeWithTag("search-app-bar-entry")
            .fetchSemanticsNode().boundsInRoot
        val editRect = rule.onNodeWithTag("superapp-edit")
            .fetchSemanticsNode().boundsInRoot
        val settingsRect = rule.onNodeWithTag("superapp-settings")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(searchRect.right <= editRect.left + 1f)
        assertTrue(editRect.right <= settingsRect.left + 1f)
        assertEquals(searchRect.center.y, editRect.center.y, 6f)

        rule.onNodeWithContentDescription("Настроить кнопки").performClick()
        rule.onNodeWithTag("home-edit-schedule").assertIsDisplayed()
        rule.onNodeWithTag("home-size-schedule-expanded").performClick()
        rule.onNodeWithTag("home-move-down-schedule").performClick()
        rule.runOnIdle {
            assertEquals(AppTab.BARS, layout.first().tab)
            assertEquals(AppTab.SCHEDULE, layout[1].tab)
            assertEquals(HomeTileSize.EXPANDED, layout[1].size)
            assertEquals(0, opens)
        }
        rule.onNodeWithContentDescription("Готово").performClick()
        rule.onNodeWithTag("service-schedule").assertIsDisplayed()
    }
}
