package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsGradeChangesTest {
    private fun control(name: String, grade: String?, week: String = "3") = BarsActivity(
        type = BarsActivityType.CONTROL_ACTIVITY,
        name = name, weekNum = week, markAndDate = grade,
    )
    private fun snapshot(
        activities: List<BarsActivity>,
        semester: String = "Осень",
        name: String = "Студент",
    ) = BarsExtractionPayload(
        name = name, group = "А-12", semester = semester,
        disciplines = listOf(BarsDiscipline(disciplineName = "Физика", activities = activities)),
    )

    @Test fun baselineMustNotNotify() {
        assertTrue(detectBarsGradeChanges(null, snapshot(listOf(control("КМ 1", "5")))).isEmpty())
    }

    @Test fun newAndCorrectedGrades() {
        val before = snapshot(listOf(control("КМ 1", "3 (01.10)"), control("КМ 2", null)))
        val after = snapshot(listOf(control("КМ 1", "4 (10.10)"), control("КМ 2", "5")))
        val changes = detectBarsGradeChanges(before, after).getValue("Физика")
        assertEquals(2, changes.size)
        assertEquals(1, changes.count { it.isNew })
        assertEquals("Новых: 1 · исправлено: 1", gradeChangesCaption(changes))
    }

    @Test fun dateOnlyModificationIsIgnored() {
        val before = snapshot(listOf(control("КМ 1", "5 (01.10)")))
        val after = snapshot(listOf(control("КМ 1", "5 (10.10)")))
        assertTrue(detectBarsGradeChanges(before, after).isEmpty())
    }

    @Test fun differentStudentAndSemesterIgnored() {
        val before = snapshot(listOf(control("КМ 1", null)))
        assertTrue(detectBarsGradeChanges(before,
            snapshot(listOf(control("КМ 1", "4")), semester = "Весна")).isEmpty())
        assertTrue(detectBarsGradeChanges(before,
            snapshot(listOf(control("КМ 1", "4")), name = "Другой")).isEmpty())
    }

    @Test fun newlyAddedGradedEvent() {
        val old = snapshot(listOf(control("КМ 1", "4")))
        val fresh = snapshot(listOf(control("КМ 1", "4"), control("КМ 2", "5", "4")))
        val change = detectBarsGradeChanges(old, fresh).getValue("Физика").single()
        assertEquals("КМ 2", change.activityName)
        assertTrue(change.isNew)
    }

    @Test fun reorderedEventsKeepIdentity() {
        val first = snapshot(listOf(control("КМ 1", "4"), control("КМ 2", "5", "4")))
        val reversed = snapshot(listOf(control("КМ 2", "5", "4"), control("КМ 1", "4")))
        assertTrue(detectBarsGradeChanges(first, reversed).isEmpty())
    }
}
