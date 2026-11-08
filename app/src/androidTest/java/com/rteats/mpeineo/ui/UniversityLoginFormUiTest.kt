package com.rteats.mpeineo.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UniversityLoginFormUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun sharedCredentialsRequireBothFieldsAndRememberLabelToggles() {
        var submissions = 0
        composeRule.setContent {
            var account by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var rememberLogin by remember { mutableStateOf(true) }
            MpeiNeoTheme {
                UniversityLoginLayout(
                    title = "Вход в БАРС",
                    subtitle = "Используйте логин и пароль учётной записи МЭИ.",
                ) {
                    UniversityCredentialFields(
                        account = account, onAccountChange = { account = it },
                        password = password, onPasswordChange = { password = it },
                        rememberLogin = rememberLogin,
                        onRememberLoginChange = { rememberLogin = it },
                        securityNote = "Для БАРС также потребуется код подтверждения.",
                        busy = false, savedLoginAvailable = false,
                        onRetrySavedLogin = {}, onForgetSavedLogin = {},
                        onSubmit = { if (account.isNotBlank() && password.isNotBlank()) submissions++ },
                        secondaryLabel = "Начать заново", onSecondaryAction = {},
                        tags = UniversityLoginTags(
                            "account", "password", "remember", "retry", "forget",
                            "submit", "secondary",
                        ),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("submit").assertIsNotEnabled()
        composeRule.onNodeWithText("Запомнить вход на устройстве").performClick()
        composeRule.onNodeWithTag("remember").assertIsOff()
        composeRule.onNodeWithTag("account").performTextInput("student")
        composeRule.onNodeWithTag("submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("password").performTextInput("password")
        composeRule.onNodeWithTag("submit").assertIsEnabled()
        composeRule.onNodeWithTag("password").performImeAction()
        composeRule.runOnIdle { assertEquals(1, submissions) }
    }
}
