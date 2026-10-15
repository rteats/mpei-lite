package com.rteats.mpeineo.ui

import com.rteats.mpeineo.model.Lesson
import com.rteats.mpeineo.model.ScheduleDay
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import com.rteats.mpeineo.model.ScheduleWeek
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDashboardTest {
    private fun lesson(name: String, from: String, until: String) = Lesson(
        name = name, kind = "", startTime = from, endTime = until,
        place = "Б-204", lecturer = "", groups = "",
    )

    private fun week(vararg days: ScheduleDay) = ScheduleWeek(
        target = ScheduleTarget(1, "А-12", type = ScheduleTargetType.GROUP),
        weekStart = "2026-10-12",
        days = days.toList(),
        fetchedAtEpochMillis = 1,
    )

    @Test
    fun ongoingClassTakesPriorityOverLaterClass() {
        val today = ScheduleDay(
            "2026-10-15",
            listOf(lesson("First", "09:20", "10:55"), lesson("Second", "11:10", "12:45")),
        )
        val result = nextHomeLesson(
            listOf(week(today)), LocalDateTime.of(2026, 10, 15, 10, 30),
        )
        assertEquals("First", result?.lesson?.name)
        assertTrue(result?.isOngoing == true)
        assertEquals(0L, result?.minutesUntil)
    }

    @Test
    fun finishedClassIsSkippedAndTomorrowIsShown() {
        val today = ScheduleDay("2026-10-15", listOf(lesson("Finished", "09:20", "10:55")))
        val tomorrow = ScheduleDay("2026-10-16", listOf(lesson("Tomorrow", "11:10", "12:45")))
        val result = nextHomeLesson(
            listOf(week(tomorrow, today)), LocalDateTime.of(2026, 10, 15, 15, 0),
        )
        assertEquals("Tomorrow", result?.lesson?.name)
        assertFalse(result?.isOngoing ?: true)
        assertEquals("2026-10-16", result?.date.toString())
    }

    @Test
    fun expiredOrUnavailableWeeksDoNotInventUpcomingLessons() {
        val today = ScheduleDay("2026-10-15", listOf(lesson("Finished", "09:20", "10:55")))
        val now = LocalDateTime.of(2026, 10, 15, 15, 0)
        assertNull(nextHomeLesson(listOf(week(today)), now))
        assertNull(nextHomeLesson(emptyList(), now))
    }

    @Test
    fun malformedCachedRecordsAreIgnored() {
        val malformed = ScheduleDay("bad-date", listOf(lesson("Invalid date", "09:20", "10:55")))
        val times = ScheduleDay(
            "2026-10-15",
            listOf(lesson("Invalid time", "bad", "10:55"), lesson("Valid", "11:10", "12:45")),
        )
        val result = nextHomeLesson(
            listOf(week(malformed, times)), LocalDateTime.of(2026, 10, 15, 10, 0),
        )
        assertEquals("Valid", result?.lesson?.name)
    }
}
