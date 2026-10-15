package com.rteats.mpeineo.ui

import com.google.gson.Gson
import java.net.URI

/**
 * Student-scoped BARS routes are derived only from the current authenticated
 * document or official same-origin study links. Never log the student ID or URLs.
 */
internal data class BarsAuthenticatedRoutes(
    val main: String = "",
    val summary: String = "",
)

/** Diagnostic counters are intentionally devoid of URL, ID and page content. */
internal data class BarsRouteDiscovery(
    val main: String = "",
    val summary: String = "",
    val source: String = "missing",
    val currentParam: Boolean = false,
    val scopedLinks: Int = 0,
    val profileLinks: Int = 0,
    val distinctIds: Int = 0,
    val mainLinks: Int = 0,
    val summaryLinks: Int = 0,
) {
    fun safeDiagnostics(): String {
        val safeSource = when (source) {
            "current-url", "menu-main", "menu-summary", "scoped-link", "profile-link",
            "ambiguous", "missing" -> source
            else -> "unknown"
        }
        return "source=$safeSource currentParam=$currentParam " +
            "studyLinks=${scopedLinks.coerceIn(0, 999)} " +
            "profileLinks=${profileLinks.coerceIn(0, 999)} " +
            "distinctIds=${distinctIds.coerceIn(0, 999)} " +
            "mainLinks=${mainLinks.coerceIn(0, 999)} " +
            "summaryLinks=${summaryLinks.coerceIn(0, 999)}"
    }
}

internal object BarsAuthenticatedRoutePolicy {
    private val gson = Gson()
    private val idPattern = Regex("[A-Za-z0-9%-]{1,128}")

    private fun studentId(url: String): String? = runCatching {
        val parts = URI(url).rawQuery?.split('&') ?: return@runCatching null
        val ids = parts.filter { it.substringBefore('=') == "studentID" }
        if (ids.size != 1) return@runCatching null
        ids.single().substringAfter('=', "").takeIf { it.matches(idPattern) }
    }.getOrNull()

    fun isStudentRoute(url: String, page: String): Boolean = runCatching {
        if (page != "Main" && page != "Summary") return@runCatching false
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("bars.mpei.ru", ignoreCase = true) &&
            uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null &&
            uri.path == "/bars_web/ST_Study/Main/$page" &&
            studentId(url) != null
    }.getOrDefault(false)

    /** JS evaluation encodes the returned JSON string one additional time. */
    fun parseJsResult(result: String?): BarsRouteDiscovery? = runCatching {
        val payload = gson.fromJson(result, String::class.java) ?: return@runCatching null
        gson.fromJson(payload, BarsRouteDiscovery::class.java)
    }.getOrNull()

    /** Refuse even syntactically valid routes when their student IDs disagree. */
    fun validatedRoutes(discovery: BarsRouteDiscovery?): BarsAuthenticatedRoutes? {
        if (discovery == null ||
            !isStudentRoute(discovery.main, "Main") ||
            !isStudentRoute(discovery.summary, "Summary") ||
            studentId(discovery.main) != studentId(discovery.summary)
        ) return null
        return BarsAuthenticatedRoutes(discovery.main, discovery.summary)
    }
}
