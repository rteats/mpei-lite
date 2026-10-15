package com.rteats.mpeineo.data

import org.json.JSONArray
import org.json.JSONObject

/** Exchange 2013 directory suggestions. Never persist or log directory entries. */
internal data class OwaRecipientSuggestion(val name: String, val email: String)

internal object OwaRecipientDirectory {
    private val emailPattern = Regex("^[^\\s@<>;,]+@[^\\s@<>;,]+\\.[^\\s@<>;,]+$")

    fun token(value: String): String = value.substringAfterLast(',')
        .substringAfterLast(';').substringAfterLast('\n').trim().take(80)

    fun replaceToken(value: String, email: String): String {
        val at = maxOf(value.lastIndexOf(','), value.lastIndexOf(';'), value.lastIndexOf('\n'))
        val prefix = if (at >= 0) value.substring(0, at + 1).trimEnd() + " " else ""
        return prefix + email.trim() + "; "
    }

    fun searchRequest(query: String): JSONObject = JSONObject()
        .put("__type", "FindPeopleRequest:#Exchange")
        .put("QueryString", query.trim().take(80))
        .put("IndexedPageItemView", JSONObject()
            .put("__type", "IndexedPageView:#Exchange")
            .put("BasePoint", "Beginning")
            .put("Offset", 0)
            .put("MaxEntriesReturned", 12))
        .put("ParentFolderId", JSONObject()
            .put("__type", "TargetFolderId:#Exchange")
            .put("BaseFolderId", JSONObject()
                .put("__type", "DistinguishedFolderId:#Exchange")
                .put("Id", "directory")))
        .put("PersonaShape", JSONObject()
            .put("__type", "PersonaResponseShape:#Exchange")
            .put("BaseShape", "Default"))
        .put("ShouldResolveOneOffEmailAddress", false)
        .put("SearchPeopleSuggestionIndex", true)

    fun parse(response: JSONObject): List<OwaRecipientSuggestion> {
        val body = response.optJSONObject("Body") ?: return emptyList()
        val rows = body.optJSONArray("ResultSet") ?: body.optJSONArray("People")
            ?: return emptyList()
        val seen = HashSet<String>()
        return (0 until rows.length()).mapNotNull { index ->
            val persona = rows.optJSONObject(index) ?: return@mapNotNull null
            val emails = persona.optJSONArray("EmailAddresses")
            val firstAddress = emails?.opt(0)
            val nested = persona.optJSONObject("EmailAddress")
            val email = when {
                nested != null -> nested.optString("EmailAddress")
                    .ifBlank { nested.optString("Address") }
                persona.opt("EmailAddress") is String -> persona.optString("EmailAddress")
                firstAddress is JSONObject -> firstAddress.optString("EmailAddress")
                    .ifBlank { firstAddress.optString("Address") }
                firstAddress is String -> firstAddress
                else -> persona.optString("PrimarySmtpAddress")
            }.trim()
            if (!emailPattern.matches(email) || !seen.add(email.lowercase())) return@mapNotNull null
            OwaRecipientSuggestion(
                name = persona.optString("DisplayName").ifBlank { email },
                email = email,
            )
        }.take(8)
    }

    /** Offline fallback only for actual addresses, never display-name guesses. */
    fun cachedAddresses(query: String, candidateAddresses: List<String>): List<OwaRecipientSuggestion> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return candidateAddresses.asSequence()
            .map(String::trim)
            .filter { emailPattern.matches(it) && it.contains(q, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(8)
            .map { OwaRecipientSuggestion(it, it) }
            .toList()
    }
}
