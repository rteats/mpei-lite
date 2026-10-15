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

    private fun jsResult(discovery: BarsRouteDiscovery): String {
        val gson = Gson()
        return gson.toJson(gson.toJson(discovery))
    }

    @Test fun officialStudentRoutesPassValidation() {
        assertTrue(BarsAuthenticatedRoutePolicy.isStudentRoute(main, "Main"))
        assertTrue(BarsAuthenticatedRoutePolicy.isStudentRoute(summary, "Summary"))
        assertFalse(BarsAuthenticatedRoutePolicy.isStudentRoute(summary, "Main"))
    }

    @Test fun missingOrUntrustedStudentRoutesAreRejected() {
        listOf(
            "https://bars.mpei.ru/bars_web/ST_Study/Main/Summary",
            "https://example.com/bars_web/ST_Study/Main/Summary?studentID=abc",
            "https://bars.mpei.ru/bars_web/ST_Study/Main/Summary?studentID=",
            "https://bars.mpei.ru/bars_web/ST_Study/Main/Summary?studentID=abc#x",
            "https://bars.mpei.ru.evil.test/bars_web/ST_Study/Main/Summary?studentID=abc",
            "https://bars.mpei.ru/bars_web/ST_Study/Main/Summary?studentID=a&studentID=b",
            "http://bars.mpei.ru/bars_web/ST_Study/Main/Summary?studentID=abc",
        ).forEach { url ->
            assertFalse(url, BarsAuthenticatedRoutePolicy.isStudentRoute(url, "Summary"))
        }
    }

    @Test fun decodesDoubleEncodedJavascriptAndValidatesSameStudent() {
        val discovery = BarsRouteDiscovery(
            main = main, summary = summary, source = "current-url",
            currentParam = true, scopedLinks = 0, distinctIds = 0,
        )
        val parsed = BarsAuthenticatedRoutePolicy.parseJsResult(jsResult(discovery))
        assertEquals("current-url", parsed?.source)
        assertEquals(main, BarsAuthenticatedRoutePolicy.validatedRoutes(parsed)?.main)
        assertEquals(summary, BarsAuthenticatedRoutePolicy.validatedRoutes(parsed)?.summary)
        assertNull(BarsAuthenticatedRoutePolicy.parseJsResult("null"))
        assertNull(BarsAuthenticatedRoutePolicy.parseJsResult("not-json"))
    }

    @Test fun oneStudentObservedFromStudyLinksCanSupplyBothRoutes() {
        val discovery = BarsRouteDiscovery(
            main, summary, source = "scoped-link", scopedLinks = 1, distinctIds = 1,
        )
        assertEquals(main, BarsAuthenticatedRoutePolicy.validatedRoutes(
            BarsAuthenticatedRoutePolicy.parseJsResult(jsResult(discovery)),
        )?.main)
    }

    @Test fun missingOrCrossStudentRoutesAreRejectedEvenIfIndividuallyValid() {
        assertNull(BarsAuthenticatedRoutePolicy.validatedRoutes(BarsRouteDiscovery()))
        val differentStudent = summary.replace("example-123", "another-456")
        assertTrue(BarsAuthenticatedRoutePolicy.isStudentRoute(differentStudent, "Summary"))
        assertNull(BarsAuthenticatedRoutePolicy.validatedRoutes(
            BarsRouteDiscovery(main, differentStudent, source = "menu-main"),
        ))
    }

    @Test fun debugTextDoesNotExposeUrlOrUntrustedSource() {
        val url = "https://bars.mpei.ru/secret?studentID=private-identifier"
        val debug = BarsRouteDiscovery(
            main, summary, source = url, scopedLinks = 30_000,
            distinctIds = 2, mainLinks = 1, summaryLinks = 0,
        ).safeDiagnostics()
        assertTrue(debug.contains("source=unknown"))
        assertTrue(debug.contains("studyLinks=999"))
        assertFalse(debug.contains("private-identifier"))
        assertFalse(debug.contains("studentID"))
        assertFalse(debug.contains("example-123"))
    }

    @Test fun discoveryExaminesCurrentUrlAndScopedLinksWithoutLeakingThemToLogs() {
        assertTrue(BARS_AUTHENTICATED_ROUTES_SCRIPT.contains("window.location.href"))
        assertTrue(BARS_AUTHENTICATED_ROUTES_SCRIPT.contains("querySelectorAll('a[href]')"))
        assertTrue(BARS_AUTHENTICATED_ROUTES_SCRIPT.contains("searchParams.getAll('studentID')"))
        assertTrue(BARS_AUTHENTICATED_ROUTES_SCRIPT.contains("studentIds.size === 1"))
        assertTrue(BARS_AUTHENTICATED_ROUTES_SCRIPT.contains("found.source = 'ambiguous'"))
        assertTrue(BARS_SUMMARY_DIAGNOSTICS_SCRIPT.contains("gradeRows="))
        assertTrue(BARS_SUMMARY_DIAGNOSTICS_SCRIPT.contains("attendanceRows="))
        assertTrue(BARS_SUMMARY_DIAGNOSTICS_SCRIPT.contains("studentParam="))
        assertFalse(BARS_SUMMARY_DIAGNOSTICS_SCRIPT.contains("window.location.href"))
    }
}
