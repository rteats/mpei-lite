package com.rteats.mpeineo.ui

import androidx.lifecycle.ViewModel
import android.net.Uri
import android.content.Intent
import android.provider.OpenableColumns
import com.rteats.mpeineo.data.OutgoingMail
import com.rteats.mpeineo.data.OutgoingFile
import com.rteats.mpeineo.data.MailDraftSnapshot
import com.rteats.mpeineo.data.OwaComposeProtocol
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rteats.mpeineo.data.MailAttachment
import com.rteats.mpeineo.data.MailDetail
import com.rteats.mpeineo.data.MailRepository
import com.rteats.mpeineo.data.OwaAuthProbe
import com.rteats.mpeineo.data.OwaMailRepository
import com.rteats.mpeineo.data.OwaMailFolder
import com.rteats.mpeineo.data.OwaRecipientSuggestion
import com.rteats.mpeineo.data.OwaRecipientDirectory
import com.rteats.mpeineo.data.MailNotificationScheduler
import com.rteats.mpeineo.data.MailInboxSyncEvents
import com.rteats.mpeineo.data.MailSummary
import com.rteats.mpeineo.data.markMailRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class RecipientField { TO, CC, BCC }

internal data class MailUiState(
    val configured: Boolean = false,
    val loginRequired: Boolean = false,
    val username: String = "",
    val loading: Boolean = false,
    val items: List<MailSummary> = emptyList(),
    val mailbox: OwaMailFolder = OwaMailFolder.INBOX,
    val sentItems: List<MailSummary> = emptyList(),
    val sentLoaded: Boolean = false,
    val sentLoading: Boolean = false,
    val sentLoadingMore: Boolean = false,
    val sentHasMore: Boolean = false,
    val sentNextPageOffset: Int = 0,
    val sentMoreError: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextPageOffset: Int = 0,
    val moreError: Boolean = false,
    val selected: MailSummary? = null,
    val message: MailDetail? = null,
    val loadingMessage: Boolean = false,
    val savingAttachment: List<Int>? = null,
    val localAttachmentUris: Map<String, Uri> = emptyMap(),
    val notice: String? = null,
    val error: String? = null,
    val composing: Boolean = false,
    val draft: OutgoingMail = OutgoingMail(),
    val outgoingFiles: List<OutgoingFile> = emptyList(),
    val sending: Boolean = false,
    val sendStage: String? = null,
    val sendError: String? = null,
    val activeRecipientField: RecipientField? = null,
    val recipientSuggestions: List<OwaRecipientSuggestion> = emptyList(),
    val recipientSearching: Boolean = false,
)

/** Preserve OWA's server-provided newest-first order and avoid duplicate rows
 *  when a conversation shifts across page boundaries during a new delivery. */
internal fun mergeOwaInboxPages(
    existing: List<MailSummary>,
    older: List<MailSummary>,
): List<MailSummary> = (existing + older).distinctBy { it.uidValidity to it.uid }

/** Return up to ten unresolved IDs, prioritizing rows currently on screen. */
internal fun prioritizedSentIds(
    unresolved: List<Long>,
    visible: Set<Long>,
    batchSize: Int = 10,
): List<Long> = unresolved.distinct().let { unique ->
    // The caller preserves viewport order in its LinkedHashSet. A user sees
    // those rows before older unresolved messages, regardless of mail order.
    (visible.filter { it in unique } + unique.filterNot { it in visible })
        .take(batchSize.coerceAtLeast(0))
}

