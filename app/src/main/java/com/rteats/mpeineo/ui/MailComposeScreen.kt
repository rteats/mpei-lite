package com.rteats.mpeineo.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.clickable
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.data.OwaComposeProtocol
import com.rteats.mpeineo.data.OwaRecipientSuggestion

/** Native message composer; unsent data is encrypted in account-scoped local storage. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MailComposeScreen(
    state: MailUiState,
    viewModel: MailViewModel,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    val hasDraft = draft.to.isNotBlank() || draft.cc.isNotBlank() || draft.bcc.isNotBlank() ||
        draft.subject.isNotBlank() || draft.body.isNotBlank() || state.outgoingFiles.isNotEmpty()
    var askDiscard by remember { mutableStateOf(false) }
    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Удалить черновик?") },
            text = { Text("Текст, получатели и вложения будут удалены с устройства.") },
            confirmButton = {
                TextButton(onClick = {
                    askDiscard = false
                    viewModel.discardDraft()
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { askDiscard = false }) { Text("Отмена") }
            },
        )
    }
    var showCopies by remember { mutableStateOf(draft.cc.isNotBlank() || draft.bcc.isNotBlank()) }
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.addOutgoingFiles(uris) }
    BackHandler(enabled = !state.sending) { viewModel.closeCompose() }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("mail-compose"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "compose-actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = viewModel::closeCompose, enabled = !state.sending) {
                    AppIcon(AppIcons.Back, contentDescription = "Свернуть черновик")
                }
                Text(
                    if (hasDraft) "Черновик" else "Новое письмо",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (hasDraft && !state.sending) {
                    TextButton(onClick = { askDiscard = true }) { Text("Удалить") }
                }
                Button(
                    onClick = viewModel::sendDraft,
                    enabled = !state.sending && OwaComposeProtocol.validRecipients(draft) &&
                        (draft.subject.isNotBlank() || draft.body.isNotBlank() ||
                            state.outgoingFiles.isNotEmpty()),
                ) {
                    Text("Отправить")
                }
            }
        }

        item(key = "compose-to") {
            RecipientEntry(
                value = draft.to, label = "Кому", field = RecipientField.TO,
                state = state, viewModel = viewModel,
                placeholder = "Адрес или имя получателя",
            )
        }
        item(key = "compose-copies-toggle") {
            TextButton(
                onClick = { showCopies = !showCopies },
                enabled = !state.sending,
            ) {
                Text(if (showCopies) "Скрыть копии" else "Добавить копию или скрытую копию")
            }
        }
        if (showCopies) {
            item(key = "compose-cc") {
                RecipientEntry(
                    value = draft.cc, label = "Копия", field = RecipientField.CC,
                    state = state, viewModel = viewModel,
                )
            }
            item(key = "compose-bcc") {
                RecipientEntry(
                    value = draft.bcc, label = "Скрытая копия", field = RecipientField.BCC,
                    state = state, viewModel = viewModel,
                )
            }
        }
        item(key = "compose-subject") {
            OutlinedTextField(
                value = draft.subject,
                onValueChange = { value -> viewModel.editDraft { it.copy(subject = value) } },
                label = { Text("Тема") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.sending,
            )
        }
        item(key = "compose-body") {
            OutlinedTextField(
                value = draft.body,
                onValueChange = { value -> viewModel.editDraft { it.copy(body = value) } },
                label = { Text("Сообщение") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 230.dp),
                minLines = 8,
                enabled = !state.sending,
            )
        }

        item(key = "compose-attach") {
            TextButton(
                onClick = { pickFiles.launch(arrayOf("*/*")) },
                enabled = !state.sending && state.outgoingFiles.size < 8,
            ) {
                AppIcon(AppIcons.Attachment, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Прикрепить файл")
            }
        }
        items(state.outgoingFiles, key = { it.uri.toString() }) { file ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(
                        AppIcons.Attachment,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (file.size >= 0) {
                            Text(
                                "${(file.size + 1023) / 1024} КБ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.removeOutgoingFile(file.uri) },
                        enabled = !state.sending,
                    ) {
                        AppIcon(AppIcons.ClearLog, contentDescription = "Удалить вложение")
                    }
                }
            }
        }
        if (state.sending) {
            item(key = "compose-progress") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ContainedLoadingIndicator(modifier = Modifier.size(32.dp))
                    Text(
                        state.sendStage ?: "Отправка…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        state.sendError?.let { error ->
            item(key = "compose-error") {
                ServiceStatus(
                    title = "Не удалось отправить письмо",
                    detail = error,
                    tone = ServiceStatusTone.ERROR,
                )
            }
        }
        item(key = "compose-help") {
            Text(
                "Черновик сохраняется на устройстве. Он доступен после перезапуска " +
                    "и удаляется после отправки или выхода из ОСЭП. " +
                    "Максимум 8 вложений, до 10 МБ каждое.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Suggestions live under the edited field and never cover the send action. */
@Composable
private fun RecipientEntry(
    value: String,
    label: String,
    field: RecipientField,
    state: MailUiState,
    viewModel: MailViewModel,
    placeholder: String = "",
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { viewModel.editRecipient(field, it) },
            label = { Text(label) },
            placeholder = if (placeholder.isNotBlank()) {
                { Text(placeholder) }
            } else null,
            modifier = Modifier.fillMaxWidth().testTag("recipient-" + field.name.lowercase()),
            enabled = !state.sending,
            maxLines = 3,
        )
        if (state.activeRecipientField == field && state.recipientSearching) {
            Text(
                "Поиск получателей…",
                modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.activeRecipientField == field && state.recipientSuggestions.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().testTag("recipient-suggestions"),
            ) {
                Column {
                    state.recipientSuggestions.forEach { candidate ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { viewModel.acceptRecipient(field, candidate) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AppIcon(AppIcons.Person, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (candidate.name != candidate.email) {
                                    Text(candidate.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
