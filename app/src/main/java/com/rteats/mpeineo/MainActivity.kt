package com.rteats.mpeineo

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rteats.mpeineo.data.InterfacePreferences
import com.rteats.mpeineo.ui.MainViewModel
import com.rteats.mpeineo.ui.MpeiNeoApp
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme

data class MailNotificationTarget(val uid: Long, val validity: Long, val requestId: Int)

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_MAIL = "open_mpei_mail"
        const val EXTRA_MAIL_UID = "open_mpei_mail_uid"
        const val EXTRA_MAIL_VALIDITY = "open_mpei_mail_uid_validity"
    }

    private var nextMailRequestId = 0
    private var mailNotificationTarget by mutableStateOf<MailNotificationTarget?>(null)

    private fun readMailTarget(launchIntent: Intent?) {
        if (launchIntent?.getBooleanExtra(EXTRA_OPEN_MAIL, false) != true) return
        mailNotificationTarget = MailNotificationTarget(
            uid = launchIntent.getLongExtra(EXTRA_MAIL_UID, -1L),
            validity = launchIntent.getLongExtra(EXTRA_MAIL_VALIDITY, -1L),
            requestId = ++nextMailRequestId,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readMailTarget(intent)
    }
    override fun onStop() {
        // Cancel a capture if Android backgrounds the Activity; otherwise
        // unrelated background frames could skew the measurement.
        (application as MpeiNeoApplication).container.uiFrameProfiler.stop("activity_background")
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Use Android's normal splash screen without an artificial delay.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        readMailTarget(intent)
        setContent {
            val interfacePrefs = remember { InterfacePreferences(applicationContext) }
            var theme by remember { mutableStateOf(interfacePrefs.theme) }
            MpeiNeoTheme(theme = theme) {
                val application = application as MpeiNeoApplication
                val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(application.container))
                Box(Modifier.fillMaxSize()) {
                    MpeiNeoApp(
                        viewModel,
                        initialMailTab = intent.getBooleanExtra(EXTRA_OPEN_MAIL, false),
                        mailNotificationTarget = mailNotificationTarget,
                        interfaceTheme = theme,
                        onInterfaceThemeChange = { next ->
                            if (theme != next) {
                                interfacePrefs.setTheme(next)
                                theme = next
                                application.container.diagnostics.log("UI_PREF", "theme=" + next.name)
                            }
                        },
                    )
                    SystemNavigationBarProtection(Modifier.matchParentSize())
                }
            }
        }
    }

    @Composable
    private fun SystemNavigationBarProtection(modifier: Modifier) {
        val color = MaterialTheme.colorScheme.surfaceContainer
        // Gesture navigation reports no tappable strip. Older releases only have button navigation.
        val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WindowInsets.tappableElement
        } else WindowInsets.navigationBars
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val bottom = insets.getBottom(density).toFloat()
        val left = insets.getLeft(density, direction).toFloat()
        val right = insets.getRight(density, direction).toFloat()
        val hasButtons = bottom > 0f || left > 0f || right > 0f
        val darkIcons = if (hasButtons) color.luminance() > 0.179f else !isSystemInDarkTheme()

        SideEffect {
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = darkIcons
        }
        if (hasButtons) {
            Canvas(modifier) {
                drawRect(color, size = Size(left, size.height))
                drawRect(color, Offset(size.width - right, 0f), Size(right, size.height))
                drawRect(color, Offset(0f, size.height - bottom), Size(size.width, bottom))
            }
        }
    }
}
