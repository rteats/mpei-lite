package com.rteats.mpeineo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rteats.mpeineo.ui.BarsAuthenticatedRoutePolicy
import com.rteats.mpeineo.ui.BarsPayloadParser
import com.rteats.mpeineo.ui.BarsActivityType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Must run as connectedDevReleaseAndroidTest with -POPTIMIZE_RELEASE=true.
 * A regular JVM test never sees R8's transformed app classes.
 */
@RunWith(AndroidJUnit4::class)
class BarsR8JsonRuntimeTest {
    @Test fun coldStartPageStateIsNotAnAbstractGsonDto() {
        val page = BarsPayloadParser.parsePageState(
            """{"url":"https://bars.mpei.ru/bars_web/ST_Study/Main/Main","path":"/bars_web/st_study/main/main","isLoginPage":false,"isAuthFlow":false,"isStudentList":false,"isMarksPage":true,"isSummaryPage":false,"isSummaryReady":false}"""
        )
        assertTrue(page.isMarksPage)
        assertEquals("/bars_web/st_study/main/main", page.path)
    }

    @Test fun marksPayloadStillDeserializesUnderR8() {
        val marks = BarsPayloadParser.parseExtraction(
            """{"name":"Test Student","group":"Test Group","disciplines":[{"disciplineName":"Test Subject","activities":[{"type":"CONTROL_ACTIVITY","name":"KM-1","weight":"0.2","weekNum":"3","markAndDate":"5"}]}]}"""
        )
        assertEquals("Test Student", marks.name)
        assertEquals("Test Subject", marks.disciplines.single().disciplineName)
        assertEquals(BarsActivityType.CONTROL_ACTIVITY,
            marks.disciplines.single().activities.single().type)
    }

    @Test fun officialSummaryRouteStillDeserializesUnderR8() {
        val main = "https://bars.mpei.ru/bars_web/ST_Study/Main/Main?studentID=test-123"
        val summary = "https://bars.mpei.ru/bars_web/ST_Study/Main/Summary?studentID=test-123"
        val inner = JSONObject()
            .put("main", main).put("summary", summary).put("source", "profile-link")
            .put("currentParam", false).put("profileLinks", 1)
            .put("scopedLinks", 0).put("distinctIds", 1).toString()
        val parsed = BarsAuthenticatedRoutePolicy.parseJsResult(JSONObject.quote(inner))
        assertNotNull(parsed)
        assertEquals(summary, BarsAuthenticatedRoutePolicy.validatedRoutes(parsed)?.summary)
    }
}
