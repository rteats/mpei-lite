package com.rteats.mpeineo.ui

import com.rteats.mpeineo.data.MailSummary
import com.rteats.mpeineo.data.OwaMailFolder
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import com.rteats.mpeineo.model.Lesson
import com.rteats.mpeineo.model.ScheduleDay
import com.rteats.mpeineo.model.ScheduleWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchIndexTest {
    @Test fun cachedLessonSearchKeepsItsExactWeekAndDate() {
        val target = ScheduleTarget(2L, "А-12", type = ScheduleTargetType.GROUP)
        val lesson = Lesson("Физика", "", "11:10", "12:45", "В-206", "Иванов", "")
        val week = ScheduleWeek(target, "2026-10-19",
            listOf(ScheduleDay("2026-10-21", listOf(lesson))), 1)
        val results = GlobalSearchIndex.lessons("В-206", MainUiState(
            selected = target, agendaWeeks = mapOf(1 to week)))
        assertEquals(1, results.size)
        assertEquals(1, results.single().weekOffset)
        assertEquals("2026-10-21", results.single().date)
        assertEquals(lesson, results.single().lesson)
    }

    @Test fun combinesCachedMailHeadersWithoutServerRequests() {
        val messages = listOf(
            MailSummary(1L, 1L, "Registrar", "Exam schedule", 0L, true, false),
            MailSummary(2L, 1L, "Professor", "Laboratory work", 0L, false, false),
        )
        assertEquals(1, GlobalSearchIndex.mail("exam", messages).size)
        assertEquals(2L, GlobalSearchIndex.mail("PROFESSOR", messages).single().uid)
    }

    @Test fun scopesKeepServiceResultsSeparateWithoutLosingCrossSearch() {
        assertTrue(SearchScope.ALL.includesSchedule)
        assertTrue(SearchScope.ALL.includesBars)
        assertTrue(SearchScope.ALL.includesMail)
        assertTrue(SearchScope.SCHEDULE.includesSchedule)
        assertTrue(!SearchScope.SCHEDULE.includesBars)
        assertTrue(!SearchScope.BARS.includesMail)
        assertTrue(!SearchScope.MAIL.includesSchedule)
        // The map searches buildings; timetable favorites belong to Schedule.
        assertTrue(SearchScope.MAPS.includesMaps)
        assertTrue(!SearchScope.MAPS.includesSchedule)
        assertTrue(!SearchScope.MAPS.includesBars)
        assertTrue(!SearchScope.MAPS.includesMail)
    }

    @Test fun scopedMailSearchIncludesInboxSentAndRecipientMatches() {
        val inbox = MailSummary(1L, 1L, "Alice", "Notice", 0L, false, false)
        val sent = MailSummary(2L, 2L, "My account", "Lab result", 0L, false, false,
            recipients = listOf("bob@mpei.ru"))
        val state = MailUiState(items = listOf(inbox), sentItems = listOf(sent))
        val result = GlobalSearchIndex.mailHits("bob", state)
        assertEquals(1, result.size)
        assertEquals(OwaMailFolder.SENT, result.single().folder)
        assertEquals(sent, result.single().message)
        assertEquals(2, GlobalSearchIndex.mailHits("", state).size)
        assertEquals(1, GlobalSearchIndex.mailHits("Alice", state).size)
    }

    @Test fun searchesDisciplinesByTitleOrLecturer() {
        val grades = listOf(
            BarsDiscipline(disciplineName = "Физика", personName = "Иванов"),
            BarsDiscipline(disciplineName = "Математика", personName = "Сидоров"),
        )
        assertEquals("Физика", GlobalSearchIndex.disciplines("иванов", grades).single().disciplineName)
        assertEquals("Математика", GlobalSearchIndex.disciplines("матем", grades).single().disciplineName)
    }

    @Test fun scheduleTypeFiltersFavoritesRemoteResultsAndSelectedTimetable() {
        val favoriteGroup = ScheduleTarget(42L, "А-12",
            type = ScheduleTargetType.GROUP)
        val favoriteTeacher = ScheduleTarget(42L, "Иванов",
            type = ScheduleTargetType.PERSON)
        val savedRoom = ScheduleTarget(8L, "В-206",
            type = ScheduleTargetType.ROOM)
        val state = MainUiState(
            favorites = listOf(favoriteGroup, favoriteTeacher),
            searchResults = listOf(favoriteTeacher, savedRoom),
            selected = savedRoom,
            favoriteNames = mapOf("person:42" to "Мой преподаватель"),
        )

        assertEquals(listOf(favoriteGroup),
            GlobalSearchIndex.targets("", state, ScheduleTargetType.GROUP))
        assertEquals(listOf(favoriteTeacher),
            GlobalSearchIndex.targets("", state, ScheduleTargetType.PERSON))
        assertEquals(listOf(savedRoom),
            GlobalSearchIndex.targets("", state, ScheduleTargetType.ROOM))
        assertEquals(listOf(favoriteTeacher),
            GlobalSearchIndex.targets("мой", state, ScheduleTargetType.PERSON))
        assertTrue(GlobalSearchIndex.targets("мой", state, ScheduleTargetType.GROUP).isEmpty())
        assertEquals(3, GlobalSearchIndex.targets("", state).size)
    }

    @Test fun scopeTypeFilterCannotLeakToAllServices() {
        val group = ScheduleTarget(1L, "А-12", type = ScheduleTargetType.GROUP)
        val teacher = ScheduleTarget(2L, "Иванов",
            type = ScheduleTargetType.PERSON)
        val state = MainUiState(
            favorites = listOf(group, teacher),
            searchType = ScheduleTargetType.GROUP,
        )
        // Schedule filter narrows the current Schedule category, while
        // the cross-service "Все" scope should show both types.
        assertEquals(listOf(group),
            GlobalSearchIndex.targets("", state, state.searchType))
        assertEquals(listOf(group, teacher),
            GlobalSearchIndex.targets("", state))
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
