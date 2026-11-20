package com.rteats.mpeineo

import android.content.res.Configuration
import android.view.WindowInsets
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.rteats.mpeineo.data.InterfacePreferences
import com.rteats.mpeineo.data.InterfaceTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class SystemNavigationBarUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun bottomSystemButtonsUseTheAppSurfaceColor() {
        val (buttonBarHeight, expectedColor) = composeRule.runOnIdle {
            val activity = composeRule.activity
            val insets = activity.window.decorView.rootWindowInsets
            val dark = when (InterfacePreferences(activity).theme) {
                InterfaceTheme.DARK -> true
                InterfaceTheme.LIGHT -> false
                InterfaceTheme.SYSTEM -> activity.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }
            val colors = if (dark) dynamicDarkColorScheme(activity) else dynamicLightColorScheme(activity)
            insets.getInsets(WindowInsets.Type.tappableElement()).bottom to colors.surfaceContainer.toArgb()
        }
        assumeTrue("Requires a visible bottom button navigation bar", buttonBarHeight > 0)

        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        // Sample inside the system strip, away from Back, Home and Recents.
        val background = screenshot.getPixel(screenshot.width / 12, screenshot.height - buttonBarHeight / 2)
        assertEquals("System navigation background", expectedColor, background)
    }
}
