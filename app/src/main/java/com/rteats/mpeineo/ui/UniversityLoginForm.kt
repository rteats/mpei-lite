package com.rteats.mpeineo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** The same account form is used by mail and BARS; each service owns its session. */
internal data class UniversityLoginTags(
    val account: String,
    val password: String,
    val remember: String,
    val retrySaved: String,
    val forgetSaved: String,
    val submit: String,
    val secondary: String,
)

@Composable
internal fun UniversityLoginLayout(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.widthIn(max = 520.dp).fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
internal fun UniversityCredentialFields(
    account: String,
    onAccountChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    rememberLogin: Boolean,
    onRememberLoginChange: (Boolean) -> Unit,
    securityNote: String,
    busy: Boolean,
    savedLoginAvailable: Boolean,
    onRetrySavedLogin: () -> Unit,
    onForgetSavedLogin: () -> Unit,
    onSubmit: () -> Unit,
    secondaryLabel: String,
    onSecondaryAction: () -> Unit,
    tags: UniversityLoginTags,
    error: String? = null,
    notice: String? = null,
    busyMessage: String? = null,
) {
    OutlinedTextField(
        value = account, onValueChange = onAccountChange,
        label = { Text("Логин") }, singleLine = true, enabled = !busy,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag(tags.account),
    )
    OutlinedTextField(
        value = password, onValueChange = onPasswordChange,
        label = { Text("Пароль") }, singleLine = true, enabled = !busy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.fillMaxWidth().testTag(tags.password),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = rememberLogin,
            onCheckedChange = onRememberLoginChange,
            enabled = !busy,
            modifier = Modifier.testTag(tags.remember),
        )
        Text(
            "Запомнить вход на устройстве",
            modifier = Modifier.weight(1f)
                .clickable(enabled = !busy) { onRememberLoginChange(!rememberLogin) },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(securityNote, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (busy && busyMessage != null) Text(busyMessage)
    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error) }
    notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    if (!busy && savedLoginAvailable) {
        TextButton(onClick = onRetrySavedLogin,
            modifier = Modifier.testTag(tags.retrySaved)) {
            Text("Войти с сохранёнными данными")
        }
        TextButton(onClick = onForgetSavedLogin,
            modifier = Modifier.testTag(tags.forgetSaved)) {
            Text("Забыть сохранённый пароль")
        }
    }
    Button(
        onClick = onSubmit,
        enabled = !busy && account.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth().testTag(tags.submit),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        } else Text("Войти")
    }
    TextButton(
        onClick = onSecondaryAction,
        modifier = Modifier.fillMaxWidth().testTag(tags.secondary),
    ) { Text(secondaryLabel) }
}
