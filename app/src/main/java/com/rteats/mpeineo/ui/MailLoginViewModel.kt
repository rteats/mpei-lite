package com.rteats.mpeineo.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rteats.mpeineo.data.DiagnosticLog
import com.rteats.mpeineo.data.SavedCredentialStore
import com.rteats.mpeineo.data.SavedLoginService
import com.rteats.mpeineo.data.SavedLoginCredentials
import com.rteats.mpeineo.data.OwaLoginResult
import com.rteats.mpeineo.data.OwaNativeLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class MailLoginStatus(
    val busy: Boolean = false,
    val error: String? = null,
    val browserRecommended: Boolean = false,
    val savedLoginAvailable: Boolean = false,
    val usingSavedLogin: Boolean = false,
)

internal class MailLoginViewModel(
    context: Context,
    private val diagnostics: DiagnosticLog,
) : ViewModel() {
    private val loginClient = OwaNativeLogin(context.applicationContext)
    private val credentials = SavedCredentialStore(context.applicationContext, SavedLoginService.MAIL)
    private val mutableStatus = MutableStateFlow(
        MailLoginStatus(savedLoginAvailable = credentials.hasSavedLogin()),
    )
    val status = mutableStatus.asStateFlow()
    // Do not retry automatically more than once for the same expired session.
    // Incorrect credentials or unexpected OWA errors must not cause a login loop.
    private var automaticAttempted = false

    /** Called when a mailbox request confirms an authenticated session. */
    fun onMailboxReady() {
        automaticAttempted = false
        if (mutableStatus.value.error != null) {
            mutableStatus.value = MailLoginStatus(
                savedLoginAvailable = credentials.hasSavedLogin(),
            )
        }
    }

    fun refreshSavedLoginState() {
        mutableStatus.value = mutableStatus.value.copy(
            savedLoginAvailable = credentials.hasSavedLogin(),
        )
    }

    /** Retry saved credentials once after an expired/missing cookie session. */
    fun restoreSavedLogin(onSuccess: () -> Unit) {
        if (automaticAttempted || mutableStatus.value.busy) return
        automaticAttempted = true
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { credentials.load() }
            if (saved == null) {
                refreshSavedLoginState()
                return@launch
            }
            mutableStatus.value = MailLoginStatus(
                busy = true, savedLoginAvailable = true, usingSavedLogin = true,
            )
            try {
                when (loginClient.login(saved.username, saved.password)) {
                    OwaLoginResult.SUCCESS -> {
                        diagnostics.log("OWA_NATIVE", "saved_login=verified")
                        mutableStatus.value = MailLoginStatus(savedLoginAvailable = true)
                        onSuccess()
                    }
                    OwaLoginResult.REJECTED -> {
                        // A rejected saved password must never be retried automatically.
                        withContext(Dispatchers.IO) { credentials.clear() }
                        diagnostics.log("OWA_NATIVE", "saved_login=rejected")
                        mutableStatus.value = MailLoginStatus(
                            error = "Сохранённый вход больше не действует. Введите пароль.",
                        )
                    }
                    OwaLoginResult.USE_BROWSER -> {
                        diagnostics.log("OWA_NATIVE", "saved_login=browser_required")
                        mutableStatus.value = MailLoginStatus(
                            error = "Для повторного входа требуется браузер ОСЭП.",
                            browserRecommended = true, savedLoginAvailable = true,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                diagnostics.log("OWA_NATIVE",
                    "saved_login=network_error type=" + failure.javaClass.simpleName)
                mutableStatus.value = MailLoginStatus(
                    error = "Не удалось войти автоматически. Проверьте подключение.",
                    savedLoginAvailable = credentials.hasSavedLogin(),
                )
            }
        }
    }

    fun retrySavedLogin(onSuccess: () -> Unit) {
        if (mutableStatus.value.busy) return
        automaticAttempted = false
        restoreSavedLogin(onSuccess)
    }

    fun login(
        username: String,
        password: String,
        rememberLogin: Boolean,
        onSuccess: () -> Unit,
    ) {
        if (mutableStatus.value.busy) return
        if (username.isBlank() || password.isBlank()) {
            mutableStatus.value = mutableStatus.value.copy(
                error = "Введите логин и пароль.",
            )
            return
        }
        mutableStatus.value = MailLoginStatus(
            busy = true, savedLoginAvailable = credentials.hasSavedLogin(),
        )
        viewModelScope.launch {
            try {
                when (loginClient.login(username.trim(), password)) {
                    OwaLoginResult.SUCCESS -> {
                        withContext(Dispatchers.IO) {
                            if (rememberLogin) {
                                runCatching {
                                    credentials.save(SavedLoginCredentials(username.trim(), password))
                                }.onFailure {
                                    diagnostics.log("OWA_NATIVE",
                                        "save_login_failed type=" + it.javaClass.simpleName)
                                }
                            } else {
                                credentials.clear()
                            }
                        }
                        diagnostics.log("OWA_NATIVE", "login=verified")
                        mutableStatus.value = MailLoginStatus(
                            savedLoginAvailable = credentials.hasSavedLogin(),
                        )
                        onSuccess()
                    }
                    OwaLoginResult.REJECTED -> {
                        diagnostics.log("OWA_NATIVE", "login=unverified")
                        mutableStatus.value = MailLoginStatus(
                            error = "Не удалось подтвердить вход. Проверьте данные или используйте браузер.",
                            savedLoginAvailable = credentials.hasSavedLogin(),
                        )
                    }
                    OwaLoginResult.USE_BROWSER -> {
                        diagnostics.log("OWA_NATIVE", "login=browser_required")
                        mutableStatus.value = MailLoginStatus(
                            error = "Эта форма входа требует браузер ОСЭП.",
                            browserRecommended = true,
                            savedLoginAvailable = credentials.hasSavedLogin(),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Never include credential-bearing URLs, HTTP bodies or tokens in logs.
                diagnostics.log("OWA_NATIVE",
                    "login=network_error type=" + failure.javaClass.simpleName)
                mutableStatus.value = MailLoginStatus(
                    error = "Не удалось связаться с ОСЭП. Повторите попытку или откройте браузер.",
                    savedLoginAvailable = credentials.hasSavedLogin(),
                )
            }
        }
    }

    companion object {
        fun factory(context: Context, diagnostics: DiagnosticLog): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MailLoginViewModel(context, diagnostics) as T
            }
    }
}
