package com.rteats.mpeineo

import androidx.activity.BackEventCompat
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
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
        composeRule.onNodeWithTag("open-settings").assertDoesNotExist()
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
        composeRule.onNodeWithTag("schedule-expanded-query").performTextInput("A-17")
        composeRule.onNodeWithContentDescription("Закрыть поиск").performClick()
        composeRule.onNodeWithTag("schedule-explorer").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-target-query").performClick()
        composeRule.onNodeWithTag("schedule-expanded-query").assertTextContains("A-17")
        composeRule.onNodeWithContentDescription("Закрыть поиск").performClick()
    }

    @Test fun settingsKeepsOriginAndNavigationMountedForPredictiveBack() {
        composeRule.onNodeWithTag("schedule-overflow").performClick()
        composeRule.onNodeWithTag("schedule-open-settings").performClick()
        composeRule.onNodeWithTag("settings-overview").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule-explorer").assertExists()
        composeRule.onNodeWithTag("main-navigation-bar").assertExists()
        composeRule.onNodeWithTag("settings-back").performClick()
        composeRule.onNodeWithTag("nav-schedule").assertIsSelected()
        composeRule.onNodeWithTag("schedule-explorer").assertIsDisplayed()
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

    @Test fun mailBrowserRestoresAcrossTabsAndReselectReturnsToRoot() {
        composeRule.onNodeWithTag("nav-mail").performClick()
        composeRule.onNodeWithTag("mail-actions-overflow").performClick()
        composeRule.onNodeWithTag("portal-header-ОСЭП").performClick()
        composeRule.onNodeWithTag("mail-webview").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-mail").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-maps").performClick()
        composeRule.onNodeWithTag("nav-maps").assertIsSelected()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("nav-schedule") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("nav-schedule").assertIsSelected()
        composeRule.onNodeWithTag("nav-mail").performClick()
        composeRule.onNodeWithTag("mail-webview").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-mail").performClick()
        composeRule.onNodeWithTag("mail-webview").assertDoesNotExist()
        composeRule.onNodeWithTag("nav-mail").assertIsSelected()
    }

    @Test fun mapKeepsItsCameraAfterSwitchingAway() {
        composeRule.onNodeWithTag("nav-maps").performClick()
        composeRule.onNodeWithTag("campus-zoom-in").performClick()
        val zoomed = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "125%")
        composeRule.onNodeWithTag("campus-zoom-reset").assert(zoomed)
        composeRule.onNodeWithTag("nav-schedule").performClick()
        composeRule.onNodeWithTag("schedule-explorer").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-maps").performClick()
        composeRule.onNodeWithTag("campus-zoom-reset").assert(zoomed)
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test fun schedulePreviewHasTheSamePositionBeforeAndAfterBackCommit() {
        composeRule.onNodeWithTag("nav-maps").performClick()
        composeRule.onNodeWithTag("nav-maps").assertIsSelected()
        composeRule.runOnUiThread {
            val dispatcher = composeRule.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(160f, 0f, .7f, BackEventCompat.EDGE_LEFT))
        }
        val schedule = composeRule.onNodeWithTag("schedule-search-open", useUnmergedTree = true)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("schedule-search-open", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val previewY = schedule.fetchSemanticsNode().positionInRoot.y
        composeRule.onNodeWithTag("nav-maps").assertIsSelected()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("nav-schedule") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(previewY, schedule.fetchSemanticsNode().positionInRoot.y, 1f)
    }
}
