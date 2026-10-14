package com.rteats.mpeineo.data

import android.content.Context
import android.os.SystemClock
import android.text.Html
import android.webkit.CookieManager
import android.webkit.WebSettings
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only OWA service.svc client for the MPEI Exchange 2013-compatible server.
 *
 * WebView owns interactive authentication. This client borrows only its current
 * cookies and anti-CSRF canary; nothing is stored or printed. Request shapes
 * are based on captured OWA FindConversation / GetConversationItems traffic.
 *
 * Do not use IMAP UIDs to address Exchange conversations or message bodies.
 * Sending, read flags, attachments and background polling are deliberately
 * outside this first implementation.
 */
class OwaMailRepository(
    private val context: Context,
    private val diagnostics: DiagnosticLog,
) {
    class LoginRequired : IllegalStateException("Откройте ОСЭП и войдите в почту.")
    class ServiceFailure(val category: String) :
        IllegalStateException("Не удалось получить почту через ОСЭП (" + category + ").")

    private val mutex = Mutex()
    private data class Session(val cookieHeader: String, val canary: String, val userAgent: String)
    private data class Reply(val status: Int, val body: String, val setCookies: List<String>)

    suspend fun inbox(maxConversations: Int = 60, refreshId: Long = 0L): List<MailSummary> {
        val started = SystemClock.elapsedRealtime()
        return try {
            val body = JSONObject()
                .put("__type", "FindConversationRequest:#Exchange")
                .put("ParentFolderId", JSONObject()
                    .put("__type", "TargetFolderId:#Exchange")
                    .put("BaseFolderId", JSONObject()
                        .put("__type", "DistinguishedFolderId:#Exchange")
                        .put("Id", "inbox")))
                .put("ConversationShape", JSONObject()
                    .put("__type", "ConversationResponseShape:#Exchange")
                    .put("BaseShape", "IdOnly"))
                .put("ShapeName", "ConversationListView")
                .put("Paging", JSONObject()
                    .put("__type", "IndexedPageView:#Exchange")
                    .put("BasePoint", "Beginning")
                    .put("Offset", 0)
                    .put("MaxEntriesReturned", maxConversations.coerceIn(1, 100)))
                .put("ViewFilter", "All")
                .put("SortOrder", JSONArray().put(JSONObject()
                    .put("__type", "SortResults:#Exchange")
                    .put("Order", "Descending")
                    .put("Path", JSONObject()
                        .put("__type", "PropertyUri:#Exchange")
                        .put("FieldURI", "ConversationLastDeliveryTime"))))

            val result = service("FindConversation", "Browse_All", body, refreshId)
            val responseBody = result.optJSONObject("Body") ?: throw ServiceFailure("missing_body")
            verifyResponse(responseBody)
            val conversations = responseBody.optJSONArray("Conversations")
                ?: throw ServiceFailure("missing_conversations")
            val items = (0 until conversations.length()).mapNotNull { index ->
                val item = conversations.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optJSONObject("ConversationId")?.optString("Id").orEmpty()
                if (id.isEmpty()) return@mapNotNull null
                val senders = item.optJSONArray("UniqueSenders")
                MailSummary(
                    uid = stableId(id),
                    uidValidity = OWA_UID_VALIDITY,
                    sender = senders?.optString(0).orEmpty(),
                    subject = item.optString("ConversationTopic"),
                    sentAt = parseDate(item.optString("LastDeliveryTime")),
                    unread = item.optInt("UnreadCount", 0) > 0,
                    hasAttachment = item.optBoolean("HasAttachments"),
                    owaConversationId = id,
                )
            }
            diagnostics.log("OWA_MAIL",
                "inbox_success refreshId=" + refreshId +
                    " conversations=" + items.size +
                    " durationMs=" + (SystemClock.elapsedRealtime() - started))
            items
        } catch (e: Exception) {
            diagnostics.log("OWA_MAIL",
                "inbox_failure refreshId=" + refreshId +
                    " category=" + when (e) {
                        is LoginRequired -> "LOGIN_REQUIRED"
                        is ServiceFailure -> e.category
                        else -> e.javaClass.simpleName
                    } +
                    " durationMs=" + (SystemClock.elapsedRealtime() - started))
            throw e
        }
    }

    suspend fun message(summary: MailSummary): MailDetail {
        val id = summary.owaConversationId?.takeIf { it.isNotBlank() }
            ?: throw ServiceFailure("missing_conversation_id")
        val body = JSONObject()
            .put("__type", "GetConversationItemsRequest:#Exchange")
            .put("Conversations", JSONArray().put(JSONObject()
                .put("__type", "ConversationRequestType:#Exchange")
                .put("ConversationId", JSONObject()
                    .put("__type", "ItemId:#Exchange")
                    .put("Id", id))
                .put("SyncState", "")))
            .put("ItemShape", JSONObject()
                .put("__type", "ItemResponseShape:#Exchange")
                .put("BaseShape", "IdOnly")
                .put("FilterHtmlContent", true)
                .put("BlockExternalImagesIfSenderUntrusted", true)
                .put("AddBlankTargetToLinks", true)
                .put("ClientSupportsIrm", false)
                .put("MaximumBodySize", 2097152)
                .put("MaximumRecipientsToReturn", 10))
            .put("ShapeName", "ItemPartUniqueBody")
            .put("SortOrder", "DateOrderDescending")
            .put("MaxItemsToReturn", 20)
        val result = service("GetConversationItems", "GetConversationItemsAction", body)
        val response = result.optJSONObject("Body")
            ?.optJSONObject("ResponseMessages")
            ?.optJSONArray("Items")
            ?.optJSONObject(0) ?: throw ServiceFailure("missing_message_response")
        verifyResponse(response)
        val nodes = response.optJSONObject("Conversation")?.optJSONArray("ConversationNodes")
            ?: throw ServiceFailure("missing_message_nodes")
        var selected: JSONObject? = null
        for (i in 0 until nodes.length()) {
            val entries = nodes.optJSONObject(i)?.optJSONArray("Items") ?: continue
            for (j in 0 until entries.length()) {
                val item = entries.optJSONObject(j) ?: continue
                if (selected == null ||
                    parseDate(item.optString("DateTimeReceived")) >
                        parseDate(selected.optString("DateTimeReceived"))) selected = item
            }
        }
        val item = selected ?: throw ServiceFailure("empty_conversation")
        val content = item.optJSONObject("UniqueBody")
        val rawBody = content?.optString("Value").orEmpty()
        val plainBody = if (content?.optString("BodyType").equals("HTML", true)) {
            Html.fromHtml(rawBody, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } else rawBody
        val mailbox = item.optJSONObject("From")?.optJSONObject("Mailbox")
            ?: item.optJSONObject("Sender")?.optJSONObject("Mailbox")
        val sender = mailbox?.optString("Name")?.takeIf { it.isNotBlank() }
            ?: mailbox?.optString("EmailAddress").orEmpty()
        val detail = MailDetail(
            uid = summary.uid,
            sender = sender.ifBlank { summary.sender },
            subject = item.optString("Subject").ifBlank { summary.subject },
            sentAt = parseDate(item.optString("DateTimeReceived"))
                .takeIf { it > 0 } ?: summary.sentAt,
            body = plainBody,
            attachments = emptyList(),
        )
        diagnostics.log("OWA_MAIL",
            "message_success bodyPresent=" + plainBody.isNotBlank() +
                " truncated=" + (content?.optBoolean("IsTruncated") ?: false))
        return detail
    }

    private suspend fun service(
        action: String,
        actionName: String,
        body: JSONObject,
        refreshId: Long = 0L,
    ): JSONObject = mutex.withLock {
        val session = withContext(Dispatchers.Main) {
            val cookies = CookieManager.getInstance().getCookie(OWA_URL).orEmpty()
            if (cookies.isEmpty()) throw LoginRequired()
            val canary = cookies.split(';').asSequence().map { it.trim() }
                .firstOrNull { it.substringBefore('=').equals("X-OWA-CANARY", true) }
                ?.substringAfter('=', "")?.takeIf { it.isNotBlank() }
                ?: throw ServiceFailure("missing_canary")
            Session(cookies, canary, WebSettings.getDefaultUserAgent(context))
        }
        val payload = JSONObject()
            .put("__type", action + "JsonRequest:#Exchange")
            .put("Header", JSONObject()
                .put("__type", "JsonRequestHeaders:#Exchange")
                .put("RequestServerVersion", "Exchange2013")
                .put("TimeZoneContext", JSONObject()
                    .put("__type", "TimeZoneContext:#Exchange")
                    .put("TimeZoneDefinition", JSONObject()
                        .put("__type", "TimeZoneDefinitionType:#Exchange")
                        .put("Id", "Russian Standard Time"))))
            .put("Body", body)
        val reply = withContext(Dispatchers.IO) {
            request(action, actionName, session, payload.toString())
        }
        if (reply.setCookies.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                val jar = CookieManager.getInstance()
                reply.setCookies.forEach { jar.setCookie(OWA_URL, it) }
                jar.flush()
            }
        }
        if (reply.status in setOf(301, 302, 303, 307, 308, 401, 440))
            throw LoginRequired()
        if (reply.status != 200) throw ServiceFailure("http_" + reply.status)
        val json = try { JSONObject(reply.body) } catch (_: Exception) {
            throw ServiceFailure("invalid_json")
        }
        if (json.optJSONObject("Body") == null)
            throw ServiceFailure("unexpected_response")
        if (action == "FindConversation") {
            diagnostics.log("OWA_AUTH",
                "service_auth source=inbox_refresh refreshId=" + refreshId +
                    " state=AUTHENTICATED http=200")
        }
        json
    }

    private fun request(
        action: String,
        actionName: String,
        session: Session,
        payload: String,
    ): Reply {
        val actionId = if (action == "FindConversation") "-5" else "-8"
        val path = OWA_URL + "service.svc?action=" + action +
            "&ID=" + actionId + "&AC=1"
        val connection = URL(path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Cookie", session.cookieHeader)
            connection.setRequestProperty("X-OWA-CANARY", session.canary)
            connection.setRequestProperty("Action", action)
            connection.setRequestProperty("X-OWA-ActionName", actionName)
            connection.setRequestProperty("X-OWA-ActionId", actionId)
            connection.setRequestProperty("X-OWA-Attempt", "1")
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Origin", "https://mail.mpei.ru")
            connection.setRequestProperty("Referer", OWA_URL)
            connection.setRequestProperty("User-Agent", session.userAgent)
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val renewed = connection.headerFields
                .filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
                .values.flatMap { it.orEmpty() }
            val response = if (status == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else ""
            return Reply(status, response, renewed)
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyResponse(body: JSONObject) {
        if (body.optString("ResponseCode", "NoError") != "NoError" ||
            body.optString("ResponseClass", "Success") != "Success") {
            throw ServiceFailure("exchange_response")
        }
    }

    private fun stableId(id: String): Long {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray(Charsets.UTF_8))
        var number = 0L
        for (i in 0 until 8) number = (number shl 8) or (digest[i].toLong() and 255L)
        return (number and Long.MAX_VALUE).coerceAtLeast(1L)
    }

    private fun parseDate(input: String): Long =
        runCatching { Instant.parse(input).toEpochMilli() }.getOrDefault(0L)

    companion object {
        private const val OWA_URL = "https://mail.mpei.ru/owa/"
        private const val OWA_UID_VALIDITY = 0x4F5741L
    }
}
