package com.rteats.mpeineo.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.rteats.mpeineo.data.MailDraftSnapshot
import com.rteats.mpeineo.data.MailRepository
import com.rteats.mpeineo.data.OwaMailFolder
import com.rteats.mpeineo.data.OwaMailRepository
import com.rteats.mpeineo.data.OwaComposeProtocol
import com.rteats.mpeineo.data.OwaRecipientDirectory
import com.rteats.mpeineo.data.OwaRecipientSuggestion
import com.rteats.mpeineo.data.OutgoingFile
import com.rteats.mpeineo.data.OutgoingMail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Pure recipient mutations shared by editing, lookup and suggestions. */
internal fun mailRecipientValue(draft: OutgoingMail, field: RecipientField): String = when (field) {
    RecipientField.TO -> draft.to
    RecipientField.CC -> draft.cc
    RecipientField.BCC -> draft.bcc
}

internal fun mailWithRecipient(
    draft: OutgoingMail,
    field: RecipientField,
    value: String,
): OutgoingMail = when (field) {
    RecipientField.TO -> draft.copy(to = value)
    RecipientField.CC -> draft.copy(cc = value)
    RecipientField.BCC -> draft.copy(bcc = value)
}

/**
 * Owns Mail composition, recipient suggestions, attachment permissions and
 * encrypted draft persistence. Inbox and Sent synchronization remain owned by
 * MailViewModel. A shared StateFlow preserves the existing screen contract.
 */
