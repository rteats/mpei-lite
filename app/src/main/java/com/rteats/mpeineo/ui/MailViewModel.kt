package com.rteats.mpeineo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rteats.mpeineo.data.MailAttachment
import com.rteats.mpeineo.data.MailCredentials
import com.rteats.mpeineo.data.MailDetail
import com.rteats.mpeineo.data.MailRepository
import com.rteats.mpeineo.data.MailNotificationScheduler
import com.rteats.mpeineo.data.MailSummary
import com.rteats.mpeineo.data.MailServerCertificate
import com.rteats.mpeineo.data.markMailRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class MailUiState(
    val configured: Boolean = false,
    val certificate: MailServerCertificate? = null,
    val inspectingCertificate: Boolean = false,
    val username: String = "",
    val loading: Boolean = false,
    val items: List<MailSummary> = emptyList(),
    val selected: MailSummary? = null,
    val message: MailDetail? = null,
    val loadingMessage: Boolean = false,
    val savingAttachment: List<Int>? = null,
    val notice: String? = null,
    val error: String? = null,
)

/** Only the username and message summaries enter Compose state. Never the password. */
internal class MailViewModel(private val repository: MailRepository) : ViewModel() {
    private val initialAuth = repository.credentials.load()
    private val _state = MutableStateFlow(
        MailUiState(configured = initialAuth != null, username = initialAuth?.username.orEmpty()),
    )
    val state: StateFlow<MailUiState> = _state.asStateFlow()
    private var inMemoryCredentials: MailCredentials? = initialAuth
    private var attemptedCredentials: MailCredentials? = initialAuth
    private var sessionVersion = 0L

