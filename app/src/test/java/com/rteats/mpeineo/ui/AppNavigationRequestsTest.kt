package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationRequestsTest {
    @Test fun repeatedBarsRequestsHaveUniqueIdsAndCanBeConsumed() {
        val targets = AppNavigationTargets()
        targets.focusBarsDiscipline("Физика")
        val first = targets.barsSearchRequest
        assertEquals("Физика", targets.barsRequestedDiscipline)
        targets.consumeBarsDiscipline()
        assertNull(targets.barsRequestedDiscipline)
        targets.focusBarsDiscipline("Физика")
        assertTrue(targets.barsSearchRequest > first)
        assertEquals("Физика", targets.barsRequestedDiscipline)
    }

    @Test fun campusFocusKeepsOnlyLatestRequestAndCanBeConsumed() {
        val targets = AppNavigationTargets()
        targets.focusCampus("А", "А-204")
        val first = targets.mapFocus!!
        assertEquals("А-204", first.classroom)
        targets.focusCampus("Б", "Б-100")
        assertTrue(targets.mapFocus!!.id > first.id)
        assertEquals("Б", targets.mapFocus!!.building)
        targets.consumeCampusFocus()
        assertNull(targets.mapFocus)
    }

    @Test fun focusCueOnlyClearsTheMatchingDeepLinkAndCanBeReused() {
        val targets = AppNavigationTargets()
        val lesson = com.rteats.mpeineo.model.Lesson(
            name = "Математика", kind = "Лекция",
            startTime = "10:15", endTime = "11:50", place = "А-101",
            lecturer = "Преподаватель", groups = "А-12",
        )
        val hit = ScheduleLessonHit(0, "2026-10-22", lesson)
        targets.focusScheduleLesson(hit)
        val previous = targets.scheduleLessonFocus!!.requestId
        targets.focusScheduleLesson(hit)
        val latest = targets.scheduleLessonFocus!!.requestId
        assertTrue(latest > previous)
        targets.consumeScheduleFocus(previous)
        assertEquals(latest, targets.scheduleLessonFocus?.requestId)
        targets.consumeScheduleFocus(latest)
        assertNull(targets.scheduleLessonFocus)
        targets.focusScheduleLesson(hit)
        assertTrue(targets.scheduleLessonFocus!!.requestId > latest)
        targets.clearScheduleFocus()
        assertNull(targets.scheduleLessonFocus)
    }

    @Test fun intentsPreserveExplicitNavigationDestinationAndTarget() {
        val service = AppNavigationRequest.Service(AppTab.SETTINGS)
        assertEquals(AppTab.SETTINGS, service.destination)
        val classroom = AppNavigationRequest.FocusCampus("Б", "Б-102", fromSchedule = true)
        assertTrue(classroom.fromSchedule)
        assertEquals("Б-102", classroom.classroom)
    }

    @Test fun clearingScheduleTargetNeverClearsOtherFeatureTargets() {
        val targets = AppNavigationTargets()
        targets.focusBarsDiscipline("Математика")
        targets.focusCampus("В")
        targets.clearScheduleFocus()
        assertEquals("Математика", targets.barsRequestedDiscipline)
        assertEquals("В", targets.mapFocus?.building)
    }
}
