package com.rteats.mpeineo.ui

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsAuthenticatedRoutesTest {
    private val main = "https://bars.mpei.ru/bars_web/ST_Study/Main/Main?studentID=example-123"
    private val summary = "https://bars.mpei.ru/bars_web/ST_Study/Main/Summary?studentID=example-123"

    @Test fun officialStudentRoutesPassValidation() {
        assertTrue(BarsAuthenticatedRoutePolicy.isStudentRoute(main, "Main"))
        assertTrue(BarsAuthenticatedRoutePolicy.isStudentRoute(summary, "Summary"))
        assertFalse(BarsAuthenticatedRoutePolicy.isStudentRoute(summary, "Main"))
    }

    @Test fun missingOrUntrustedStudentRoutesAreRejected() {
        assertFalse(BarsAuthenticatedRoutePolicy.isStudentRoute(
            "https://bars.mpei.ru/bars_web/ST_Study/Main/Summary", "Summary"))
        assertFalse(BarsAuthenticatedRoutePolicy.isStudentRoute(
            "https://example.com/bars_web/ST_Study/Main/Summary?studentID=abc", "Summary"))
        assertFalse(BarsAuthenticatedRoutePolicy.isStudentRoute(
            "https://bars.mpei.ru/bars_web/ST_Study/Main/Summary?studentID=", "Summary"))
        assertFalse(BarsAuthenticatedRoutePolicy.isStudentRoute(
            "https://bars.mpei.ru/bars_web/ST_Study/Main/Summary?studentID=abc#x", "Summary"))
    }

    @Test fun decodesEvaluateJavascriptResponseWithoutLeakingOrMutatingUrls() {
        val gson = Gson()
        val wrappedJsResponse = gson.toJson(gson.toJson(
            BarsAuthenticatedRoutes(main, summary),
        ))
        val routes = BarsAuthenticatedRoutePolicy.parseJsResult(wrappedJsResponse)
        assertEquals(main, routes?.main)
        assertEquals(summary, routes?.summary)
        assertNull(BarsAuthenticatedRoutePolicy.parseJsResult("null"))
        assertNull(BarsAuthenticatedRoutePolicy.parseJsResult("not-json"))
    }

    @Test fun scriptsUseOfficialMenuAndSafeSummaryDiagnostics() {
        assertTrue(BARS_AUTHENTICATED_ROUTES_SCRIPT.contains("querySelectorAll('a[href]')"))
        assertTrue(BARS_AUTHENTICATED_ROUTES_SCRIPT.contains("searchParams.get('studentID')"))
        assertTrue(BARS_SUMMARY_DIAGNOSTICS_SCRIPT.contains("gradeRows="))
        assertTrue(BARS_SUMMARY_DIAGNOSTICS_SCRIPT.contains("attendanceRows="))
        assertTrue(BARS_SUMMARY_DIAGNOSTICS_SCRIPT.contains("studentParam="))
        assertFalse(BARS_SUMMARY_DIAGNOSTICS_SCRIPT.contains("window.location.href"))
    }
}
