package com.rteats.mpeineo.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class MailDetailOverlayUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun committedPredictiveBackDoesNotAnimateTheOverlayOutAgain() {
        val backCalls = AtomicInteger()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            var detailOpen by remember { mutableStateOf(true) }
            MpeiNeoTheme {
                Box(Modifier.fillMaxSize()) {
                    Text("Inbox behind detail")
                    MailDetailOverlay(
                        visible = detailOpen,
                        enabled = true,
                        onBack = {
                            backCalls.incrementAndGet()
                            detailOpen = false
                        },
                    ) { Text("Message detail") }
                }
            }
        }
        rule.onNodeWithText("Message detail").assertIsDisplayed()
        rule.runOnUiThread {
            val dispatcher = rule.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT, 0L))
            dispatcher.dispatchOnBackProgressed(
                BackEventCompat(200f, 0f, 0.8f, BackEventCompat.EDGE_LEFT, 16L))
            dispatcher.onBackPressed()
        }
        repeat(120) {
            if (backCalls.get() == 0) rule.mainClock.advanceTimeByFrame()
        }
        assertEquals(1, backCalls.get())
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Message detail").assertDoesNotExist()
        rule.onNodeWithText("Inbox behind detail").assertIsDisplayed()
    }
}
