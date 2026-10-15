package com.rteats.mpeineo.ui

import androidx.lifecycle.ViewModel
import android.net.Uri
import android.provider.OpenableColumns
import com.rteats.mpeineo.data.OutgoingMail
import com.rteats.mpeineo.data.OutgoingFile
import com.rteats.mpeineo.data.OwaComposeProtocol
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rteats.mpeineo.data.MailAttachment
import com.rteats.mpeineo.data.MailCredentials
import com.rteats.mpeineo.data.MailDetail
import com.rteats.mpeineo.data.MailRepository
import com.rteats.mpeineo.data.OwaAuthProbe
import com.rteats.mpeineo.data.OwaMailRepository
import com.rteats.mpeineo.data.MailNotificationScheduler
import com.rteats.mpeineo.data.MailInboxSyncEvents
import com.rteats.mpeineo.data.MailSummary
import com.rteats.mpeineo.data.MailServerCertificate
import com.rteats.mpeineo.data.markMailRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class MailUiState(
    val configured: Boolean = false,
    val loginRequired: Boolean = false,
    val certificate: MailServerCertificate? = null,
    val inspectingCertificate: Boolean = false,
    val username: String = "",
    val loading: Boolean = false,
    val items: List<MailSummary> = emptyList(),
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextPageOffset: Int = 0,
    val moreError: Boolean = false,
    val selected: MailSummary? = null,
    val message: MailDetail? = null,
    val loadingMessage: Boolean = false,
    val savingAttachment: List<Int>? = null,
    val notice: String? = null,
    val error: String? = null,
    val composing: Boolean = false,
    val draft: OutgoingMail = OutgoingMail(),
    val outgoingFiles: List<OutgoingFile> = emptyList(),
    val sending: Boolean = false,
    val sendStage: String? = null,
    val sendError: String? = null,
)

/** Preserve OWA's server-provided newest-first order and avoid duplicate rows
 *  when a conversation shifts across page boundaries during a new delivery. */
internal fun mergeOwaInboxPages(
    existing: List<MailSummary>,
    older: List<MailSummary>,
): List<MailSummary> = (existing + older).distinctBy { it.uidValidity to it.uid }

private const val MAIL_PAGE_SIZE = 60

