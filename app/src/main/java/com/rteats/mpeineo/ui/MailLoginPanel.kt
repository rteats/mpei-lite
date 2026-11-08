package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Native credentials, with an explicit browser escape hatch for unknown SSO flows. */
@Composable
internal fun MailLoginPanel(
    loginViewModel: MailLoginViewModel,
    onAuthenticated: () -> Unit,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by loginViewModel.status.collectAsState()
    LaunchedEffect(Unit) { loginViewModel.refreshSavedLoginState() }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberLogin by remember { mutableStateOf(true) }
    fun submit() {
        if (state.busy || account.isBlank() || password.isBlank()) return
        val submitted = password
        password = ""
        loginViewModel.login(account, submitted, rememberLogin, onAuthenticated)
    }
    Box(modifier.fillMaxWidth().testTag("mail-native-login"),
        contentAlignment = Alignment.TopCenter) {
        UniversityLoginLayout(
            title = "Вход в почту ОСЭП",
            subtitle = "Используйте логин и пароль учётной записи МЭИ.",
        ) {
            UniversityCredentialFields(
                account = account, onAccountChange = { account = it },
                password = password, onPasswordChange = { password = it },
                rememberLogin = rememberLogin,
                onRememberLoginChange = { rememberLogin = it },
                securityNote = "Данные защищены Android Keystore. При выходе из аккаунта они удаляются.",
                busy = state.busy,
                savedLoginAvailable = state.savedLoginAvailable,
                onRetrySavedLogin = { loginViewModel.retrySavedLogin(onAuthenticated) },
                onForgetSavedLogin = loginViewModel::forgetSavedLogin,
                onSubmit = ::submit,
                secondaryLabel = if (state.browserRecommended) "Продолжить в браузере ОСЭП"
                    else "Войти через браузер ОСЭП",
                onSecondaryAction = onOpenBrowser,
                tags = UniversityLoginTags(
                    account = "mail-native-username",
                    password = "mail-native-password",
                    remember = "mail-remember-login",
                    retrySaved = "mail-retry-saved-login",
                    forgetSaved = "mail-forget-saved-login",
                    submit = "mail-native-submit",
                    secondary = "mail-native-browser-fallback",
                ),
                error = state.error,
                busyMessage = if (state.usingSavedLogin) "Входим с сохранёнными данными…" else null,
            )
        }
    }
}
