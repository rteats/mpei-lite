package com.rteats.mpeineo.ui

import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.model.Lesson
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableDataTest {
    @Test fun datesStayAdjacentAcrossWeeksAndYears() {
        val sunday = LocalDate.of(2027, 1, 3)
        val monday = sunday.plusDays(1)
        assertEquals(monday, timetableDayAt(timetableIndexOf(sunday) + 1))
        assertEquals(sunday, timetableDayAt(timetableIndexOf(monday) - 1))
        assertEquals(LocalDate.of(2026, 12, 28), timetableWeekStart(sunday))
        assertEquals(monday, timetableWeekStart(monday))
    }

    @Test fun aliasesOverrideDeterministicLabelsAcrossCapitalizationAndSpacing() {
        val official = "Теоретические основы электротехники"
        assertEquals("ТОЭ", timetableLessonLabel(official, emptyMap()))
        assertEquals("Электрика", timetableLessonLabel(official.uppercase(),
            mapOf(timetableAliasKey("  Теоретические  основы электротехники ") to "Электрика")))
        assertEquals("Физика", timetableLessonLabel("Физика", emptyMap()))
        assertEquals("Мат. анализ", timetableLessonLabel("Математический анализ", emptyMap()))
    }

    @Test fun periodGroupingPreservesSimultaneousAndOffSlotLessons() {
        val first = lesson("Физика", "9:20")
        val parallel = lesson("Оптика", "09:20")
        val offSlot = lesson("Консультация", "10:30")
        val unspecified = lesson("Семинар", "")
        val periods = timetableLessonsByStart(listOf(first, parallel, offSlot, unspecified))
        assertEquals(listOf(first, parallel), periods[LocalTime.of(9, 20)])
        assertEquals(listOf(offSlot), periods[LocalTime.of(10, 30)])
        assertEquals(listOf(unspecified), periods[null])
    }

    @Test fun lecturerInitialsReturnEveryExactMatchButExcludeWrongInitials() {
        fun lecturer(id: Long, name: String) =
            ScheduleTarget(id = id, name = name, type = ScheduleTargetType.LECTURER)
        val first = lecturer(1, "Иванов Иван Иванович")
        val second = lecturer(2, "Иванов Игорь Ильич")
        val other = lecturer(3, "Иванов Пётр Иванович")
        assertEquals(listOf(first, second),
            timetableTeacherMatches("Иванов И.И.", listOf(first, second, other)))
    }

    @Test fun lessonKindsMapToTheRequestedMaterialSymbols() {
        val icons = mapOf(
            "Лекция" to AppIcons.Education,
            "ЛЕК." to AppIcons.Education,
            "Практическое занятие" to AppIcons.Edit,
            "сем." to AppIcons.Edit,
            "Лабораторная работа" to AppIcons.Science,
            "лб" to AppIcons.Science,
            "Экзамен" to AppIcons.Quiz,
            "Консультация" to AppIcons.Quiz,
            "" to AppIcons.Quiz,
        )
        icons.forEach { (kind, expected) -> assertEquals(kind, expected, timetableLessonType(kind).icon) }
    }

    private fun lesson(name: String, time: String) =
        Lesson(name, "лек", time, "", "А-100", "", "")
}
