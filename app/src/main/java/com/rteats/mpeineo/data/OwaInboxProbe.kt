package com.rteats.mpeineo.data

import android.content.Context
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebSettings
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Manual, read-only Exchange OWA inbox test. Its purpose is to verify that a
 * WebView-authenticated session can also access the mailbox service API.
 * No message bodies, senders, subjects, IDs, cookies, or tokens are logged.
 */
class OwaInboxProbe(
    context: Context,
    private val diagnostics: DiagnosticLog,
) {
    private val application = context.applicationContext

    data class Result(val summary: String)

    private data class Response(
        val status: Int,
        val contentType: String,
        val body: String,
        val location: String,
        val setCookies: List<String>,
    )

    suspend fun check(): Result {
        val started = SystemClock.elapsedRealtime()
        var state = "NETWORK_ERROR"
        var http: Int? = null
        var listed = 0
        var total = 0
        var unread = 0
        var cookieCount = 0
        var renewedCookies = 0
        var responseCode = "none"
        var failureType = "none"
        var summary = "Не удалось проверить входящие OWA"

        try {
            val (cookies, agent) = withContext(Dispatchers.Main) {
                CookieManager.getInstance().getCookie(OWA_URL).orEmpty() to
                    WebSettings.getDefaultUserAgent(application)
            }
            cookieCount = cookies.split(';').count { it.isNotBlank() }
            val canary = cookies.split(';')
                .map { it.trim() }
                .firstOrNull { it.substringBefore('=').equals("X-OWA-CANARY", ignoreCase = true) }
                ?.substringAfter('=', "")
                .orEmpty()

            if (cookies.isBlank()) {
                state = "NO_COOKIES"
                summary = "Нет cookies ОСЭП — сначала войдите через браузер ОСЭП"
            } else if (canary.isBlank()) {
                state = "NO_CANARY"
                summary = "Нет X-OWA-CANARY: проверьте сохранение cookies ОСЭП"
            } else {
                val result = withContext(Dispatchers.IO) { requestInbox(cookies, agent, canary) }
                http = result.status
                if (result.setCookies.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        CookieManager.getInstance().apply {
                            result.setCookies.forEach { setCookie(OWA_URL, it) }
                            flush()
                        }
                    }
                    renewedCookies = result.setCookies.size
                }

                when {
                    result.status in listOf(301, 302, 303, 307, 308) &&
                        (result.location.contains("auth", true) ||
                         result.location.contains("logon", true)) -> {
                        state = "LOGIN_REQUIRED"
                        summary = "OWA перенаправляет на вход"
                    }
                    result.status == 401 || result.status == 440 -> {
                        state = "LOGIN_REQUIRED"
                        summary = "Требуется повторный вход в ОСЭП"
                    }
                    result.status == 200 -> {
                        val json = runCatching { JSONObject(result.body) }.getOrNull()
                        val content = json?.optJSONObject("Body")
                        responseCode = content?.optString("ResponseCode").orEmpty()
                            .ifBlank { "none" }
                        if (content != null &&
                            responseCode == "NoError" &&
                            content.optString("ResponseClass") == "Success" &&
                            content.optJSONArray("Conversations") != null
                        ) {
                            val conversations = content.getJSONArray("Conversations")
                            listed = conversations.length()
                            total = content.optInt("TotalConversationsInView", listed)
                            unread = (0 until listed).sumOf { index ->
                                conversations.optJSONObject(index)?.optInt("UnreadCount", 0) ?: 0
                            }
                            state = "SUCCESS"
                            summary = "OWA: получено $listed цепочек, всего $total, непрочитанных $unread"
                        } else if (result.contentType.contains("text/html", true)) {
                            state = "LOGIN_REQUIRED"
                            summary = "OWA вернул страницу входа вместо писем"
                        } else {
                            state = "UNEXPECTED_RESPONSE"
                            summary = "Неожиданный ответ Exchange ($responseCode)"
                        }
                    }
                    else -> {
                        state = "HTTP_ERROR"
                        summary = "Ответ сервера OWA: HTTP ${result.status}"
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failureType = error.javaClass.simpleName
            state = "NETWORK_ERROR"
            summary = "Ошибка OWA: $failureType"
        }
        diagnostics.log(
            "OWA_INBOX",
            "manual_probe state=$state http=${http ?: "none"} " +
                "listed=$listed total=$total unread=$unread " +
                "cookies=$cookieCount setCookies=$renewedCookies " +
                "responseCode=${responseCode.take(80)} failureType=$failureType " +
                "durationMs=${SystemClock.elapsedRealtime() - started}",
        )
        return Result(summary)
    }

    private fun requestInbox(cookies: String, agent: String, canary: String): Response {
        val connection = (URL(SERVICE_URL).openConnection() as HttpURLConnection)
        try {
            val payload = FIND_INBOX_JSON.toByteArray(Charsets.UTF_8)
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(payload.size)
            connection.setRequestProperty("Cookie", cookies)
            connection.setRequestProperty("User-Agent", agent)
            connection.setRequestProperty("Origin", "https://mail.mpei.ru")
            connection.setRequestProperty("Referer", OWA_URL)
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            connection.setRequestProperty("Action", "FindConversation")
            connection.setRequestProperty("X-OWA-ActionName", "FindConversation")
            connection.setRequestProperty("X-OWA-CANARY", canary)
            connection.outputStream.use { it.write(payload) }

            val status = connection.responseCode
            val setCookies = connection.headerFields
                .filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
                .values.flatMap { it.orEmpty() }
            val responseBody = if (status == 200) {
                connection.inputStream.bufferedReader().use { reader ->
                    val value = reader.readText()
                    require(value.length <= 2_000_000) { "OWA response exceeds diagnostic limit" }
                    value
                }
            } else ""
            return Response(
                status = status,
                contentType = connection.contentType.orEmpty(),
                body = responseBody,
                location = connection.getHeaderField("Location").orEmpty(),
                setCookies = setCookies,
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val OWA_URL = "https://mail.mpei.ru/owa/"
        private const val SERVICE_URL =
            "https://mail.mpei.ru/owa/service.svc?action=FindConversation&ID=-1&AC=1"
        // Static JSON schema derived from user's Exchange 2013 receive capture.
        // Contains no credentials, tokens, email addresses or message contents.
        private const val FIND_INBOX_JSON = """{"__type":"FindConversationJsonRequest:#Exchange","Header":{"__type":"JsonRequestHeaders:#Exchange","RequestServerVersion":"Exchange2013","TimeZoneContext":{"__type":"TimeZoneContext:#Exchange","TimeZoneDefinition":{"__type":"TimeZoneDefinitionType:#Exchange","Id":"Russian Standard Time"}}},"Body":{"__type":"FindConversationRequest:#Exchange","ParentFolderId":{"__type":"TargetFolderId:#Exchange","BaseFolderId":{"__type":"DistinguishedFolderId:#Exchange","Id":"inbox"}},"ConversationShape":{"__type":"ConversationResponseShape:#Exchange","BaseShape":"IdOnly"},"ShapeName":"ConversationListView","Paging":{"__type":"IndexedPageView:#Exchange","BasePoint":"Beginning","Offset":0,"MaxEntriesReturned":25},"ViewFilter":"All","SortOrder":[{"__type":"SortResults:#Exchange","Order":"Descending","Path":{"__type":"PropertyUri:#Exchange","FieldURI":"ConversationLastDeliveryTime"}}]}}"""
    }
}
