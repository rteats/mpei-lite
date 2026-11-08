package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.rteats.mpeineo.MpeiNeoApplication
import androidx.compose.ui.unit.dp

/** The BARS screen never creates a WebView or executes university JavaScript. */
@Composable
internal fun BarsScreen(
    viewModel: BarsViewModel,
    autoRefreshEnabled: Boolean = true,
    active: Boolean = true,
    allowBackgroundSync: Boolean = false,
    onBrowserExit: () -> Unit = {},
    onAuthBrowserExit: () -> Unit = {},
    onSearch: () -> Unit = {},
    onDetailOpenChange: (Boolean) -> Unit = {},
    reselectRequest: Int = 0,
    openDisciplineName: String? = null,
    openDisciplineRequest: Int = 0,
    onOpenDisciplineConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as MpeiNeoApplication
    val auth: BarsNativeAuthViewModel = composeViewModel(
        factory = BarsNativeAuthViewModel.factory(viewModel, app.container.diagnostics, app),
    )
    val data by viewModel.state.collectAsState()
    val login by auth.status.collectAsState()
    LaunchedEffect(active) {
        if (active) auth.start()
    }
    LaunchedEffect(login.forceLogin, data.disciplines.isEmpty()) {
        if (login.forceLogin || data.disciplines.isEmpty()) {
            onDetailOpenChange(false)
        }
    }

    Box(modifier.fillMaxSize().testTag("bars-screen"),
        contentAlignment = Alignment.TopCenter) {
        if (login.forceLogin || data.disciplines.isEmpty()) {
            BarsCredentialsForm(
                status = login,
                onLogin = auth::login,
                onRetrySavedLogin = auth::retrySavedLogin,
                onForgetSavedLogin = auth::forgetSavedLogin,
                onCode = auth::confirm,
                onRequestCode = auth::sendCode,
                onRetry = { auth.openLogin(force = true) },
                onRetrySession = auth::retrySession,
                onCancel = if (data.disciplines.isNotEmpty()) auth::cancelLogin else null,
            )
        } else {
            BarsNativeDashboard(
                state = data, active = active,
                onDetailOpenChange = onDetailOpenChange,
                reselectRequest = reselectRequest,
                openDisciplineName = openDisciplineName,
                openDisciplineRequest = openDisciplineRequest,
                onOpenDisciplineConsumed = onOpenDisciplineConsumed,
                showCachedRefreshErrors = autoRefreshEnabled,
                onRefresh = auth::refresh,
                onOpenBrowser = { auth.openLogin(force = true) },
                onAcknowledgeGrades = viewModel::acknowledgeGradeChanges,
                onRefreshSummary = auth::refreshSummary,
                onSearch = onSearch,
            )
        }
    }
}

/** Legacy pure helper used by existing tests. No WebView is initialized. */
internal fun shouldInitializeBarsWebView(
    connectionRequired: Boolean,
    hasWebView: Boolean,
    active: Boolean,
    backgroundAllowed: Boolean,
): Boolean = connectionRequired && (hasWebView || active || backgroundAllowed)

@Composable
private fun BarsCredentialsForm(
    status: BarsNativeStatus,
    onLogin: (String, String, Boolean) -> Unit,
    onRetrySavedLogin: () -> Unit,
    onForgetSavedLogin: () -> Unit,
    onCode: (String) -> Unit,
    onRequestCode: () -> Unit,
    onRetry: () -> Unit,
    onRetrySession: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var rememberLogin by remember { mutableStateOf(true) }

    UniversityLoginLayout(
        title = "Вход в БАРС",
        subtitle = "Используйте логин и пароль учётной записи МЭИ.",
        modifier = Modifier.fillMaxHeight(),
    ) {
        if (status.step != BarsNativeStep.LOGIN) {
            status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            status.notice?.let { Text(it) }
        }
        when (status.step) {
            BarsNativeStep.IDLE, BarsNativeStep.OPENING, BarsNativeStep.SUBMITTING,
            BarsNativeStep.REFRESHING -> Box(
                Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            BarsNativeStep.RESTORE_FAILED -> {
                Text("Не удалось проверить предыдущую сессию.")
                Button(onClick = onRetrySession, modifier = Modifier.fillMaxWidth()) {
                    Text("Проверить ещё раз")
                }
                TextButton(onClick = onRetry) { Text("Войти заново") }
            }
            BarsNativeStep.LOGIN -> {
                UniversityCredentialFields(
                    account = account, onAccountChange = { account = it },
                    password = password, onPasswordChange = { password = it },
                    rememberLogin = rememberLogin,
                    onRememberLoginChange = { rememberLogin = it },
                    securityNote = "Пароль защищён Android Keystore. Для БАРС также потребуется код подтверждения.",
                    busy = false,
                    savedLoginAvailable = status.savedLoginAvailable,
                    onRetrySavedLogin = onRetrySavedLogin,
                    onForgetSavedLogin = onForgetSavedLogin,
                    onSubmit = {
                        if (account.isNotBlank() && password.isNotBlank()) {
                            val submitted = password
                            password = ""
                            onLogin(account, submitted, rememberLogin)
                        }
                    },
                    secondaryLabel = "Начать заново",
                    onSecondaryAction = onRetry,
                    tags = UniversityLoginTags(
                        account = "bars-native-account",
                        password = "bars-native-password",
                        remember = "bars-remember-login",
                        retrySaved = "bars-retry-saved-login",
                        forgetSaved = "bars-forget-saved-login",
                        submit = "bars-native-login",
                        secondary = "bars-start-again",
                    ),
                    error = status.error,
                    notice = status.notice,
                )
            }
            BarsNativeStep.CODE -> {
                Text("Введите код подтверждения из сообщения MAX.")
                OutlinedTextField(
                    value = code, onValueChange = { code = it },
                    label = { Text("Код подтверждения") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("bars-native-code"),
                )
                Button(onClick = {
                    val submitted = code
                    code = ""
                    onCode(submitted)
                }, enabled = code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("bars-native-confirm"),
                ) { Text("Подтвердить") }
                TextButton(onClick = onRequestCode, enabled = !status.busySending) {
                    Text(barsCodeSendActionLabel(status))
                }
            }
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel) { Text("К сохранённым оценкам") }
        }
    }
}
