package com.rteats.mpeineo.data

import android.content.Context
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manual, read-only Exchange OWA authentication diagnostic.
 *
 * The OWA browser owns interactive login. We borrow only its in-memory cookie
 * header for one POST to sessiondata.ashx; no credentials, cookies, response
 * bodies, email addresses, or anti-CSRF tokens are persisted or logged.
 *
 * This check is active network traffic and MAY extend the remote idle timeout.
 * It must never run on a timer or during normal app startup.
 */
class OwaAuthProbe(
    context: Context,
    private val diagnostics: DiagnosticLog,
) {
    private val application = context.applicationContext
    private val preferences = application.getSharedPreferences("owa_auth_diagnostics", Context.MODE_PRIVATE)

    data class Result(val summary: String)

    private data class HttpResult(
        val status: Int,
        val state: String,
        val summary: String,
        val setCookies: List<String>,
    )

    suspend fun check(): Result {
        val started = SystemClock.elapsedRealtime()
        val timestamp = System.currentTimeMillis()
        val previous = preferences.getLong("checked_at", 0L)
        val lastState = preferences.getString("last_state", "none").orEmpty()
        var state = "NETWORK_ERROR"
        var status: Int? = null
        var cookieCount = 0
        var refreshedCookieCount = 0
        var summary = "Сеть недоступна — срок сессии не определён"

        try {
            val (cookies, userAgent) = withContext(Dispatchers.Main) {
                CookieManager.getInstance().getCookie(OWA_URL).orEmpty() to
                    WebSettings.getDefaultUserAgent(application)
            }
            cookieCount = cookies.split(';').count { it.isNotBlank() }

            if (cookies.isBlank()) {
                state = "NO_COOKIES"
                summary = "Нет cookies ОСЭП — откройте ОСЭП и войдите"
            } else {
                val probe = withContext(Dispatchers.IO) { requestSessionData(cookies, userAgent) }
                state = probe.state
                status = probe.status
                summary = probe.summary

                // Respect cookie renewal/expiry responses, just as the WebView
                // cookie jar would. Never print the Set-Cookie values.
                if (probe.setCookies.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val manager = CookieManager.getInstance()
                        probe.setCookies.forEach { manager.setCookie(OWA_URL, it) }
                        manager.flush()
                    }
                    refreshedCookieCount = probe.setCookies.size
                }
            }
        } catch (failure: Exception) {
            state = "NETWORK_ERROR"
            summary = "Ошибка проверки: ${failure.javaClass.simpleName} (не обязательно истекла сессия)"
        }

        val previousAge = if (previous > 0 && timestamp >= previous) {
            ((timestamp - previous) / 1000L).toString() + "s"
        } else {
            "none"
        }
        val duration = SystemClock.elapsedRealtime() - started
        diagnostics.log(
            "OWA_AUTH",
            "manual_probe state=$state http=${status ?: "none"} " +
                "cookies=$cookieCount setCookies=$refreshedCookieCount " +
                "durationMs=$duration sincePrevious=$previousAge previousState=$lastState",
        )
        preferences.edit()
            .putLong("checked_at", timestamp)
            .putString("last_state", state)
            .apply()
        return Result(summary)
    }

    private fun requestSessionData(cookies: String, userAgent: String): HttpResult {
        val connection = (URL(SESSION_DATA_URL).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(0)
            connection.setRequestProperty("Cookie", cookies)
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Origin", "https://mail.mpei.ru")
            connection.setRequestProperty("Referer", OWA_URL)
            connection.setRequestProperty("Accept", "*/*")
            connection.outputStream.use { /* Empty POST, matching the OWA bootstrap capture. */ }

            val status = connection.responseCode
            val setCookies = connection.headerFields
                .filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
                .values.flatMap { it.orEmpty() }

            val outcome = when (status) {
                200 -> {
                    val payload = connection.inputStream.bufferedReader().use { it.readText() }
                    val validConfig = runCatching {
                        JSONObject(payload).optJSONObject("owaUserConfig")
                            ?.optJSONObject("SessionSettings") != null
                    }.getOrDefault(false)
                    when {
                        validConfig -> "AUTHENTICATED" to "Сессия ОСЭП действительна (HTTP 200)"
                        connection.contentType.orEmpty().contains("text/html", ignoreCase = true) ->
                            "LOGIN_REQUIRED" to "OWA вернул страницу входа вместо данных сессии"
                        else ->
                            "UNEXPECTED_RESPONSE" to "HTTP 200, но ответ не похож на сессию OWA"
                    }
                }
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location").orEmpty()
                    if (location.contains("CookieAuth", true) ||
                        location.contains("/auth/", true) ||
                        location.contains("logon", true)
                    ) {
                        "LOGIN_REQUIRED" to "OWA перенаправляет на вход (HTTP $status)"
                    } else {
                        "REDIRECT" to "OWA вернул перенаправление (HTTP $status)"
                    }
                }
                401, 440 -> "LOGIN_REQUIRED" to "OWA требует повторного входа (HTTP $status)"
                403 -> "ACCESS_DENIED" to "OWA отказал в доступе (HTTP 403); причина не установлена"
                else -> "HTTP_ERROR" to "Ответ OWA: HTTP $status"
            }
            return HttpResult(status, outcome.first, outcome.second, setCookies)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val OWA_URL = "https://mail.mpei.ru/owa/"
        private const val SESSION_DATA_URL =
            "https://mail.mpei.ru/owa/sessiondata.ashx?appcacheclient=0"
    }
}