/** Preserve previously fetched recipients when a refreshed sent page omits To. */
internal fun mergeOwaSentPages(
    incoming: List<MailSummary>,
    cached: List<MailSummary>,
): List<MailSummary> {
    val byId = cached.associateBy { it.uidValidity to it.uid }
    return (incoming + cached).distinctBy { it.uidValidity to it.uid }.map { message ->
        if (!message.recipients.isNullOrEmpty()) message
        else byId[message.uidValidity to message.uid]?.recipients
            ?.takeIf { it.isNotEmpty() }?.let { message.copy(recipients = it) } ?: message
    }
}

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
    private var sentRecipientJob: Job? = null
    private var sentVisible = false
    private var visibleSentUids = emptySet<Long>()
    private val compose = MailComposeController(
        _state = _state,
        repository = repository,
        owaMailRepository = owaMailRepository,
        scope = viewModelScope,
        sessionVersion = { sessionVersion },
        onRefreshSent = { refreshSent() },
    )

    /** Optional enrichment never steals network bandwidth from another service. */
    fun setSentVisible(visible: Boolean) {
        if (sentVisible == visible) return
        sentVisible = visible
        if (!visible) {
            sentRecipientJob?.cancel()
            sentRecipientJob = null
            visibleSentUids = emptySet()
            repository.logState("OWA sent enrichment paused offscreen")
        } else if (!_state.value.sentLoading &&
            _state.value.sentItems.any { it.recipients.isNullOrEmpty() }) {
            repository.logState("OWA sent enrichment resumed items=${_state.value.sentItems.size}")
            hydrateSentRecipients()
        }
    }

    /** Nonprivate identifiers are kept in memory, but never written to logs. */
    fun updateVisibleSentUids(ids: Set<Long>) {
        visibleSentUids = ids
    }

    private fun hydrateSentRecipients() {
        if (!sentVisible) return
        sentRecipientJob?.cancel()
        val version = sessionVersion
        sentRecipientJob = viewModelScope.launch {
            val unsuccessful = HashSet<Long>()
            while (sentVisible && version == sessionVersion) {
                val pending = _state.value.sentItems.filter {
                    it.recipients.isNullOrEmpty() && !it.owaConversationId.isNullOrBlank() &&
                        it.uid !in unsuccessful
                }
                val byUid = pending.associateBy { it.uid }
                val batch = prioritizedSentIds(pending.map { it.uid }, visibleSentUids)
                    .mapNotNull(byUid::get)
                if (batch.isEmpty()) break
                val resolved = try {
                    owaMailRepository.sentRecipientNames(batch)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    repository.logState("OWA sent recipients unavailable type=" +
                        failure.javaClass.simpleName)
                    emptyMap()
                }
                if (version != sessionVersion || !sentVisible) return@launch
                unsuccessful.addAll(batch.map { it.uid }.filter { it !in resolved })
                if (resolved.isNotEmpty()) {
                    _state.update { current ->
                        current.copy(sentItems = current.sentItems.map { item ->
                            resolved[item.uid]?.let { item.copy(recipients = it) } ?: item
                        })
                    }
                }
                repository.logState(
                    "OWA sent enrichment batch=${batch.size} resolved=${resolved.size}" +
                        " remaining=${_state.value.sentItems.count { it.recipients.isNullOrEmpty() }}",
                )
            }
        }
    }

    private var initialRefreshStarted = false

    init {
        compose.restoreCachedDraft()
        // Android Keystore access, decryption and JSON parsing can be expensive.
        // Restore cached pages without blocking construction of the first screen.
        val accountAtStart = owaMailRepository.cachedAccountKey()
        val versionAtStart = sessionVersion
        viewModelScope.launch {
            val (cachedInbox, cachedSent) = withContext(Dispatchers.IO) {
                owaMailRepository.cachedInbox() to owaMailRepository.cachedSent()
            }
            if (versionAtStart != sessionVersion ||
                accountAtStart != owaMailRepository.cachedAccountKey()) return@launch
            _state.update { current ->
                // Do not overwrite a page delivered by a concurrent refresh/worker.
                val inbox = if (current.items.isEmpty()) cachedInbox else current.items
                val sent = if (current.sentItems.isEmpty()) cachedSent else current.sentItems
                current.copy(
                    configured = current.configured || inbox.isNotEmpty(),
                    items = inbox,
                    hasMore = current.hasMore || inbox.size >= MAIL_PAGE_SIZE,
                    nextPageOffset = maxOf(current.nextPageOffset, inbox.size),
                    sentItems = sent,
                    sentHasMore = current.sentHasMore || sent.size >= MAIL_PAGE_SIZE,
                    sentNextPageOffset = maxOf(current.sentNextPageOffset, sent.size),
                )
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
    }

    /**
     * Mail is a lazy tab. Its first network synchronization starts on entry,
     * not while Schedule is racing to display its first frame.
     */
    fun onMailScreenVisible() {
        if (initialRefreshStarted) return
        initialRefreshStarted = true
        refresh()
    }

    fun refresh() {
        if (_state.value.mailbox == OwaMailFolder.SENT) {
            refreshSent()
            return
        }
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
                compose.restoreDraftForActiveAccount()
                repository.logState("refresh success id=$id protocol=OWA items=${items.size}")
                _state.update { old ->
                    val page = refreshedMailPage(
                        incoming = items,
                        cached = old.items,
                        previousOffset = old.nextPageOffset,
                        previousHasMore = old.hasMore,
                        pageSize = MAIL_PAGE_SIZE,
                        merge = ::mergeOwaInboxPages,
                    )
                    old.copy(
                        configured = true,
                        loginRequired = false,
                        loading = false,
                        items = page.messages,
                        nextPageOffset = page.nextOffset,
                        hasMore = page.hasMore,
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
        if (_state.value.mailbox == OwaMailFolder.SENT) {
            loadMoreSent()
            return
        }
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
                    val page = appendedMailPage(
                        cached = it.items, incoming = older,
                        requestedOffset = offset, pageSize = MAIL_PAGE_SIZE,
                        merge = ::mergeOwaInboxPages,
                    )
                    it.copy(
                        loadingMore = false,
                        items = page.messages,
                        hasMore = page.hasMore,
                        nextPageOffset = page.nextOffset,
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


    /** Sent Items is lazy: loading it never interferes with inbox polling or notifications. */
    fun switchMailbox(folder: OwaMailFolder) {
        if (_state.value.mailbox == folder) return
        _state.update { it.copy(mailbox = folder, selected = null, message = null,
            loadingMessage = false, error = null, notice = null) }
        if (folder == OwaMailFolder.SENT && !_state.value.sentLoaded) refreshSent()
    }

    private fun refreshSent() {
        if (_state.value.sentLoading || _state.value.sentLoadingMore) return
        val version = sessionVersion
        _state.update { it.copy(sentLoading = true, sentMoreError = false, error = null) }
        viewModelScope.launch {
            try {
                val page = owaMailRepository.inbox(
                    maxConversations = MAIL_PAGE_SIZE, folder = OwaMailFolder.SENT,
                )
                if (version != sessionVersion) return@launch
                _state.update { old ->
                    val merged = refreshedMailPage(
                        incoming = page,
                        cached = old.sentItems,
                        previousOffset = old.sentNextPageOffset,
                        previousHasMore = old.sentHasMore,
                        pageSize = MAIL_PAGE_SIZE,
                        merge = ::mergeOwaSentPages,
                    )
                    old.copy(
                        sentItems = merged.messages,
                        sentLoaded = true, sentLoading = false,
                        sentHasMore = merged.hasMore,
                        sentNextPageOffset = merged.nextOffset,
                        sentMoreError = false, loginRequired = false,
                    )
                }
                hydrateSentRecipients()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (version != sessionVersion) return@launch
                repository.logState("OWA sent refresh failed type=${failure.javaClass.simpleName}")
                _state.update { it.copy(
                    sentLoading = false,
                    loginRequired = failure is OwaMailRepository.LoginRequired,
                    error = "Не удалось обновить папку «Отправленные».",
                ) }
            }
        }
    }

    private fun loadMoreSent() {
        val old = _state.value
        if (old.sentLoading || old.sentLoadingMore || !old.sentHasMore) return
        val version = sessionVersion
        val offset = old.sentNextPageOffset
        _state.update { it.copy(sentLoadingMore = true, sentMoreError = false) }
        viewModelScope.launch {
            try {
                val page = owaMailRepository.inbox(
                    maxConversations = MAIL_PAGE_SIZE, offset = offset,
                    folder = OwaMailFolder.SENT,
                )
                if (version != sessionVersion ||
                    _state.value.sentNextPageOffset != offset) return@launch
                _state.update {
                    val next = appendedMailPage(
                        cached = it.sentItems, incoming = page,
                        requestedOffset = offset, pageSize = MAIL_PAGE_SIZE,
                        merge = { cached, older -> mergeOwaSentPages(older, cached) },
                    )
                    it.copy(
                        sentItems = next.messages,
                        sentNextPageOffset = next.nextOffset,
                        sentHasMore = next.hasMore,
                        sentLoadingMore = false, sentMoreError = false,
                    )
                }
                hydrateSentRecipients()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (version != sessionVersion) return@launch
                repository.logState("OWA sent page failed type=${failure.javaClass.simpleName}")
                _state.update { it.copy(sentLoadingMore = false, sentMoreError = true) }
            }
        }
    }

    fun openInboxMessage(summary: MailSummary) {
        _state.update { it.copy(mailbox = OwaMailFolder.INBOX) }
        open(summary)
    }

    private fun refreshLocalAttachmentUris(
        summary: MailSummary,
        detail: MailDetail,
        version: Long,
    ) {
        if (detail.attachments.isEmpty()) return
        viewModelScope.launch {
            val found = owaMailRepository.localAttachmentUris(detail.attachments)
            if (version != sessionVersion) return@launch
            _state.update { current ->
                if (current.selected?.uid != summary.uid ||
                    current.selected?.uidValidity != summary.uidValidity ||
                    current.message != detail
                ) current else current.copy(
                    localAttachmentUris = current.localAttachmentUris + found,
                )
            }
        }
    }

    fun localOpenFailed() {
        _state.update { it.copy(error = "Не удалось открыть файл. Проверьте доступ и приложение для просмотра.") }
    }

    fun open(summary: MailSummary) {
        val version = sessionVersion
        val cached = owaMailRepository.cachedMessage(summary)
        _state.update {
            it.copy(selected = summary, message = cached, localAttachmentUris = emptyMap(),
                loadingMessage = cached == null, error = null, notice = null)
        }
        if (cached != null) refreshLocalAttachmentUris(summary, cached, version)
        viewModelScope.launch {
            try {
                val detail = owaMailRepository.message(summary)
                if (version != sessionVersion) return@launch
                _state.update { current ->
                    if (current.selected?.uid != summary.uid ||
                        current.selected?.uidValidity != summary.uidValidity) current
                    else current.copy(message = detail, loadingMessage = false,
                        sentItems = if (current.mailbox == OwaMailFolder.SENT &&
                            !detail.recipients.isNullOrEmpty()) current.sentItems.map { row ->
                                if (row.uid == summary.uid) row.copy(recipients = detail.recipients)
                                else row
                            } else current.sentItems,
                        notice = null, localAttachmentUris = emptyMap())
                }
                refreshLocalAttachmentUris(summary, detail, version)
                if (summary.unread && _state.value.mailbox == OwaMailFolder.INBOX &&
                    _state.value.selected?.uid == summary.uid) {
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
        _state.update { it.copy(mailbox = OwaMailFolder.INBOX) }
        _state.value.items.firstOrNull { it.uid == uid && it.uidValidity == validity }
            ?.let { open(it); return }
        pendingNotification = uid to validity
        refresh()
    }

    fun closeMessage() {
        _state.update { it.copy(selected = null, loadingMessage = false, notice = null,
            error = null, localAttachmentUris = emptyMap()) }
    }

    fun download(attachment: MailAttachment) {
        if (_state.value.savingAttachment != null) return
        val version = sessionVersion
        viewModelScope.launch {
            _state.update { it.copy(savingAttachment = attachment.partPath, error = null, notice = null) }
            try {
                val saved = owaMailRepository.downloadAttachment(attachment)
                if (version != sessionVersion) return@launch
                _state.update { current ->
                    val id = attachment.owaAttachmentId
                    current.copy(
                        savingAttachment = null, notice = "Сохранено: " + saved.name,
                        localAttachmentUris = if (id != null &&
                            current.message?.attachments?.any { it.owaAttachmentId == id } == true
                        ) current.localAttachmentUris + (id to saved.uri)
                        else current.localAttachmentUris,
                    )
                }
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

    // The controller owns drafts, recipient lookup jobs, sending and
    // attachment grants; MailViewModel remains the single public UI facade.
    fun startCompose() = compose.startCompose()
    fun closeCompose() = compose.closeCompose()
    fun editDraft(change: (OutgoingMail) -> OutgoingMail) = compose.editDraft(change)
    fun editRecipient(field: RecipientField, value: String) = compose.editRecipient(field, value)
    fun acceptRecipient(field: RecipientField, suggestion: OwaRecipientSuggestion) =
        compose.acceptRecipient(field, suggestion)
    fun addOutgoingFiles(uris: List<Uri>) = compose.addOutgoingFiles(uris)
    fun removeOutgoingFile(uri: Uri) = compose.removeOutgoingFile(uri)
    fun discardDraft() = compose.discardDraft()
    fun sendDraft() = compose.sendDraft()

    fun logout() {
        sessionVersion++
        pendingNotification = null
        sentRecipientJob?.cancel()
        compose.cancelAndRelease()
        sentVisible = false
        visibleSentUids = emptySet()
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