    init {
        repository.logState("inbox model created credentialsStored=${initialAuth != null}")
        if (initialAuth != null) {
            val version = sessionVersion
            viewModelScope.launch {
                val cached = repository.cachedInbox(initialAuth.username)
                if (version == sessionVersion &&
                    inMemoryCredentials?.username == initialAuth.username && cached.isNotEmpty()) {
                    _state.update { it.copy(items = cached) }
                }
                refresh()
            }
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Введите имя пользователя IMAP и пароль.") }
            return
        }
        val auth = MailCredentials(username.trim(), password)
        repository.logState("login requested")
        attemptedCredentials = auth
        val version = ++sessionVersion
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null, certificate = null) }
            runCatching { repository.refreshInbox(auth) }
                .onSuccess { items ->
                    repository.logState("login success messages=${items.size}")
                    if (version != sessionVersion) return@onSuccess
                    // An obsolete login must never restore credentials after logout.
                    repository.credentials.save(auth)
                    inMemoryCredentials = auth
                    _state.update {
                        it.copy(configured = true, username = auth.username, items = items, loading = false, certificate = null)
                    }
                }.onFailure { cause ->
                    if (version != sessionVersion) return@onFailure
                    repository.logState("login failure type=${cause.javaClass.simpleName}")
                    handleConnectionFailure(cause, version)
                }
        }
    }

    fun refresh() {
        val auth = inMemoryCredentials ?: return
        attemptedCredentials = auth
        if (_state.value.loading) return
        repository.logState("refresh requested cachedItems=${_state.value.items.size}")
        val version = sessionVersion
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.refreshInbox(auth) }
                .onSuccess { items ->
                    if (version == sessionVersion) {
                        repository.logState("refresh success messages=${items.size}")
                        _state.update { it.copy(items = items, loading = false) }
                    }
                }
                .onFailure { cause ->
                    if (version == sessionVersion) {
                        repository.logState("refresh failure type=${cause.javaClass.simpleName}")
                        handleConnectionFailure(cause, version)
                    }
                }
        }
    }

    fun open(summary: MailSummary) {
        val auth = inMemoryCredentials ?: return
        val version = sessionVersion
        _state.update {
            it.copy(selected = summary, message = null, loadingMessage = true, error = null, notice = null)
        }
        viewModelScope.launch {
            val cached = repository.cachedMessage(auth.username, summary.uid, summary.uidValidity)
            if (version != sessionVersion) return@launch
            if (cached != null) {
                _state.update { current ->
                    if (current.selected?.uid == summary.uid &&
                        current.selected?.uidValidity == summary.uidValidity
                    ) current.copy(message = cached, loadingMessage = false)
                    else current
                }
                markReadAfterOpening(auth, summary, version)
                return@launch
            }
            runCatching { repository.readMessage(auth, summary.uid, summary.uidValidity) }
                .onSuccess { body ->
                    if (version != sessionVersion) return@onSuccess
                    _state.update { current ->
                        if (current.selected?.uid != summary.uid ||
                            current.selected?.uidValidity != summary.uidValidity
                        ) current
                        else current.copy(message = body, loadingMessage = false)
                    }
                    markReadAfterOpening(auth, summary, version)
                }
                .onFailure { cause ->
                    if (version != sessionVersion) return@onFailure
                    _state.update { current ->
                        if (current.selected?.uid != summary.uid ||
                            current.selected?.uidValidity != summary.uidValidity
                        ) current
                        else current.copy(loadingMessage = false, error = readableError(cause))
                    }
                }
        }
    }

    /**
     * A message counts as opened only once its body (or offline cached body)
     * is displayed. Mark it as read on the actual server without delaying the
     * message screen; an unsuccessful write must not falsify the local flags.
     */
    private fun markReadAfterOpening(
        auth: MailCredentials,
        summary: MailSummary,
        version: Long,
    ) {
        if (!summary.unread || version != sessionVersion) return
        viewModelScope.launch {
            runCatching {
                repository.markAsRead(auth, summary.uid, summary.uidValidity)
            }.onSuccess {
                if (version != sessionVersion) return@onSuccess
                _state.update { current ->
                    current.copy(
                        items = markMailRead(current.items, summary.uid, summary.uidValidity),
                        selected = current.selected?.let { selected ->
                            if (selected.uid == summary.uid &&
                                selected.uidValidity == summary.uidValidity
                            ) selected.copy(unread = false) else selected
                        },
                    )
                }
            }.onFailure { cause ->
                if (version != sessionVersion) return@onFailure
                _state.update { current ->
                    if (current.selected?.uid == summary.uid &&
                        current.selected?.uidValidity == summary.uidValidity
                    ) current.copy(
                        notice = "Письмо открыто, но не удалось отметить его прочитанным: " +
                            readableError(cause),
                    ) else current
                }
            }
        }
    }

    /** Open the exact UID referenced by an email notification. */
    fun openFromNotification(uid: Long, validity: Long) {
        if (uid <= 0L || validity <= 0L || inMemoryCredentials == null) return
        val summary = _state.value.items.firstOrNull {
            it.uid == uid && it.uidValidity == validity
        } ?: MailSummary(
            uid = uid,
            uidValidity = validity,
            sender = "",
            subject = "",
            sentAt = 0L,
            unread = true,
            hasAttachment = false,
        )
        open(summary)
    }

    fun closeMessage() {
        // AnimatedContent keeps the outgoing message composed until its exit
        // finishes. Retain its body so back navigation doesn't replace the
        // outgoing message with a placeholder in the middle of the animation.
        // open() and logout() clear the old body when appropriate.
        _state.update {
            it.copy(selected = null, loadingMessage = false, notice = null, error = null)
        }
    }

    fun download(attachment: MailAttachment) {
        val auth = inMemoryCredentials ?: return
        val version = sessionVersion
        val summary = _state.value.selected ?: return
        if (_state.value.savingAttachment != null) return
        viewModelScope.launch {
            _state.update { it.copy(savingAttachment = attachment.partPath, notice = null, error = null) }
            runCatching {
                repository.saveAttachment(auth, summary.uid, summary.uidValidity, attachment)
            }.onSuccess { destination ->
                if (version == sessionVersion) {
                    _state.update { it.copy(savingAttachment = null, notice = "Сохранено: $destination") }
                }
            }.onFailure { cause ->
                if (version == sessionVersion) {
                    _state.update { it.copy(savingAttachment = null, error = readableError(cause)) }
                }
            }
        }
    }

    fun logout() {
        sessionVersion++
        viewModelScope.coroutineContext.cancelChildren()
        MailNotificationScheduler.stopAndClear(repository.contextForNotifications)
        inMemoryCredentials?.username?.let(repository::clearCachedMail)
        repository.credentials.clear()
        repository.forgetTrustedCertificate()
        attemptedCredentials = null
        inMemoryCredentials = null
        _state.value = MailUiState()
    }

    /**
     * Don't accept trust automatically. The user must independently compare
     * the SHA-256 fingerprint and explicitly approve the exact certificate.
     */
    fun approveCertificate() {
        val candidate = _state.value.certificate ?: return
        val auth = attemptedCredentials ?: return
        val version = sessionVersion
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val live = repository.inspectServerCertificate()
                require(live.sha256 == candidate.sha256) {
                    "Сертификат сервера изменился. Проверьте новый отпечаток."
                }
                require(live.currentlyValid && live.matchesMailHostname) {
                    "Сертификат просрочен либо не принадлежит mail.mpei.ru."
                }
                if (version != sessionVersion) return@runCatching
                repository.trustCertificate(live)
            }.onSuccess {
                if (version != sessionVersion) return@onSuccess
                _state.update { it.copy(loading = false, certificate = null) }
                if (_state.value.configured) refresh() else login(auth.username, auth.password)
            }.onFailure { problem ->
                if (version != sessionVersion) return@onFailure
                _state.update {
                    it.copy(
                        loading = false,
                        certificate = null,
                        error = readableError(problem),
                    )
                }
            }
        }
    }

    fun dismissCertificateWarning() {
        _state.update { it.copy(certificate = null, inspectingCertificate = false) }
    }

    private suspend fun handleConnectionFailure(cause: Throwable, version: Long) {
        if (version != sessionVersion) return
        val descriptions = generateSequence(cause) { it.cause }
            .take(8).mapNotNull { it.message }.joinToString(" ")
        val likelyTlsIssue = descriptions.contains("certificate", ignoreCase = true) ||
            descriptions.contains("PKIX", ignoreCase = true) ||
            descriptions.contains("SSLHandshake", ignoreCase = true)

        repository.logState(
            "connection failure tlsSuspected=$likelyTlsIssue type=${cause.javaClass.simpleName}"
        )
        if (!likelyTlsIssue) {
            _state.update {
                it.copy(loading = false, inspectingCertificate = false, error = readableError(cause))
            }
            return
        }

        _state.update {
            it.copy(
                loading = false,
                inspectingCertificate = true,
                certificate = null,
                error = "Сертификат mail.mpei.ru не доверен. Для подключения проверьте его SHA-256 отпечаток.",
            )
        }
        runCatching { repository.inspectServerCertificate() }
            .onSuccess { cert ->
                if (version != sessionVersion) return@onSuccess
                _state.update {
                    it.copy(certificate = cert, inspectingCertificate = false)
                }
            }
            .onFailure { failure ->
                if (version != sessionVersion) return@onFailure
                _state.update {
                    it.copy(
                        inspectingCertificate = false,
                        error = "Не удалось получить сертификат: " + readableError(failure),
                    )
                }
            }
    }

    private fun readableError(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            message.contains("authentication", true) || message.contains("login failed", true) ->
                "Не удалось войти в IMAP. Проверьте имя пользователя и пароль FairEmail."
            message.contains("timeout", true) -> "Сервер почты не ответил. Повторите позже."
            else -> message.take(240).ifBlank { "Не удалось получить почту через IMAP." }
        }
    }

    companion object {
        fun factory(repo: MailRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MailViewModel(repo) as T
            }
    }
}
