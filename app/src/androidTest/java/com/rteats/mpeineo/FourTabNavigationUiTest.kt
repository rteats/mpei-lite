package com.rteats.mpeineo

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rteats.mpeineo.ui.AppTab
import com.rteats.mpeineo.ui.FourTabNavigationBar
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FourTabNavigationUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fourDestinationsSwitchDirectlyWithoutHome() {
        val selected = mutableStateOf(AppTab.SCHEDULE)
        rule.setContent {
            MpeiNeoTheme {
                FourTabNavigationBar(selected.value, unreadMail = false) {
                    selected.value = it
                }
            }
        }
        rule.onNodeWithTag("four-tab-navigation").assertIsDisplayed()
        rule.onNodeWithTag("tab-schedule").assertIsDisplayed()
        rule.onNodeWithTag("tab-bars").performClick()
        rule.runOnIdle { assertEquals(AppTab.BARS, selected.value) }
        rule.onNodeWithTag("tab-mail").performClick()
        rule.runOnIdle { assertEquals(AppTab.MAIL, selected.value) }
        rule.onNodeWithTag("tab-maps").performClick()
        rule.runOnIdle { assertEquals(AppTab.MAPS, selected.value) }
        rule.onNodeWithTag("tab-schedule").performClick()
        rule.runOnIdle { assertEquals(AppTab.SCHEDULE, selected.value) }
    }
}
