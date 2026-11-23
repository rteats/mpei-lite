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

/** Synthetic fixtures model the deputy group leader's HAR without personal information. */
class BarsDeputyStudentLoginTest {
    private fun login() = """
        <form action="/bars_web/" method="post">
          <input type="hidden" name="__RequestVerificationToken" value="login-token">
          <input name="Account"><input name="Password">
        </form>
    """.trimIndent()

    private fun challenge() = """
        <form action="/bars_web/Auth/LoginCode" method="post">
          <input type="hidden" name="__RequestVerificationToken" value="code-token">
          <input type="hidden" name="Account" value="fictional">
          <input name="AF2_Code">
        </form>
    """.trimIndent()

    private fun menu(includeStudent: Boolean = true) = """
        <html><nav>
          ${if (includeStudent) """<a href="/bars_web/ST/Student/ListStudent">Студенты</a>""" else ""}
          <a href="/bars_web/SG/StudyGroup/ListStudyGroup">Учебные группы</a>
        </nav></html>
    """.trimIndent()

    private fun listStudents(vararg ids: String): String = buildString {
        append("<html><table>")
        ids.forEach { student ->
            append("<tr><td><a href='/bars_web/ST/Student/Main?studentID=")
            append(student)
            append("'>Student</a></td></tr>")
        }
        append("</table></html>")
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private fun redirect(path: String) = MockResponse().setResponseCode(302)
        .addHeader("Location", path)
    private val grades = "<div id='div-Student_SemesterSheet__Mark'></div>"

    @Test fun deputyMenuResolvesOnlyOwnStudentViaListingAndRedirect() = runBlocking {
        val server = MockWebServer()
        val paths = mutableListOf<String>()
        val seenReferers = mutableListOf<String?>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(paths) {
                    paths += "${request.method} ${request.path}"
                    seenReferers += request.getHeader("Referer")
                }
                return when {
                    request.method == "GET" && request.path == "/bars_web/" -> ok(login())
                    request.method == "POST" && request.path == "/bars_web/" -> ok(challenge())
                    request.method == "POST" && request.path == "/bars_web/Auth/LoginCode" ->
                        redirect("/bars_web/Auth/Menu")
                    request.path == "/bars_web/Auth/Menu" -> ok(menu())
                    request.path == "/bars_web/ST/Student/ListStudent" ->
                        ok(listStudents("1001", "1001"))
                    request.path == "/bars_web/ST/Student/Main?studentID=1001" ->
                        redirect("/bars_web/ST_Study/Main/Main?studentID=1001")
                    request.path == "/bars_web/ST_Study/Main/Main?studentID=1001" -> ok(grades)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val client = BarsHttpClient(root = server.url("/bars_web/"))
            client.openLogin()
            assertTrue(client.credentials("fictional", "not-a-real-password") is BarsCredentialResult.NeedsCode)
            val page = client.confirm("000000")
            assertEquals("/bars_web/ST_Study/Main/Main", page.url.encodedPath)
            assertEquals("1001", page.url.queryParameter("studentID"))
            assertEquals(page.url, client.mainUrl)
            val requests = synchronized(paths) { paths.toList() }
            assertEquals(listOf(
                "GET /bars_web/",
                "POST /bars_web/",
                "POST /bars_web/Auth/LoginCode",
                "GET /bars_web/Auth/Menu",
                "GET /bars_web/ST/Student/ListStudent",
                "GET /bars_web/ST/Student/Main?studentID=1001",
                "GET /bars_web/ST_Study/Main/Main?studentID=1001",
            ), requests)
            assertTrue(requests.none { it.contains("StudyGroup") })
            val referers = synchronized(paths) { seenReferers.toList() }
            assertEquals(server.url("/bars_web/Auth/Menu").toString(), referers[4])
            assertEquals(server.url("/bars_web/ST/Student/ListStudent").toString(), referers[5])
        } finally {
            server.shutdown()
        }
    }

    @Test fun directMenuWithoutCodeAlsoResolvesOwnStudent() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/bars_web/" && request.method == "GET" -> ok(login())
                request.path == "/bars_web/" && request.method == "POST" ->
                    redirect("/bars_web/Auth/Menu")
                request.path == "/bars_web/Auth/Menu" -> ok(menu())
                request.path == "/bars_web/ST/Student/ListStudent" ->
                    ok(listStudents("1001"))
                request.path == "/bars_web/ST/Student/Main?studentID=1001" ->
                    redirect("/bars_web/ST_Study/Main/Main?studentID=1001")
                request.path == "/bars_web/ST_Study/Main/Main?studentID=1001" -> ok(grades)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val client = BarsHttpClient(root = server.url("/bars_web/"))
            client.openLogin()
            val result = client.credentials("fictional", "not-a-real-password")
            assertTrue(result is BarsCredentialResult.Complete)
            assertEquals("1001", (result as BarsCredentialResult.Complete).page.url
                .queryParameter("studentID"))
            assertEquals("1001", client.mainUrl?.queryParameter("studentID"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun multipleDistinctStudentsAreNeverChosenAutomatically() = runBlocking {
        val server = MockWebServer()
        var openedStudent = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/bars_web/" && request.method == "GET" -> ok(login())
                request.path == "/bars_web/" && request.method == "POST" -> ok(challenge())
                request.path == "/bars_web/Auth/LoginCode" -> redirect("/bars_web/Auth/Menu")
                request.path == "/bars_web/Auth/Menu" -> ok(menu())
                request.path == "/bars_web/ST/Student/ListStudent" ->
                    ok(listStudents("1001", "1002"))
                request.path?.startsWith("/bars_web/ST/Student/Main?") == true -> {
                    openedStudent = true
                    ok(grades)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val client = BarsHttpClient(root = server.url("/bars_web/"))
            client.openLogin()
            client.credentials("fictional", "not-a-real-password")
            val failure = runCatching { client.confirm("000000") }.exceptionOrNull()
            assertEquals(BarsFailureReason.STUDENT_SELECTION_AMBIGUOUS,
                (failure as BarsProtocolException).reason)
            assertFalse(openedStudent)
            assertEquals(null, client.mainUrl)
        } finally {
            server.shutdown()
        }
    }

    @Test fun groupOnlyMenuDoesNotAccessGroupOrClaimStudentSession() = runBlocking {
        val server = MockWebServer()
        var groupVisited = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/bars_web/" && request.method == "GET" -> ok(login())
                request.path == "/bars_web/" && request.method == "POST" -> ok(challenge())
                request.path == "/bars_web/Auth/LoginCode" -> redirect("/bars_web/Auth/Menu")
                request.path == "/bars_web/Auth/Menu" -> ok(menu(includeStudent = false))
                request.path == "/bars_web/SG/StudyGroup/ListStudyGroup" -> {
                    groupVisited = true
                    ok("<html>Group data</html>")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val client = BarsHttpClient(root = server.url("/bars_web/"))
            client.openLogin()
            client.credentials("fictional", "not-a-real-password")
            val failure = runCatching { client.confirm("000000") }.exceptionOrNull()
            assertEquals(BarsFailureReason.STUDENT_ROUTE_UNAVAILABLE,
                (failure as BarsProtocolException).reason)
            assertFalse(groupVisited)
            assertEquals(null, client.mainUrl)
        } finally {
            server.shutdown()
        }
    }

    @Test fun studentRedirectToDifferentIdentityIsRejected() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/bars_web/" && request.method == "GET" -> ok(login())
                request.path == "/bars_web/" && request.method == "POST" -> ok(challenge())
                request.path == "/bars_web/Auth/LoginCode" -> redirect("/bars_web/Auth/Menu")
                request.path == "/bars_web/Auth/Menu" -> ok(menu())
                request.path == "/bars_web/ST/Student/ListStudent" -> ok(listStudents("1001"))
                request.path == "/bars_web/ST/Student/Main?studentID=1001" ->
                    redirect("/bars_web/ST_Study/Main/Main?studentID=1002")
                request.path == "/bars_web/ST_Study/Main/Main?studentID=1002" -> ok(grades)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val client = BarsHttpClient(root = server.url("/bars_web/"))
            client.openLogin()
            client.credentials("fictional", "not-a-real-password")
            val failure = runCatching { client.confirm("000000") }.exceptionOrNull()
            assertEquals(BarsFailureReason.STUDENT_ROUTE_MISMATCH,
                (failure as BarsProtocolException).reason)
            assertEquals(null, client.mainUrl)
        } finally {
            server.shutdown()
        }
    }
}
