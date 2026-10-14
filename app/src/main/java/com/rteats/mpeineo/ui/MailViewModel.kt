package com.rteats.mpeineo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rteats.mpeineo.data.MailAttachment
import com.rteats.mpeineo.data.MailCredentials
import com.rteats.mpeineo.data.MailDetail
import com.rteats.mpeineo.data.MailRepository
import com.rteats.mpeineo.data.OwaAuthProbe
import com.rteats.mpeineo.data.OwaMailRepository
import com.rteats.mpeineo.data.MailNotificationScheduler
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
            _state.update { it.copy(configured = true, items = cached) }
        }
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        val version = sessionVersion
        val id = ++refreshSequence
        repository.logState("refresh requested id=$id protocol=OWA cachedItems=${_state.value.items.size}")
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
            try {
                val items = owaMailRepository.inbox(refreshId = id)
                if (version != sessionVersion) return@launch
                repository.credentials.clear()
                repository.logState("refresh success id=$id protocol=OWA items=${items.size}")
                _state.update { it.copy(configured = true, loading = false, items = items) }
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
                        loading = false,
                        error = if (needsLogin) "Сессия ОСЭП недействительна. Войдите через кнопку ОСЭП."
                            else if (it.items.isEmpty()) "Не удалось получить почту через ОСЭП." else null,
                        notice = if (it.items.isNotEmpty()) "Показаны сохранённые письма" else null,
                    )
                }
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
                        notice = if (summary.hasAttachment)
                            "Вложения пока доступны через ОСЭП" else null)
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
        _state.update { it.copy(notice = "Вложения пока доступны через ОСЭП.") }
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
