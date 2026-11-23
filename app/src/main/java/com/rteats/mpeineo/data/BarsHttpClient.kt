package com.rteats.mpeineo.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class BarsForm(
    val action: HttpUrl,
    val hidden: List<Pair<String, String>>,
)

internal data class BarsHttpPage(
    val url: HttpUrl,
    val status: Int,
    val html: String,
)

internal sealed interface BarsCredentialResult {
    data class NeedsCode(val hint: String = "Введите код подтверждения") : BarsCredentialResult
    data class Complete(val page: BarsHttpPage) : BarsCredentialResult
}

/**
 * A returned login or 2FA form indicates the step was not accepted, but does not
 * establish whether the user typed the wrong value or the server rejected the session.
 */
internal fun barsUnacceptedAuthPage(html: String, codeChallenge: Boolean): BarsProtocolException {
    val field = if (codeChallenge) "AF2_Code" else "Password"
    val reason = if (Jsoup.parse(html).selectFirst("input[name=$field]") != null) {
        if (codeChallenge) BarsFailureReason.CODE_REJECTED else BarsFailureReason.CREDENTIALS_REJECTED
    } else {
        BarsFailureReason.UNEXPECTED_AUTH_PAGE
    }
    return BarsProtocolException(reason)
}

/**
 * Private, in-memory BARS session. No WebView, user-agent scripts, or disk cookies.
 * Passwords and MFA codes are used only as method arguments and never logged.
 */
