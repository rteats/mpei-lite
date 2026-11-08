package com.rteats.mpeineo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun fourBottomDestinationsAndNativeScheduleSearch() {
        composeRule.onNodeWithTag("main-navigation-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-explorer").assertIsDisplayed()
        composeRule.onNodeWithTag("primary-icon-tabs").assertDoesNotExist()
        composeRule.onNodeWithTag("global-search-input").assertDoesNotExist()
        composeRule.onNodeWithTag("nav-bars").assertIsDisplayed()

        composeRule.onNodeWithTag("nav-mail").performClick()
        composeRule.onNodeWithTag("mail-inbox").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-mail").assertIsSelected()
        composeRule.onNodeWithTag("nav-maps").performClick()
        composeRule.onNodeWithTag("campus-map-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("open-settings").performClick()
        composeRule.onNodeWithTag("settings-overview").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-back").performClick()
        composeRule.onNodeWithTag("nav-schedule").performClick()
        composeRule.onNodeWithTag("schedule-explorer").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-search-open").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-target-query").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("schedule-search-results")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("schedule-search-results").assertIsDisplayed()
        composeRule.onNodeWithText("Группы").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-target-query").performTextInput("A-17")
        composeRule.onNodeWithContentDescription("Закрыть поиск").performClick()
        composeRule.onNodeWithTag("schedule-explorer").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-target-query").performClick()
        composeRule.onNodeWithTag("schedule-target-query").assertTextContains("A-17")
        composeRule.onNodeWithContentDescription("Закрыть поиск").performClick()
    }

    @Test fun settingsKeepsOriginAndNavigationMountedForPredictiveBack() {
        composeRule.onNodeWithTag("nav-maps").performClick()
        composeRule.onNodeWithTag("open-settings").performClick()
        composeRule.onNodeWithTag("settings-overview").assertIsDisplayed()
        composeRule.onNodeWithTag("campus-map-screen").assertExists()
        composeRule.onNodeWithTag("main-navigation-bar").assertExists()
        composeRule.onNodeWithTag("settings-back").performClick()
        composeRule.onNodeWithTag("nav-maps").assertIsSelected()
        composeRule.onNodeWithTag("campus-map-screen").assertIsDisplayed()
    }

    @Test fun mailSearchOpensNativeMaterialResults() {
        composeRule.onNodeWithTag("nav-mail").performClick()
        composeRule.onNodeWithTag("mail-search-open").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("mail-search-results")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("mail-search-results").assertIsDisplayed()
    }

    @Test fun mailBrowserDoesNotSwitchToMap() {
        composeRule.onNodeWithTag("nav-mail").performClick()
        composeRule.onNodeWithTag("mail-actions-overflow").performClick()
        composeRule.onNodeWithTag("portal-header-ОСЭП").performClick()
        composeRule.onNodeWithTag("mail-webview").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-mail").assertDoesNotExist()
        composeRule.onNodeWithTag("campus-map-screen").assertDoesNotExist()
        composeRule.onNodeWithTag("browser-exit").performClick()
        composeRule.onNodeWithTag("nav-mail").assertIsSelected()
    }
}
