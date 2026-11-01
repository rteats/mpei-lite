package com.rteats.mpeineo.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Extract the live, same-origin HTML login form instead of assuming its field names.
 * If OWA changes to SSO, JavaScript or another factor, use the interactive browser.
 */
internal data class OwaLoginForm(
    val action: HttpUrl,
    val fields: List<Pair<String, String>>,
    val usernameField: String,
    val passwordField: String,
) {
    fun body(username: String, password: String): FormBody =
        FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
            add(usernameField, username)
            add(passwordField, password)
        }.build()
}

/**
 * MPEI OWA uses /CookieAuth.dll to collect credentials, then /owa/ for mail.
 * Restrict all navigation to the observed routes on the exact HTTPS origin.
 */
internal fun isTrustedOwaAuthUrl(url: HttpUrl): Boolean =
    url.isHttps && url.host.equals("mail.mpei.ru", ignoreCase = true) &&
        url.port == 443 && (
            url.encodedPath.equals("/CookieAuth.dll", ignoreCase = true) ||
            url.encodedPath.equals("/owa", ignoreCase = true) ||
            url.encodedPath.startsWith("/owa/", ignoreCase = true)
        )

internal fun parseOwaLoginForm(html: String, pageUrl: HttpUrl): OwaLoginForm? {
    val page = Jsoup.parse(html, pageUrl.toString())
    val password = page.selectFirst("input[type=password][name]") ?: return null
    val form = password.closest("form") ?: return null
    if (!form.attr("method").equals("post", ignoreCase = true)) return null
    val action = pageUrl.resolve(form.attr("action").ifBlank { pageUrl.toString() })
        ?: return null
    // CookieAuth.dll is the observed login endpoint, outside /owa/.
    if (!isTrustedOwaAuthUrl(pageUrl) || !isTrustedOwaAuthUrl(action) ||
        !action.encodedPath.equals("/CookieAuth.dll", ignoreCase = true)) return null

    val inputs = form.select("input[name]")
    val user = inputs.firstOrNull {
        it !== password && it.attr("name")
            .contains(Regex("user|login|account|email", RegexOption.IGNORE_CASE)) &&
            it.attr("type").lowercase() in setOf("text", "email", "")
    } ?: inputs.firstOrNull {
        it !== password && it.attr("type").lowercase() in setOf("text", "email")
    } ?: return null
    val extra = inputs.mapNotNull { input ->
        if (input === password || input === user || input.hasAttr("disabled")) return@mapNotNull null
        val kind = input.attr("type").lowercase()
        when {
            kind == "hidden" -> input.attr("name") to input.attr("value")
            (kind == "checkbox" || kind == "radio") && input.hasAttr("checked") ->
                input.attr("name") to input.attr("value").ifBlank { "on" }
            else -> null
        }
    }
    return OwaLoginForm(action, extra, user.attr("name"), password.attr("name"))
}

internal enum class OwaLoginResult { SUCCESS, REJECTED, USE_BROWSER }

/**
 * No password storage, telemetry or WebView JavaScript. A successful login is
 * proved using the OWA sessiondata endpoint before we publish cookies to the
 * shared WebView jar used by native mail and the optional portal.
 */
internal class OwaNativeLogin(context: Context) {
    private val application = context.applicationContext
    private val mutex = Mutex()

    suspend fun login(username: String, password: String): OwaLoginResult = mutex.withLock {
        require(username.isNotBlank() && password.isNotBlank())
        val agent = withContext(Dispatchers.Main) {
            WebSettings.getDefaultUserAgent(application)
        }
        val result = withContext(Dispatchers.IO) { authenticate(username, password, agent) }
        if (result.first == OwaLoginResult.SUCCESS) {
            withContext(Dispatchers.Main) {
                val manager = CookieManager.getInstance()
                manager.setAcceptCookie(true)
                result.second.forEach { cookie ->
                    // Preserve HttpOnly, Secure, expiry and path attributes.
                    manager.setCookie(OWA_ROOT.toString(), cookie.toString())
                }
                manager.flush()
            }
        }
        result.first
    }