internal class BarsHttpClient(
    private val store: BarsSessionStore? = null,
    private val root: HttpUrl = "https://bars.mpei.ru/bars_web/".toHttpUrl(),
) {
    private val host = root.host
    private val htmlAccept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    private val jar = object : CookieJar {
        private val cookies = mutableMapOf<String, Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(this) {
                cookies.forEach { cookie ->
                    val key = cookie.name + "|" + cookie.domain + "|" + cookie.path
                    if (cookie.value.isBlank() || cookie.expiresAt <= System.currentTimeMillis()) {
                        this.cookies.remove(key)
                    } else {
                        this.cookies[key] = cookie
                    }
                }
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(this) {
            val now = System.currentTimeMillis()
            cookies.values.filter { it.expiresAt > now && it.matches(url) }
        }
        fun clear() = synchronized(this) { cookies.clear() }
        fun exported(): List<String> = synchronized(this) {
            cookies.values.filter { cookie ->
                cookie.expiresAt > System.currentTimeMillis() &&
                    cookie.value.isNotBlank() &&
                    (cookie.domain == "bars.mpei.ru" ||
                        "bars.mpei.ru".endsWith("." + cookie.domain))
            }.map { it.toString() }
        }
        fun restore(headers: List<String>, url: HttpUrl) = synchronized(this) {
            cookies.clear()
            headers.mapNotNull { Cookie.parse(url, it) }.forEach { cookie ->
                val key = cookie.name + "|" + cookie.domain + "|" + cookie.path
                if (cookie.value.isNotBlank() && cookie.expiresAt > System.currentTimeMillis() &&
                    (cookie.domain == "bars.mpei.ru" ||
                        "bars.mpei.ru".endsWith("." + cookie.domain))) {
                    cookies[key] = cookie
                }
            }
        }
        fun hasSessionCookie(): Boolean = synchronized(this) {
            cookies.values.any {
                it.name == "ses_bars" && it.value.isNotBlank() &&
                    it.expiresAt > System.currentTimeMillis()
            }
        }
    }
    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var credentialsForm: BarsForm? = null
    private var codeForm: BarsForm? = null
    var mainUrl: HttpUrl? = null
        private set
    var summaryUrl: HttpUrl? = null
        private set

    fun clear() {
        store?.clear()
        jar.clear()
        credentialsForm = null
        codeForm = null
        mainUrl = null
        summaryUrl = null
    }

    /** Save only verified, authenticated BARS sessions. All fields are encrypted. */
    private fun saveSession() {
        val main = mainUrl ?: return
        val summary = summaryUrl ?: return
        if (!jar.hasSessionCookie()) return
        runCatching {
            store?.write(BarsSavedSession(
                System.currentTimeMillis(),
                main.toString(), summary.toString(), jar.exported(),
            ))
        }
    }

    /** Returns a verified study page, or null if no saved credentials exist. */
    suspend fun restoreSession(): BarsHttpPage? = withContext(Dispatchers.IO) {
        val saved = store?.read() ?: return@withContext null
        val main = saved.mainUrl.toHttpUrlOrNull()
        val summary = saved.summaryUrl.toHttpUrlOrNull()
        if (main == null || summary == null ||
            runCatching { valid(main); valid(summary) }.isFailure ||
            main.encodedPath != "/bars_web/ST_Study/Main/Main" ||
            summary.encodedPath != "/bars_web/ST_Study/Main/Summary") {
            clear()
            return@withContext null
        }
        jar.restore(saved.cookieHeaders, root)
        if (!jar.hasSessionCookie()) {
            clear()
            return@withContext null
        }
        mainUrl = main
        summaryUrl = summary
        refreshMain()
    }

    private fun valid(url: HttpUrl): HttpUrl {
        if (url.scheme != root.scheme || url.host != host || url.port != root.port ||
            !url.encodedPath.startsWith("/bars_web/")) {
            throw BarsProtocolException(BarsFailureReason.UNEXPECTED_ORIGIN)
        }
        return url
    }

    private fun form(page: BarsHttpPage, field: String): BarsForm? {
        val doc = Jsoup.parse(page.html)
        val element = doc.select("form").firstOrNull {
            it.selectFirst("input[name=$field]") != null
        } ?: return null
        val action = valid(page.url.resolve(element.attr("action").ifBlank { page.url.toString() })
            ?: throw BarsProtocolException(BarsFailureReason.INVALID_FORM_ACTION))
        val hidden = element.select("input[type=hidden][name]").map {
            it.attr("name") to it.attr("value")
        }
        return BarsForm(action, hidden)
    }

    private fun post(form: BarsForm, fields: List<Pair<String, String>>): BarsHttpPage {
        val body = FormBody.Builder().apply {
            form.hidden.forEach { (name, value) -> add(name, value) }
            fields.forEach { (name, value) -> add(name, value) }
        }.build()
        return execute(
            Request.Builder().url(valid(form.action))
                .header("Origin", "https://bars.mpei.ru")
                .header("Referer", root.toString())
                .header("Accept", htmlAccept)
                .post(body).build(),
        )
    }

    private fun execute(request: Request): BarsHttpPage {
        var next = request
        // Browser HAR retains the login-page Referer across the post-MFA 302.
        val navigationReferer = request.header("Referer") ?: root.toString()
        repeat(6) {
            client.newCall(next).execute().use { response: Response ->
                val url = valid(response.request.url)
                if (response.code in 300..399) {
                    val target = response.header("Location")
                        ?.let(url::resolve)?.let(::valid)
                        ?: throw BarsProtocolException(BarsFailureReason.INVALID_REDIRECT)
                    next = Request.Builder().url(target).get()
                        .header("Referer", navigationReferer)
                        .header("Accept", htmlAccept).build()
                } else {
                    if (response.code !in 200..299) {
                        throw BarsProtocolException(BarsFailureReason.HTTP_STATUS, response.code)
                    }
                    val body = response.body?.string()
                        ?: throw BarsProtocolException(BarsFailureReason.EMPTY_RESPONSE)
                    if (body.length > 1_000_000) {
                        throw BarsProtocolException(BarsFailureReason.OVERSIZED_RESPONSE)
                    }
                    return BarsHttpPage(url, response.code, body)
                }
            }
        }
        throw BarsProtocolException(BarsFailureReason.TOO_MANY_REDIRECTS)
    }

    suspend fun openLogin(): Unit = withContext(Dispatchers.IO) {
        clear()
        val page = execute(Request.Builder().url(root)
            .header("Accept", htmlAccept).get().build())
        credentialsForm = form(page, "Password")
            ?: throw BarsProtocolException(BarsFailureReason.LOGIN_FORM_UNAVAILABLE)
    }

    suspend fun credentials(account: String, password: String): BarsCredentialResult =
        withContext(Dispatchers.IO) {
            val login = credentialsForm ?: throw BarsProtocolException(BarsFailureReason.LOGIN_FORM_EXPIRED)
            val page = post(login, listOf(
                "Account" to account,
                "Password" to password,
                // The official form posts this value even when unchecked.
                "RememberMe" to "false",
            ))
            val next = form(page, "AF2_Code")
            if (next != null) {
                codeForm = next
                BarsCredentialResult.NeedsCode()
            } else if (Jsoup.parse(page.html)
                    .getElementById("div-Student_SemesterSheet__Mark") != null) {
                BarsCredentialResult.Complete(page)
            } else {
                throw barsUnacceptedAuthPage(page.html, codeChallenge = false)
            }
        }

    suspend fun requestCode() = withContext(Dispatchers.IO) {
        if (codeForm == null) throw BarsProtocolException(BarsFailureReason.CODE_FORM_EXPIRED)
        // In the successful capture, tid=3 is the code-delivery channel (MAX).
        val url = valid(root.resolve("Auth/JSON_SendAF2_Code")!!)
            .newBuilder().addQueryParameter("tid", "3")
            .addQueryParameter("_", System.currentTimeMillis().toString()).build()
        val result = execute(Request.Builder().url(url)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", root.toString()).get().build())
        // JSON errors must not be mistaken for successful delivery.
        val json = try {
            org.json.JSONObject(result.html)
        } catch (_: org.json.JSONException) {
            throw BarsProtocolException(BarsFailureReason.CODE_DELIVERY_INVALID_JSON)
        }
        if (!json.has("success")) {
            throw BarsProtocolException(BarsFailureReason.CODE_DELIVERY_INVALID_JSON)
        }
        if (!json.optBoolean("success")) {
            throw BarsProtocolException(BarsFailureReason.CODE_SEND_REJECTED)
        }
    }

    suspend fun confirm(code: String): BarsHttpPage = withContext(Dispatchers.IO) {
        val challenge = codeForm ?: throw BarsProtocolException(BarsFailureReason.CODE_FORM_EXPIRED)
        val submitted = post(challenge, listOf("AF2_Code" to code))
        val returned = Jsoup.parse(submitted.html)
        // A returned verification form is not a completed login. Update the
        // anti-forgery token so another attempt can use the same challenge.
        form(submitted, "AF2_Code")?.let {
            codeForm = it
            throw BarsProtocolException(BarsFailureReason.CODE_REJECTED)
        }
        if (returned.selectFirst("input[name=Password]") != null) {
            throw BarsProtocolException(BarsFailureReason.SESSION_NOT_AUTHENTICATED)
        }

        // Successful HAR: LoginCode -> 302 -> Main?studentID=... .
        // If BARS returns an intermediate page for another account, verify
        // the study route with the same in-memory cookies. Never accept an
        // unrelated page as an authenticated student dashboard.
        val page = if (returned.getElementById("div-Student_SemesterSheet__Mark") != null) {
            submitted
        } else {
            val study = valid(root.resolve("ST_Study/Main/Main")!!)
            val verified = execute(Request.Builder().url(study)
                .header("Referer", root.toString())
                .header("Accept", htmlAccept).get().build())
            if (Jsoup.parse(verified.html)
                    .getElementById("div-Student_SemesterSheet__Mark") == null) {
                throw BarsProtocolException(BarsFailureReason.UNEXPECTED_AUTH_PAGE)
            }
            verified
        }
        codeForm = null
        mainUrl = page.url
        summaryUrl = BarsHtmlParser.summaryUrl(page.html, page.url)
        saveSession()
        page
    }

    suspend fun refreshMain(): BarsHttpPage = withContext(Dispatchers.IO) {
        val url = mainUrl ?: throw BarsProtocolException(BarsFailureReason.SESSION_NOT_AUTHENTICATED)
        val page = execute(Request.Builder().url(valid(url))
            .header("Accept", htmlAccept).get().build())
        if (Jsoup.parse(page.html).getElementById("div-Student_SemesterSheet__Mark") == null) {
            // Only clear the stored session if BARS actually redirected to login.
            // HTML changes or network failures must not erase a valid session.
            if (page.url.encodedPath == "/bars_web/" ||
                Jsoup.parse(page.html).selectFirst("input[name=Password]") != null) {
                clear()
                throw BarsProtocolException(BarsFailureReason.SESSION_NOT_AUTHENTICATED)
            }
            throw BarsProtocolException(BarsFailureReason.UNEXPECTED_AUTH_PAGE)
        }
        saveSession()
        page
    }

    suspend fun refreshSummary(): String = withContext(Dispatchers.IO) {
        val url = summaryUrl ?: throw BarsProtocolException(BarsFailureReason.SUMMARY_ROUTE_MISSING)
        val page = execute(Request.Builder().url(valid(url)).get().build())
        BarsHtmlParser.summaryReport(page.html)
    }
}
