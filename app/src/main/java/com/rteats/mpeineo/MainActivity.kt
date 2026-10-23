package com.rteats.mpeineo

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rteats.mpeineo.ui.MainViewModel
import com.rteats.mpeineo.ui.MpeiNeoApp
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme
import com.rteats.mpeineo.update.UpdateRelaunchState
import com.rteats.mpeineo.data.InterfacePreferences

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
        // Android 12+ plays the five vector parts in the system splash window.
        // Keep that window visible just long enough to finish the sequence,
        // without adding any delay on ordinary activity resumes.
        val launchStart = android.os.SystemClock.uptimeMillis()
        val splash = installSplashScreen()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            android.animation.ValueAnimator.areAnimatorsEnabled()
        ) {
            splash.setKeepOnScreenCondition {
                android.os.SystemClock.uptimeMillis() - launchStart < 620L
            }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readMailTarget(intent)
        setContent {
            val interfacePrefs = remember { InterfacePreferences(applicationContext) }
            val restoredUpdateRoutes = remember {
                UpdateRelaunchState.consume(applicationContext, BuildConfig.VERSION_CODE)
            }
            var theme by remember { mutableStateOf(interfacePrefs.theme) }
            MpeiNeoTheme(theme = theme) {
                val application = application as MpeiNeoApplication
                val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(application.container))
                MpeiNeoApp(
                    viewModel,
                    initialMailTab = intent.getBooleanExtra(EXTRA_OPEN_MAIL, false),
                    restoredUpdateRoutes = restoredUpdateRoutes,
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
            }
        }
    }
}