    private fun authenticate(
        username: String, password: String, agent: String,
    ): Pair<OwaLoginResult, List<Cookie>> {
        val jar = TemporaryCookieJar()
        val client = OkHttpClient.Builder()
            .cookieJar(jar)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        val (url, formHtml) = getLoginPage(client, agent)
            ?: return OwaLoginResult.USE_BROWSER to emptyList()
        val form = parseOwaLoginForm(formHtml, url)
            ?: return OwaLoginResult.USE_BROWSER to emptyList()
        val request = Request.Builder().url(form.action)
            .header("User-Agent", agent)
            .header("Origin", "https://mail.mpei.ru")
            .header("Referer", url.toString())
            .post(form.body(username, password)).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403)
                return OwaLoginResult.REJECTED to emptyList()
            if (response.code >= 500) throw IOException("OWA login server unavailable")
            if (response.isRedirect && !sameOriginRedirect(response))
                return OwaLoginResult.USE_BROWSER to emptyList()
        }
        // Some deployments establish extra cookies on the landing page.
        // Load OWA once to collect any landing cookies. Its full HTML is not
        // needed: a signed-in mailbox page can be much larger than the login form.
        if (!primeLandingPage(client, agent))
            return OwaLoginResult.USE_BROWSER to emptyList()

        val verified = client.newCall(
            Request.Builder().url(SESSION_DATA)
                .header("User-Agent", agent)
                .header("Origin", "https://mail.mpei.ru")
                .header("Referer", OWA_ROOT.toString())
                .header("Accept", "*/*")
                .post(byteArrayOf().toRequestBody(null)).build(),
        ).execute().use { response ->
            if (response.code in setOf(401, 403, 440) || response.isRedirect) false
            else if (!response.isSuccessful) throw IOException("OWA session check failed")
            else {
                val body = response.peekBody(2_000_001).string()
                if (body.length > 2_000_000) throw IOException("OWA session response too large")
                runCatching {
                    JSONObject(body).optJSONObject("owaUserConfig")
                        ?.optJSONObject("SessionSettings") != null
                }.getOrDefault(false)
            }
        }
        return if (verified) OwaLoginResult.SUCCESS to jar.snapshot()
        else OwaLoginResult.REJECTED to emptyList()
    }

    private fun primeLandingPage(client: OkHttpClient, agent: String): Boolean {
        var url = OWA_ROOT
        repeat(8) {
            client.newCall(
                Request.Builder().url(url).header("User-Agent", agent).get().build(),
            ).execute().use { response ->
                if (!response.isRedirect) {
                    if (!response.isSuccessful) throw IOException("OWA landing page unavailable")
                    return true
                }
                if (!sameOriginRedirect(response)) return false
                url = url.resolve(response.header("Location").orEmpty()) ?: return false
            }
        }
        return false
    }

    private fun getLoginPage(client: OkHttpClient, agent: String): Pair<HttpUrl, String>? {
        var url = OWA_ROOT
        repeat(8) {
            val request = Request.Builder().url(url).header("User-Agent", agent).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    if (!sameOriginRedirect(response)) return null
                    url = url.resolve(response.header("Location").orEmpty()) ?: return null
                } else {
                    if (!response.isSuccessful) throw IOException("OWA login page unavailable")
                    val html = response.peekBody(1_000_001).string()
                    if (html.length > 1_000_000) return null
                    return url to html
                }
            }
        }
        return null
    }

    private fun sameOriginRedirect(response: Response): Boolean {
        val redirected = response.request.url.resolve(response.header("Location").orEmpty())
            ?: return false
        return isTrustedOwaAuthUrl(redirected)
    }

    private class TemporaryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
            synchronized(cookies) {
                incoming.forEach { fresh ->
                    cookies.removeAll { old -> old.name == fresh.name &&
                        old.domain == fresh.domain && old.path == fresh.path }
                    cookies.add(fresh)
                }
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
            cookies.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
        }
        fun snapshot(): List<Cookie> = synchronized(cookies) {
            cookies.filter { it.expiresAt > System.currentTimeMillis() }.toList()
        }
    }

    private companion object {
        val OWA_ROOT = "https://mail.mpei.ru/owa/".toHttpUrl()
        val SESSION_DATA = "https://mail.mpei.ru/owa/sessiondata.ashx?appcacheclient=0".toHttpUrl()
    }
}