internal class MailComposeController(
    private val _state: MutableStateFlow<MailUiState>,
    private val repository: MailRepository,
    private val owaMailRepository: OwaMailRepository,
    private val scope: CoroutineScope,
    private val sessionVersion: () -> Long,
    private val onRefreshSent: () -> Unit,
) {
    private var recipientJob: Job? = null
    private var draftWriteJob: Job? = null
    private var draftAccountKey: String? = owaMailRepository.cachedAccountKey()

    fun restoreCachedDraft() {
        owaMailRepository.cachedDraft()?.let { saved ->
            _state.update { it.copy(draft = saved.mail, outgoingFiles = saved.files) }
        }
    }

    /** Never log message contents or recipient addresses. */
    private fun scheduleDraftSave() {
        draftWriteJob?.cancel()
        val snapshot = MailDraftSnapshot(_state.value.draft, _state.value.outgoingFiles)
        val accountAtEdit = draftAccountKey
        draftWriteJob = scope.launch {
            delay(300)
            withContext(Dispatchers.IO) {
                owaMailRepository.saveDraft(snapshot, expectedAccount = accountAtEdit)
            }
        }
    }

    fun restoreDraftForActiveAccount() {
        val currentAccount = owaMailRepository.cachedAccountKey()
        if (currentAccount == draftAccountKey) return
        draftWriteJob?.cancel()
        draftAccountKey = currentAccount
        val restored = owaMailRepository.cachedDraft() ?: MailDraftSnapshot()
        _state.update { it.copy(draft = restored.mail, outgoingFiles = restored.files) }
    }

    private fun releaseFilePermission(uri: Uri) {
        runCatching {
            repository.contextForNotifications.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun startCompose() {
        _state.update { it.copy(composing = true, sendError = null, notice = null,
            activeRecipientField = null, recipientSuggestions = emptyList()) }
    }

    /** Closing keeps the encrypted, account-scoped unsent draft. */
    fun closeCompose() {
        if (_state.value.sending) return
        recipientJob?.cancel()
        _state.update { it.copy(composing = false, sendError = null,
            activeRecipientField = null, recipientSuggestions = emptyList(),
            recipientSearching = false) }
        scheduleDraftSave()
    }

    fun editDraft(change: (OutgoingMail) -> OutgoingMail) {
        if (_state.value.sending) return
        _state.update { it.copy(draft = change(it.draft), sendError = null) }
        scheduleDraftSave()
    }

    /** Debounced and cancelable: search only the unfinished recipient token. */
    fun editRecipient(field: RecipientField, value: String) {
        editDraft { draft -> mailWithRecipient(draft, field, value) }
        recipientJob?.cancel()
        val query = OwaRecipientDirectory.token(value)
        if (query.length < 2 || !_state.value.composing) {
            _state.update { it.copy(activeRecipientField = null,
                recipientSuggestions = emptyList(), recipientSearching = false) }
            return
        }
        val version = sessionVersion()
        _state.update { it.copy(activeRecipientField = field,
            recipientSearching = true, recipientSuggestions = emptyList()) }
        recipientJob = scope.launch {
            delay(260)
            val cached = (_state.value.items + _state.value.sentItems).map { it.sender }
            val fallback = OwaRecipientDirectory.cachedAddresses(query, cached)
            val found = try {
                owaMailRepository.findRecipients(query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                repository.logState("OWA recipient lookup unavailable type=${failure.javaClass.simpleName}")
                emptyList()
            }
            if (sessionVersion() != version) return@launch
            _state.update { current ->
                val actual = mailRecipientValue(current.draft, field)
                if (!current.composing || current.activeRecipientField != field ||
                    OwaRecipientDirectory.token(actual) != query) current
                else current.copy(recipientSuggestions = (found + fallback)
                    .distinctBy { it.email.lowercase() }.take(8), recipientSearching = false)
            }
        }
    }

    fun acceptRecipient(field: RecipientField, suggestion: OwaRecipientSuggestion) {
        recipientJob?.cancel()
        editDraft { draft ->
            mailWithRecipient(
                draft, field,
                OwaRecipientDirectory.replaceToken(mailRecipientValue(draft, field), suggestion.email),
            )
        }
        _state.update { it.copy(activeRecipientField = null,
            recipientSuggestions = emptyList(), recipientSearching = false) }
    }

    fun addOutgoingFiles(uris: List<Uri>) {
        if (_state.value.sending) return
        val resolver = repository.contextForNotifications.contentResolver
        val files = uris.distinct().mapNotNull { uri ->
            try {
                var name = "attachment"
                var size = -1L
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameColumn >= 0) name = cursor.getString(nameColumn) ?: name
                        if (sizeColumn >= 0 && !cursor.isNull(sizeColumn))
                            size = cursor.getLong(sizeColumn)
                    }
                }
                OutgoingFile(
                    uri = uri,
                    name = name.substringAfterLast('/').substringAfterLast('\\')
                        .replace(Regex("[\\u0000-\\u001f]"), "").take(160)
                        .ifBlank { "attachment" },
                    mimeType = resolver.getType(uri) ?: "application/octet-stream",
                    size = size,
                )
            } catch (_: Exception) { null }
        }
        _state.update { old ->
            if (old.sending) old else {
                val combined = (old.outgoingFiles + files).distinctBy { it.uri }
                old.copy(
                    outgoingFiles = if (combined.size <= 8) combined else old.outgoingFiles,
                    sendError = when {
                        files.size != uris.distinct().size -> "Не удалось прочитать часть вложений."
                        combined.size > 8 -> "Можно прикрепить не более 8 файлов."
                        combined.any { it.size > 10L * 1024 * 1024 } ->
                            "Размер одного вложения не должен превышать 10 МБ."
                        else -> null
                    },
                )
            }
        }
        // OpenMultipleDocuments grants persistable read permission. Retain only
        // accepted attachments; keep them accessible after a process restart.
        val accepted = _state.value.outgoingFiles.map { it.uri }.toSet()
        files.filter { it.uri in accepted }.forEach { file ->
            runCatching {
                resolver.takePersistableUriPermission(
                    file.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        scheduleDraftSave()
    }

    fun removeOutgoingFile(uri: Uri) {
        if (_state.value.sending) return
        _state.update { old -> old.copy(
            outgoingFiles = old.outgoingFiles.filterNot { it.uri == uri },
            sendError = null,
        ) }
        releaseFilePermission(uri)
        scheduleDraftSave()
    }

    fun discardDraft() {
        if (_state.value.sending) return
        draftWriteJob?.cancel()
        val previous = _state.value.outgoingFiles
        _state.update { it.copy(
            draft = OutgoingMail(),
            outgoingFiles = emptyList(),
            sendError = null,
        ) }
        previous.forEach { releaseFilePermission(it.uri) }
        draftWriteJob = scope.launch {
            withContext(Dispatchers.IO) { owaMailRepository.clearDraft() }
        }
    }

    fun sendDraft() {
        val current = _state.value
        if (!current.composing || current.sending) return
        if (!OwaComposeProtocol.validRecipients(current.draft)) {
            _state.update { it.copy(sendError = "Проверьте адреса получателей.") }
            return
        }
        val version = sessionVersion()
        _state.update { it.copy(sending = true, sendError = null, sendStage = "Подготовка…") }
        scope.launch {
            try {
                val linkedSourceUris = owaMailRepository.sendMail(
                    current.draft, current.outgoingFiles,
                ) { stage ->
                    _state.update { old -> old.copy(sendStage = stage) }
                }
                if (version != sessionVersion()) return@launch
                draftWriteJob?.cancel()
                withContext(Dispatchers.IO) { owaMailRepository.clearDraft() }
                // Keep the original SAF grant for a source confirmed by Exchange,
                // so the sent message can open it without another download.
                current.outgoingFiles.filterNot { it.uri in linkedSourceUris }
                    .forEach { releaseFilePermission(it.uri) }
                _state.update {
                    it.copy(
                        composing = false, draft = OutgoingMail(), outgoingFiles = emptyList(),
                        sentLoaded = false, activeRecipientField = null,
                        recipientSuggestions = emptyList(), recipientSearching = false,
                        sending = false, sendStage = null, sendError = null,
                        notice = "Письмо отправлено",
                    )
                }
                // If the user composed from Sent, show the new saved copy
                // without requiring another manual folder switch.
                if (_state.value.mailbox == OwaMailFolder.SENT) onRefreshSent()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (version != sessionVersion()) return@launch
                repository.logState("OWA send failed type=${failure.javaClass.simpleName}")
                _state.update {
                    it.copy(
                        sending = false, sendStage = null,
                        sendError = if (failure is OwaMailRepository.LoginRequired) {
                            "Сессия ОСЭП истекла. Войдите повторно; черновик сохранён."
                        } else if (failure is OwaMailRepository.ServiceFailure &&
                            failure.category in setOf("invalid_recipients", "message_too_long",
                                "attachment_limit", "attachment_too_large", "attachment_unavailable")) {
                            "Проверьте получателей и вложения. Черновик сохранён."
                        } else {
                            "Не удалось подтвердить отправку. Проверьте папку «Отправленные» " +
                                "в ОСЭП перед повторной попыткой."
                        },
                    )
                }
            }
        }
    }


    /** Called before logout invalidates the session and clears shared state. */
    fun cancelAndRelease() {
        recipientJob?.cancel()
        draftWriteJob?.cancel()
        _state.value.outgoingFiles.forEach { releaseFilePermission(it.uri) }
    }
}
