package com.rteats.mpeineo.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.rteats.mpeineo.data.BarsCredentialResult
import com.rteats.mpeineo.data.BarsHtmlParser
import com.rteats.mpeineo.data.BarsHttpClient
import com.rteats.mpeineo.data.BarsHttpPage
import com.rteats.mpeineo.data.BarsSessionStore
import com.rteats.mpeineo.data.DiagnosticLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class BarsNativeStep { IDLE, OPENING, LOGIN, SUBMITTING, CODE, REFRESHING }

internal data class BarsNativeStatus(
    val step: BarsNativeStep = BarsNativeStep.IDLE,
    val forceLogin: Boolean = false,
    val busySending: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

internal class BarsNativeAuthViewModel(
    private val bars: BarsViewModel,
    private val diagnostics: DiagnosticLog,
    context: Context,
) : ViewModel() {
    private val http = BarsHttpClient(BarsSessionStore(context.applicationContext))
    private var started = false
    private val gson = Gson()
    private val _status = MutableStateFlow(BarsNativeStatus())
    val status = _status.asStateFlow()

    /** Check saved session with the server before falling back to the login form. */
    fun start() {
        if (started) return
        started = true
        _status.value = BarsNativeStatus(step = BarsNativeStep.OPENING)
        viewModelScope.launch {
            val restored = runCatching { http.restoreSession() }
            val page = restored.getOrNull()
            if (page != null) {
                diagnostics.log("BARS_NATIVE", "session_restore=verified")
                publish(page)
                return@launch
            }
            diagnostics.log("BARS_NATIVE", "session_restore=" +
                if (restored.isFailure) "unverified" else "missing")
            // Keep cached marks available even when the network is offline.
            val needsLogin = bars.state.value.disciplines.isEmpty()
            _status.value = BarsNativeStatus()
            if (needsLogin) openLogin(force = true) else {
                bars.webAuth(BARS_LOGIN_URL)
            }
        }
    }

    fun openLogin(force: Boolean = false) {
        if (_status.value.step in setOf(BarsNativeStep.OPENING, BarsNativeStep.SUBMITTING)) return
        _status.value = BarsNativeStatus(BarsNativeStep.OPENING, force)
        viewModelScope.launch {
            runCatching { http.openLogin() }
                .onSuccess {
                    bars.webAuth(BARS_LOGIN_URL)
                    diagnostics.log("BARS_NATIVE", "login_form=ready")
                    _status.update { it.copy(step = BarsNativeStep.LOGIN) }
                }.onFailure {
                    diagnostics.log("BARS_NATIVE", "login_form=failed type=" + it.javaClass.simpleName)
                    _status.update { it.copy(step = BarsNativeStep.LOGIN,
                        error = "Не удалось открыть БАРС.") }
                }
        }
    }

    fun login(account: String, password: String) {
        if (_status.value.step != BarsNativeStep.LOGIN) return
        if (account.isBlank() || password.isBlank()) {
            _status.update { it.copy(error = "Введите логин и пароль.") }
            return
        }
        _status.update { it.copy(step = BarsNativeStep.SUBMITTING, error = null) }
        viewModelScope.launch {
            runCatching { http.credentials(account.trim(), password) }.onSuccess { result ->
                when (result) {
                    is BarsCredentialResult.NeedsCode ->
                        _status.update { it.copy(step = BarsNativeStep.CODE) }
                    is BarsCredentialResult.Complete -> publish(result.page)
                }
            }.onFailure {
                diagnostics.log("BARS_NATIVE", "credentials=failed type=" + it.javaClass.simpleName)
                _status.update { it.copy(step = BarsNativeStep.LOGIN,
                    error = "Вход не выполнен. Проверьте логин и пароль.") }
            }
        }
    }

    fun sendCode() {
        if (_status.value.step != BarsNativeStep.CODE || _status.value.busySending) return
        _status.update { it.copy(busySending = true, error = null) }
        viewModelScope.launch {
            runCatching { http.requestCode() }.onSuccess {
                _status.update { it.copy(busySending = false, notice = "Код запрошен через МАКС.") }
            }.onFailure {
                diagnostics.log("BARS_NATIVE", "send_code=failed type=" + it.javaClass.simpleName)
                _status.update { it.copy(busySending = false,
                    error = "Не удалось отправить код.") }
            }
        }
    }

    fun confirm(code: String) {
        if (_status.value.step != BarsNativeStep.CODE) return
        if (code.isBlank()) {
            _status.update { it.copy(error = "Введите код подтверждения.") }
            return
        }
        _status.update { it.copy(step = BarsNativeStep.SUBMITTING, error = null) }
        viewModelScope.launch {
            runCatching { http.confirm(code.trim()) }.onSuccess { publish(it) }.onFailure {
                diagnostics.log("BARS_NATIVE", "code=failed type=" + it.javaClass.simpleName)
                http.clear()
                _status.update { it.copy(step = BarsNativeStep.LOGIN,
                    error = "Код отклонён. Начните вход заново.") }
            }
        }
    }

    private suspend fun publish(page: BarsHttpPage) {
        runCatching {
            val payload = BarsHtmlParser.parseMain(page.html)
            bars.authenticatedPage(page.url.toString())
            bars.extractionReceived(gson.toJson(payload))
            bars.rawSnapshotReceived("MAIN", BarsHtmlParser.mainReport(page.html))
            diagnostics.log("BARS_NATIVE", "marks=loaded subjects=" + payload.disciplines.size)
            _status.value = BarsNativeStatus()
        }.onSuccess {
            refreshSummary()
        }.onFailure {
            diagnostics.log("BARS_NATIVE", "parse=failed type=" + it.javaClass.simpleName)
            bars.extractionFailed("Не удалось прочитать данные БАРС.")
            _status.update { it.copy(step = BarsNativeStep.LOGIN,
                error = "Не удалось разобрать таблицу дисциплин.") }
        }
    }

    fun refresh() {
        if (_status.value.step == BarsNativeStep.REFRESHING) return
        if (http.mainUrl == null) {
            openLogin(force = true)
            return
        }
        bars.startRefresh()
        _status.update { it.copy(step = BarsNativeStep.REFRESHING) }
        viewModelScope.launch {
            runCatching { http.refreshMain() }.onSuccess {
                publish(it)
            }.onFailure {
                diagnostics.log("BARS_NATIVE", "refresh=failed type=" + it.javaClass.simpleName)
                bars.extractionFailed("Не удалось обновить БАРС.")
                if (http.mainUrl == null) bars.webAuth(BARS_LOGIN_URL)
                _status.update { it.copy(step = BarsNativeStep.IDLE) }
            }
        }
    }

    fun refreshSummary() {
        if (http.summaryUrl == null) return
        bars.summaryCaptureStarted()
        viewModelScope.launch {
            runCatching { http.refreshSummary() }.onSuccess {
                bars.rawSnapshotReceived("SUMMARY", it)
                diagnostics.log("BARS_NATIVE", "summary=loaded")
            }.onFailure {
                diagnostics.log("BARS_NATIVE", "summary=failed type=" + it.javaClass.simpleName)
                bars.summaryCaptureFailed("Не удалось получить сводку БАРС.")
            }
        }
    }

    fun cancelLogin() {
        http.clear()
        _status.value = BarsNativeStatus()
    }

    companion object {
        fun factory(
            bars: BarsViewModel,
            diagnostics: DiagnosticLog,
            context: Context,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BarsNativeAuthViewModel(bars, diagnostics, context) as T
            }
    }
}
