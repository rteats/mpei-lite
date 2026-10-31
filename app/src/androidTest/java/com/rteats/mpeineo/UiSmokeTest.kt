package com.rteats.mpeineo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun fiveBottomDestinationsAndNativeScheduleSearch() {
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
        composeRule.onNodeWithTag("nav-settings").performClick()
        composeRule.onNodeWithTag("settings-overview").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-schedule").performClick()
        composeRule.onNodeWithTag("schedule-explorer").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-search-open").performClick()
        composeRule.onNodeWithTag("schedule-target-query").assertIsDisplayed()
    }

    @Test fun mailBrowserDoesNotSwitchToMap() {
        composeRule.onNodeWithTag("nav-mail").performClick()
        composeRule.onNodeWithTag("portal-header-ОСЭП").performClick()
        composeRule.onNodeWithTag("mail-webview").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-mail").assertIsSelected()
        composeRule.onNodeWithTag("campus-map-screen").assertDoesNotExist()
    }
}