/** Native mail reads and refreshes exclusively through OWA. */
internal class MailViewModel(
    private val repository: MailRepository,
    private val owaAuthProbe: OwaAuthProbe,
    private val owaMailRepository: OwaMailRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MailUiState())
    val state: StateFlow<MailUiState> = _state.asStateFlow()
    private var sessionVersion = 0L
    private var refreshSequence = 0L
    private var pendingNotification: Pair<Long, Long>? = null

    init {
        val cached = owaMailRepository.cachedInbox()
        if (cached.isNotEmpty()) {
            _state.update {
                it.copy(configured = true, items = cached,
                    hasMore = cached.size >= MAIL_PAGE_SIZE, nextPageOffset = cached.size)
            }
        }
        // WorkManager uses a separate repository instance. Its successful OWA
        // poll already writes the shared encrypted cache; reflect the new first
        // page in memory rather than re-fetching the same 60 conversations.
        viewModelScope.launch {
            MailInboxSyncEvents.updates.collect { update ->
                if (update.account != owaMailRepository.cachedAccountKey()) return@collect
                val version = sessionVersion
                _state.update { old ->
                    if (version != sessionVersion) return@update old
                    val previousKeys = old.items.mapTo(HashSet()) {
                        it.uidValidity to it.uid
                    }
                    val newAtHead = update.firstPage.count {
                        (it.uidValidity to it.uid) !in previousKeys
                    }
                    old.copy(
                        configured = true,
                        items = mergeOwaInboxPages(update.firstPage, old.items),
                        nextPageOffset = if (old.nextPageOffset > 0)
                            maxOf(old.nextPageOffset + newAtHead, update.firstPage.size)
                        else update.firstPage.size,
                        hasMore = old.hasMore || (old.items.isEmpty() &&
                            update.firstPage.size == MAIL_PAGE_SIZE),
                    )
                }
                repository.logState("OWA worker cache observed count=${update.firstPage.size}")
            }
        }
        refresh()
    }

    fun refresh() {
        if (_state.value.loading || _state.value.loadingMore) return
        val version = sessionVersion
        val id = ++refreshSequence
        repository.logState("refresh requested id=$id protocol=OWA cachedItems=${_state.value.items.size}")
        viewModelScope.launch {
            _state.update {
                it.copy(loading = true, error = null, notice = null, loginRequired = false)
            }
            try {
                val items = owaMailRepository.inbox(maxConversations = MAIL_PAGE_SIZE, refreshId = id)
                if (version != sessionVersion) return@launch
                repository.credentials.clear()
                repository.logState("refresh success id=$id protocol=OWA items=${items.size}")
                _state.update { old ->
                    val preserved = mergeOwaInboxPages(items, old.items)
                    val seenBefore = old.items.mapTo(mutableSetOf()) {
                        it.uidValidity to it.uid
                    }
                    val addedAtHead = items.count {
                        (it.uidValidity to it.uid) !in seenBefore
                    }
                    old.copy(
                        configured = true,
                        loginRequired = false,
                        loading = false,
                        items = preserved,
                        nextPageOffset = if (old.nextPageOffset > 0) {
                            maxOf(old.nextPageOffset + addedAtHead, items.size)
                        } else items.size,
                        hasMore = if (old.items.size >= MAIL_PAGE_SIZE && !old.hasMore) {
                            false
                        } else items.size == MAIL_PAGE_SIZE,
                        loadingMore = false,
                        moreError = false,
                    )
                }
                pendingNotification?.let { target ->
                    pendingNotification = null
                    items.firstOrNull { it.uid == target.first && it.uidValidity == target.second }
                        ?.let(::open)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (version != sessionVersion) return@launch
                repository.logState("refresh failure id=$id protocol=OWA type=${failure.javaClass.simpleName}")
                val needsLogin = failure is OwaMailRepository.LoginRequired
                _state.update {
                    it.copy(
                        configured = it.items.isNotEmpty() || (!needsLogin && it.configured),
                        loginRequired = needsLogin,
                        loading = false,
                        error = if (needsLogin) "Требуется повторный вход в ОСЭП."
                            else "Не удалось обновить почту через ОСЭП.",
                        // A failed refresh has one recoverable status, not a
                        // second cached-data notice below the error.
                        notice = null,
                    )
                }
            }
        }
    }

    /** Demand-driven older conversation pages. Only first page is persisted
     *  in the existing encrypted inbox cache, keeping it small and recent. */
    fun loadMore() {
        val old = _state.value
        if (old.loading || old.loadingMore || !old.hasMore || old.items.isEmpty()) return
        val offset = old.nextPageOffset
        val version = sessionVersion
        _state.update { it.copy(loadingMore = true, moreError = false) }
        viewModelScope.launch {
            try {
                val older = owaMailRepository.inbox(
                    maxConversations = MAIL_PAGE_SIZE, offset = offset,
                )
                if (version != sessionVersion ||
                    _state.value.nextPageOffset != offset) return@launch
                _state.update {
                    it.copy(
                        loadingMore = false,
                        items = mergeOwaInboxPages(it.items, older),
                        hasMore = older.size == MAIL_PAGE_SIZE,
                        nextPageOffset = offset + older.size,
                        moreError = false,
                    )
                }
                repository.logState("OWA page success offset=$offset returned=${older.size}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (version != sessionVersion) return@launch
                repository.logState("OWA page failure offset=$offset" +
                    " type=${failure.javaClass.simpleName}")
                _state.update { it.copy(loadingMore = false, moreError = true) }
            }
        }
    }

    fun open(summary: MailSummary) {
        val version = sessionVersion
        val cached = owaMailRepository.cachedMessage(summary)
        _state.update {
            it.copy(selected = summary, message = cached,
                loadingMessage = cached == null, error = null, notice = null)
        }
        viewModelScope.launch {
            try {
                val detail = owaMailRepository.message(summary)
                if (version != sessionVersion) return@launch
                _state.update { current ->
                    if (current.selected?.uid != summary.uid ||
                        current.selected?.uidValidity != summary.uidValidity) current
                    else current.copy(message = detail, loadingMessage = false,
                        notice = null)
                }
                if (summary.unread && _state.value.selected?.uid == summary.uid) {
                    try {
                        owaMailRepository.markRead(summary)
                        if (version == sessionVersion) {
                            _state.update { state ->
                                state.copy(
                                    items = com.rteats.mpeineo.data.markMailRead(
                                        state.items, summary.uid, summary.uidValidity),
                                    selected = state.selected?.let {
                                        if (it.uid == summary.uid && it.uidValidity == summary.uidValidity)
                                            it.copy(unread = false) else it
                                    },
                                )
                            }
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        repository.logState("OWA read_state failure type=${failure.javaClass.simpleName}" +
                            " category=" + (failure as? OwaMailRepository.ServiceFailure)?.category.orEmpty())
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (version != sessionVersion) return@launch
                repository.logState("OWA message failure type=${failure.javaClass.simpleName}")
                _state.update { current ->
                    if (current.selected?.uid != summary.uid) current
                    else current.copy(loadingMessage = false,
                        error = if (cached == null) "Не удалось открыть переписку через ОСЭП." else null,
                        notice = if (cached != null) "Показана сохранённая переписка" else null)
                }
            }
        }
    }

    fun openFromNotification(uid: Long, validity: Long) {
        if (uid <= 0 || validity <= 0) return
        _state.value.items.firstOrNull { it.uid == uid && it.uidValidity == validity }
            ?.let { open(it); return }
        pendingNotification = uid to validity
        refresh()
    }

    fun closeMessage() {
        _state.update { it.copy(selected = null, loadingMessage = false, notice = null, error = null) }
    }

    fun download(attachment: MailAttachment) {
        if (_state.value.savingAttachment != null) return
        val version = sessionVersion
        viewModelScope.launch {
            _state.update { it.copy(savingAttachment = attachment.partPath, error = null) }
            try {
                val file = owaMailRepository.downloadAttachment(attachment)
                if (version != sessionVersion) return@launch
                _state.update { it.copy(savingAttachment = null, notice = "Сохранено: " + file) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (version != sessionVersion) return@launch
                repository.logState("OWA attachment failure type=${failure.javaClass.simpleName}" +
                    " category=" + (failure as? OwaMailRepository.ServiceFailure)?.category.orEmpty())
                _state.update { it.copy(savingAttachment = null,
                    error = "Не удалось загрузить вложение через ОСЭП.") }
            }
        }
    }

    fun startCompose() {
        _state.update { it.copy(composing = true, sendError = null) }
    }

    /** Closing retains the unsent in-memory draft and selected file URIs. */
    fun closeCompose() {
        if (_state.value.sending) return
        _state.update { it.copy(composing = false, sendError = null) }
    }

    fun editDraft(change: (OutgoingMail) -> OutgoingMail) {
        _state.update {
            if (it.sending) it else it.copy(draft = change(it.draft), sendError = null)
        }
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
    }

    fun removeOutgoingFile(uri: Uri) {
        _state.update { old ->
            if (old.sending) old else old.copy(
                outgoingFiles = old.outgoingFiles.filterNot { it.uri == uri },
                sendError = null,
            )
        }
    }

    fun sendDraft() {
        val current = _state.value
        if (!current.composing || current.sending) return
        if (!OwaComposeProtocol.validRecipients(current.draft)) {
            _state.update { it.copy(sendError = "Проверьте адреса получателей.") }
            return
        }
        val version = sessionVersion
        _state.update { it.copy(sending = true, sendError = null, sendStage = "Подготовка…") }
        viewModelScope.launch {
            try {
                owaMailRepository.sendMail(current.draft, current.outgoingFiles) { stage ->
                    _state.update { old -> old.copy(sendStage = stage) }
                }
                if (version != sessionVersion) return@launch
                _state.update {
                    it.copy(
                        composing = false, draft = OutgoingMail(), outgoingFiles = emptyList(),
                        sending = false, sendStage = null, sendError = null,
                        notice = "Письмо отправлено",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (version != sessionVersion) return@launch
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

    fun logout() {
        sessionVersion++
        pendingNotification = null
        viewModelScope.coroutineContext.cancelChildren()
        MailNotificationScheduler.stopAndClear(repository.contextForNotifications)
        repository.credentials.clear()
        repository.forgetTrustedCertificate()
        _state.value = MailUiState()
        viewModelScope.launch {
            try { owaMailRepository.signOut() }
            catch (failure: Exception) {
                repository.logState("OWA local logout failed type=${failure.javaClass.simpleName}")
            }
        }
    }

    companion object {
        fun factory(
            repo: MailRepository,
            owaAuthProbe: OwaAuthProbe,
            owaMailRepository: OwaMailRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MailViewModel(repo, owaAuthProbe, owaMailRepository) as T
        }
    }
}
