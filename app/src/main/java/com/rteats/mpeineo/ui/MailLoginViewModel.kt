package com.rteats.mpeineo.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rteats.mpeineo.data.DiagnosticLog
import com.rteats.mpeineo.data.OwaLoginResult
import com.rteats.mpeineo.data.OwaNativeLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class MailLoginStatus(
    val busy: Boolean = false,
    val error: String? = null,
    val browserRecommended: Boolean = false,
)

internal class MailLoginViewModel(
    context: Context,
    private val diagnostics: DiagnosticLog,
) : ViewModel() {
    private val loginClient = OwaNativeLogin(context.applicationContext)
    private val mutableStatus = MutableStateFlow(MailLoginStatus())
    val status = mutableStatus.asStateFlow()

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        if (mutableStatus.value.busy) return
        if (username.isBlank() || password.isBlank()) {
            mutableStatus.value = MailLoginStatus(error = "Введите логин и пароль.")
            return
        }
        mutableStatus.value = MailLoginStatus(busy = true)
        viewModelScope.launch {
            try {
                when (loginClient.login(username.trim(), password)) {
                    OwaLoginResult.SUCCESS -> {
                        diagnostics.log("OWA_NATIVE", "login=verified")
                        mutableStatus.value = MailLoginStatus()
                        onSuccess()
                    }
                    OwaLoginResult.REJECTED -> {
                        diagnostics.log("OWA_NATIVE", "login=unverified")
                        mutableStatus.value = MailLoginStatus(
                            error = "Не удалось подтвердить вход. Проверьте данные или используйте браузер.",
                        )
                    }
                    OwaLoginResult.USE_BROWSER -> {
                        diagnostics.log("OWA_NATIVE", "login=browser_required")
                        mutableStatus.value = MailLoginStatus(
                            error = "Эта форма входа требует браузер ОСЭП.",
                            browserRecommended = true,
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
