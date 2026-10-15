package com.rteats.mpeineo.ui

import com.google.gson.Gson
import java.net.URI

/**
 * BARS personal-study routes require a studentID parameter from its OWN
 * authenticated navigation. Never log URLs or the parameter value.
 */
internal data class BarsAuthenticatedRoutes(
    val main: String = "",
    val summary: String = "",
)

internal object BarsAuthenticatedRoutePolicy {
    private val gson = Gson()

    fun isStudentRoute(url: String, page: String): Boolean = runCatching {
        if (page != "Main" && page != "Summary") return@runCatching false
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("bars.mpei.ru", ignoreCase = true) &&
            uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null &&
            uri.path == "/bars_web/ST_Study/Main/$page" &&
            uri.rawQuery?.split('&')?.any { part ->
                val name = part.substringBefore('=')
                val value = part.substringAfter('=', "")
                name == "studentID" && value.isNotBlank() &&
                    value.length <= 128 && value.matches(Regex("[A-Za-z0-9%-]+"))
            } == true
    }.getOrDefault(false)

    /** WebView.evaluateJavascript JSON-encodes the JS string result once more. */
    fun parseJsResult(result: String?): BarsAuthenticatedRoutes? = runCatching {
        val payload = gson.fromJson(result, String::class.java) ?: return@runCatching null
        val routes = gson.fromJson(payload, BarsAuthenticatedRoutes::class.java)
            ?: return@runCatching null
        routes.takeIf {
            isStudentRoute(it.main, "Main") && isStudentRoute(it.summary, "Summary")
        }
    }.getOrNull()
}
