package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

    Box(modifier.fillMaxSize().testTag("bars-screen")) {
        if (login.forceLogin || data.disciplines.isEmpty()) {
            BarsCredentialsForm(
                status = login,
                onLogin = auth::login,
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
    onLogin: (String, String) -> Unit,
    onCode: (String) -> Unit,
    onRequestCode: () -> Unit,
    onRetry: () -> Unit,
    onRetrySession: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Вход в БАРС", style = MaterialTheme.typography.headlineSmall)
        Text("Прямое соединение с bars.mpei.ru",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        status.notice?.let { Text(it) }
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
                OutlinedTextField(
                    value = account, onValueChange = { account = it },
                    label = { Text("Логин") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("bars-native-account"),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Пароль") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("bars-native-password"),
                )
                Button(onClick = {
                    val submitted = password
                    password = ""
                    onLogin(account, submitted)
                }, enabled = account.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("bars-native-login"),
                ) { Text("Войти") }
                TextButton(onClick = onRetry) { Text("Начать заново") }
            }
            BarsNativeStep.CODE -> {
                Text("Введите код подтверждения из МАКС.")
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
                    Text(if (status.busySending) "Отправка…" else "Отправить код через МАКС")
                }
            }
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel) { Text("К сохранённым оценкам") }
        }
    }
}
