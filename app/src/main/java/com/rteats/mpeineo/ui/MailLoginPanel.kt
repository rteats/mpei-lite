package com.rteats.mpeineo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

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
    Surface(
        modifier = modifier.widthIn(max = 520.dp).fillMaxWidth()
            .testTag("mail-native-login"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Вход в почту ОСЭП",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold)
            Text(
                "Введите данные учётной записи МЭИ.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = account, onValueChange = { account = it },
                label = { Text("Логин") },
                singleLine = true, enabled = !state.busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("mail-native-username"),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, enabled = !state.busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().testTag("mail-native-password"),
            )
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = rememberLogin,
                    onCheckedChange = { rememberLogin = it },
                    enabled = !state.busy,
                    modifier = Modifier.testTag("mail-remember-login"),
                )
                Text(
                    "Запомнить вход на устройстве",
                    modifier = Modifier.weight(1f)
                        .clickable { if (!state.busy) rememberLogin = !rememberLogin },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "Данные защищены Android Keystore. При выходе из аккаунта они удаляются.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.busy && state.usingSavedLogin) {
                Text("Входим с сохранёнными данными…")
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
            if (!state.busy && state.savedLoginAvailable) {
                TextButton(
                    onClick = { loginViewModel.retrySavedLogin(onAuthenticated) },
                    modifier = Modifier.testTag("mail-retry-saved-login"),
                ) { Text("Войти с сохранёнными данными") }
                TextButton(
                    onClick = loginViewModel::forgetSavedLogin,
                    modifier = Modifier.testTag("mail-forget-saved-login"),
                ) { Text("Забыть сохранённый пароль") }
            }
            Button(
                onClick = { submit() },
                enabled = !state.busy && account.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("mail-native-submit"),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else Text("Войти")
            }
            TextButton(
                onClick = onOpenBrowser,
                modifier = Modifier.fillMaxWidth().testTag("mail-native-browser-fallback"),
            ) {
                Text(if (state.browserRecommended) "Продолжить в браузере ОСЭП"
                    else "Войти через браузер ОСЭП")
            }
        }
    }
}
