package com.rteats.mpeineo.ui

import com.rteats.mpeineo.data.MailSummary
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchIndexTest {
    @Test fun combinesCachedMailHeadersWithoutServerRequests() {
        val messages = listOf(
            MailSummary(1L, 1L, "Registrar", "Exam schedule", 0L, true, false),
            MailSummary(2L, 1L, "Professor", "Laboratory work", 0L, false, false),
        )
        assertEquals(1, GlobalSearchIndex.mail("exam", messages).size)
        assertEquals(2L, GlobalSearchIndex.mail("PROFESSOR", messages).single().uid)
    }

    @Test fun searchesDisciplinesByTitleOrLecturer() {
        val grades = listOf(
            BarsDiscipline(disciplineName = "Физика", personName = "Иванов"),
            BarsDiscipline(disciplineName = "Математика", personName = "Сидоров"),
        )
        assertEquals("Физика", GlobalSearchIndex.disciplines("иванов", grades).single().disciplineName)
        assertEquals("Математика", GlobalSearchIndex.disciplines("матем", grades).single().disciplineName)
    }

    @Test fun mergesFavoriteAndRemoteScheduleWithoutDuplicates() {
        val favorite = ScheduleTarget(2L, "А-12", type = ScheduleTargetType.GROUP)
        val state = MainUiState(
            favorites = listOf(favorite),
            searchResults = listOf(favorite, ScheduleTarget(3L, "А-13", type = ScheduleTargetType.GROUP)),
        )
        assertEquals(2, GlobalSearchIndex.targets("А-1", state).size)
        assertTrue(GlobalSearchIndex.targets("не найдено", state).isEmpty())
    }
}
