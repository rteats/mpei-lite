package com.rteats.mpeineo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mailPortalNavbarBackReturnsToInbox() {
        composeRule.onNodeWithContentDescription("Почта").performClick()
        composeRule.onNodeWithTag("portal-header-ОСЭП").performClick()
        composeRule.onNodeWithTag("mail-webview").assertIsDisplayed()
        composeRule.onNodeWithTag("mail-browser-controls").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-overflow").performClick()
        composeRule.onNodeWithTag("browser-reload").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-copy-url").assertIsDisplayed()

        // The popup owns the foreground until a menu item closes it.
        composeRule.onNodeWithTag("browser-reload").performClick()
        composeRule.onNodeWithTag("browser-exit").performClick()
        composeRule.onNodeWithTag("mail-inbox").assertIsDisplayed()
    }

    @Test
    fun primaryDestinationsRender() {
        composeRule.onNodeWithTag("compact-search-action").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-week-refresh").assertIsDisplayed()
        composeRule.onNodeWithTag("search-app-bar-entry").assertDoesNotExist()
        composeRule.onNodeWithTag("compact-search-action").performClick()

        composeRule
            .onNodeWithText("Все")
            .assertIsDisplayed()

        composeRule
            .onNodeWithContentDescription("Закрыть поиск")
            .performClick()

        composeRule
            .onNodeWithContentDescription("БАРС")
            .performClick()

        composeRule
            .onNodeWithTag("bars-screen")
            .assertIsDisplayed()

        composeRule
            .onNodeWithContentDescription("Почта")
            .performClick()

        composeRule
            .onNodeWithTag("mail-inbox")
            .assertIsDisplayed()

        // Mail has one toolbar title and one bottom-tab label, but no
        // additional in-content "Почта" heading.
        composeRule.onAllNodesWithText("Почта").assertCountEquals(2)

        composeRule
            .onNodeWithTag("portal-header-ОСЭП")
            .assertIsDisplayed()

        composeRule
            .onNodeWithContentDescription("Настройки")
            .performClick()

        composeRule
            .onNodeWithText("Обновления приложения")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Диагностика")
            .assertIsDisplayed()
    }
}
