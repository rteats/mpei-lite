package com.rteats.mpeineo.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic HTML only: reproduces the HAR's request structure without storing
 * credentials, real verification codes, student IDs or cookies.
 */
class BarsHttpLoginFlowTest {
    private fun loginForm() = """
        <form method="post" action="/bars_web/">
          <input type="hidden" name="__RequestVerificationToken" value="login-token">
          <input type="hidden" name="StopOpenDefault" value="False">
          <input name="Account"><input name="Password" type="password">
          <input type="hidden" name="RememberMe" value="false">
        </form>
    """.trimIndent()

    private fun codeForm(token: String = "code-token") = """
        <form method="post" action="/bars_web/Auth/LoginCode">
          <input type="hidden" name="__RequestVerificationToken" value="$token">
          <input type="hidden" name="StopOpenDefault" value="False">
          <input type="hidden" name="Account" value="fake-student">
          <input type="hidden" name="RememberMe" value="true">
          <input name="AF2_Code" type="text">
        </form>
    """.trimIndent()

    private val grades = """<div id="div-Student_SemesterSheet__Mark"></div>"""
    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private fun redirect(path: String) = MockResponse().setResponseCode(302)
        .addHeader("Location", path)

    @Test fun successfulHarSequencePreservesRedirectQueryAndHiddenFields() = runBlocking {
        val server = MockWebServer()
        val received = mutableListOf<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(received) { received += request }
                return when {
                    request.path == "/bars_web/" && request.method == "GET" -> ok(loginForm())
                    request.path == "/bars_web/" && request.method == "POST" -> ok(codeForm())
                    request.path?.startsWith("/bars_web/Auth/JSON_SendAF2_Code?") == true ->
                        ok("""{"success":true}""")
                    request.path == "/bars_web/Auth/LoginCode" ->
                        redirect("/bars_web/ST_Study/Main/Main?studentID=42")
                    request.path == "/bars_web/ST_Study/Main/Main?studentID=42" -> ok(grades)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val client = BarsHttpClient(root = server.url("/bars_web/"))
            client.openLogin()
            assertTrue(client.credentials("fake-student", "fake-password") is BarsCredentialResult.NeedsCode)
            client.requestCode()
            val page = client.confirm("111111")
            assertEquals("42", page.url.queryParameter("studentID"))
            assertEquals(page.url, client.mainUrl)
            val requests = synchronized(received) { received.toList() }
            assertEquals(5, requests.size)
            val credentials = requests[1].body.readUtf8()
            assertTrue(credentials.contains("Account=fake-student"))
            assertTrue(credentials.contains("Password=fake-password"))
            assertTrue(credentials.contains("RememberMe=true&RememberMe=false"))
            val body = requests[3].body.readUtf8()
            assertTrue(body.contains("Account=fake-student"))
            assertTrue(body.contains("AF2_Code=111111"))
            assertTrue(body.contains("__RequestVerificationToken=code-token"))
            assertEquals(server.url("/bars_web/").toString(),
                requests[4].getHeader("Referer"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun uncheckedRememberLoginSubmitsOnlyTheHiddenFalse() = runBlocking {
        val server = MockWebServer()
        var postBody = ""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/bars_web/" && request.method == "GET" -> ok(loginForm())
                request.path == "/bars_web/" && request.method == "POST" -> {
                    postBody = request.body.readUtf8()
                    ok(codeForm())
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val client = BarsHttpClient(root = server.url("/bars_web/"))
            client.openLogin()
            assertTrue(client.credentials("fake-student", "fake-password",
                rememberSession = false) is BarsCredentialResult.NeedsCode)
            assertTrue(postBody.contains("RememberMe=false"))
            assertFalse(postBody.contains("RememberMe=true"))
            assertEquals(1, "RememberMe=".toRegex().findAll(postBody).count())
        } finally {
            server.shutdown()
        }
    }

    @Test fun intermediateLandingAfterMfaVerifiesAuthenticatedGrades() = runBlocking {
        val server = MockWebServer()
        var submitCount = 0
        var gradesRequests = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/bars_web/" && request.method == "GET" -> ok(loginForm())
                request.path == "/bars_web/" && request.method == "POST" -> ok(codeForm())
                request.path == "/bars_web/Auth/LoginCode" -> {
                    submitCount++
                    redirect("/bars_web/Account/Landing")
                }
                request.path == "/bars_web/Account/Landing" ->
                    ok("<html><p>Account page</p></html>")
                request.path == "/bars_web/ST_Study/Main/Main" -> {
                    gradesRequests++
                    redirect("/bars_web/ST_Study/Main/Main?studentID=42")
                }
                request.path == "/bars_web/ST_Study/Main/Main?studentID=42" -> ok(grades)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val client = BarsHttpClient(root = server.url("/bars_web/"))
            client.openLogin()
            client.credentials("fake-student", "fake-password")
            val page = client.confirm("111111")
            assertEquals("42", page.url.queryParameter("studentID"))
            assertEquals(1, submitCount)
            assertEquals(1, gradesRequests)
        } finally {
            server.shutdown()
        }
    }

    @Test fun codeFormRetryUsesFreshAntiForgeryToken() = runBlocking {
        val server = MockWebServer()
        var confirms = 0
        var secondPost = ""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/bars_web/" && request.method == "GET" -> ok(loginForm())
                request.path == "/bars_web/" && request.method == "POST" -> ok(codeForm())
                request.path == "/bars_web/Auth/LoginCode" -> {
                    confirms++
                    if (confirms == 1) ok(codeForm("retry-token"))
                    else {
                        secondPost = request.body.readUtf8()
                        redirect("/bars_web/ST_Study/Main/Main?studentID=42")
                    }
                }
                request.path == "/bars_web/ST_Study/Main/Main?studentID=42" -> ok(grades)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val client = BarsHttpClient(root = server.url("/bars_web/"))
            client.openLogin()
            client.credentials("fake-student", "fake-password")
            val failure = runCatching { client.confirm("000000") }.exceptionOrNull()
            assertTrue(failure is BarsProtocolException)
            assertEquals(BarsFailureReason.CODE_REJECTED,
                (failure as BarsProtocolException).reason)
            assertEquals(null, client.mainUrl)
            client.confirm("111111")
            assertTrue(secondPost.contains("__RequestVerificationToken=retry-token"))
            assertEquals(2, confirms)
        } finally {
            server.shutdown()
        }
    }

    @Test fun unknownConfirmationResponseWithoutAuthenticatedGradesIsNeverAccepted() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/bars_web/" && request.method == "GET" -> ok(loginForm())
                request.path == "/bars_web/" && request.method == "POST" -> ok(codeForm())
                request.path == "/bars_web/Auth/LoginCode" -> ok("<html>Unknown response</html>")
                request.path == "/bars_web/ST_Study/Main/Main" -> ok(loginForm())
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val client = BarsHttpClient(root = server.url("/bars_web/"))
            client.openLogin()
            client.credentials("fake-student", "fake-password")
            val failure = runCatching { client.confirm("111111") }.exceptionOrNull()
            assertTrue(failure is BarsProtocolException)
            assertEquals(BarsFailureReason.UNEXPECTED_AUTH_PAGE,
                (failure as BarsProtocolException).reason)
            assertEquals(null, client.mainUrl)
        } finally {
            server.shutdown()
        }
    }
}
