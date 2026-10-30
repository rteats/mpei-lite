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
import java.io.IOException
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
 * Private, in-memory BARS session. No WebView, user-agent scripts, or disk cookies.
 * Passwords and MFA codes are used only as method arguments and never logged.
 */
internal class BarsHttpClient(private val store: BarsSessionStore? = null) {
    private val host = "bars.mpei.ru"
    private val root = "https://bars.mpei.ru/bars_web/".toHttpUrl()
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
        require(url.scheme == "https" && url.host == host &&
            url.port == 443 && url.encodedPath.startsWith("/bars_web/")) {
            "BARS requested an unexpected origin or path"
        }
        return url
    }

    private fun form(page: BarsHttpPage, field: String): BarsForm? {
        val doc = Jsoup.parse(page.html)
        val element = doc.select("form").firstOrNull {
            it.selectFirst("input[name=$field]") != null
        } ?: return null
        val action = valid(page.url.resolve(element.attr("action").ifBlank { page.url.toString() })
            ?: error("Invalid BARS form action"))
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
                .post(body).build(),
        )
    }

    private fun execute(request: Request): BarsHttpPage {
        var next = request
        repeat(6) {
            client.newCall(next).execute().use { response: Response ->
                val url = valid(response.request.url)
                if (response.code in 300..399) {
                    val target = response.header("Location")
                        ?.let(url::resolve)?.let(::valid)
                        ?: throw IOException("BARS redirect has no valid target")
                    next = Request.Builder().url(target).get()
                        .header("Referer", url.toString()).build()
                } else {
                    if (response.code !in 200..299) {
                        throw IOException("BARS HTTP ${response.code}")
                    }
                    val body = response.body?.string()
                        ?: throw IOException("Empty BARS response")
                    if (body.length > 1_000_000) {
                        throw IOException("BARS response exceeds expected size")
                    }
                    return BarsHttpPage(url, response.code, body)
                }
            }
        }
        throw IOException("Too many BARS redirects")
    }

    suspend fun openLogin(): Unit = withContext(Dispatchers.IO) {
        clear()
        val page = execute(Request.Builder().url(root).get().build())
        credentialsForm = form(page, "Password")
            ?: throw IOException("BARS login form is unavailable")
    }

    suspend fun credentials(account: String, password: String): BarsCredentialResult =
        withContext(Dispatchers.IO) {
            val login = credentialsForm ?: throw IOException("BARS login form has expired")
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
                throw IOException("BARS did not accept the credentials")
            }
        }

    suspend fun requestCode() = withContext(Dispatchers.IO) {
        check(codeForm != null) { "Two-factor confirmation not started" }
        // In the successful capture, tid=3 is the code-delivery channel (MAX).
        val url = valid(root.resolve("Auth/JSON_SendAF2_Code")!!)
            .newBuilder().addQueryParameter("tid", "3")
            .addQueryParameter("_", System.currentTimeMillis().toString()).build()
        val result = execute(Request.Builder().url(url)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", root.toString()).get().build())
        // JSON errors must not be mistaken for successful delivery.
        val json = org.json.JSONObject(result.html)
        if (!json.optBoolean("success")) throw IOException("BARS could not send the code")
    }

    suspend fun confirm(code: String): BarsHttpPage = withContext(Dispatchers.IO) {
        val form = codeForm ?: throw IOException("BARS confirmation form has expired")
        val page = post(form, listOf("AF2_Code" to code))
        if (Jsoup.parse(page.html).getElementById("div-Student_SemesterSheet__Mark") == null) {
            throw IOException("BARS rejected the confirmation code")
        }
        codeForm = null
        mainUrl = page.url
        summaryUrl = BarsHtmlParser.summaryUrl(page.html, page.url)
        saveSession()
        page
    }

    suspend fun refreshMain(): BarsHttpPage = withContext(Dispatchers.IO) {
        val url = mainUrl ?: throw IOException("Sign in to BARS first")
        val page = execute(Request.Builder().url(valid(url)).get().build())
        if (Jsoup.parse(page.html).getElementById("div-Student_SemesterSheet__Mark") == null) {
            // Only clear the stored session if BARS actually redirected to login.
            // HTML changes or network failures must not erase a valid session.
            if (page.url.encodedPath == "/bars_web/" ||
                Jsoup.parse(page.html).selectFirst("input[name=Password]") != null) {
                clear()
            }
            throw IOException("BARS study page is not authenticated")
        }
        saveSession()
        page
    }

    suspend fun refreshSummary(): String = withContext(Dispatchers.IO) {
        val url = summaryUrl ?: throw IOException("BARS summary route is missing")
        val page = execute(Request.Builder().url(valid(url)).get().build())
        BarsHtmlParser.summaryReport(page.html)
    }
}
