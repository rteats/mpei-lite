package com.rteats.mpeineo.ui

import com.rteats.mpeineo.model.Lesson
import com.rteats.mpeineo.model.ScheduleWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val lessonClock = DateTimeFormatter.ofPattern("H:mm")

/** Use the published local timetable (Moscow time), not the phone's time zone. */
internal data class HomeLessonHighlight(
    val lesson: Lesson,
    val date: LocalDate,
    val startsAt: LocalDateTime,
    val isOngoing: Boolean,
    val minutesUntil: Long,
)

/**
 * Read only weeks already loaded by the schedule screen. A visit to Home must
 * never cause a new timetable request, and expired lessons must not be offered.
 */
internal fun nextHomeLesson(
    weeks: Collection<ScheduleWeek>,
    now: LocalDateTime,
): HomeLessonHighlight? = weeks.asSequence()
    .flatMap { it.days.asSequence() }
    .flatMap { day ->
        val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
            ?: return@flatMap emptySequence()
        day.lessons.asSequence().mapNotNull { lesson ->
            val start = runCatching { LocalTime.parse(lesson.startTime.trim(), lessonClock) }
                .getOrNull() ?: return@mapNotNull null
            val end = runCatching { LocalTime.parse(lesson.endTime.trim(), lessonClock) }
                .getOrNull() ?: start.plusMinutes(95)
            val startsAt = date.atTime(start)
            val endsAt = date.atTime(end)
            if (!endsAt.isAfter(now)) return@mapNotNull null
            HomeLessonHighlight(
                lesson = lesson,
                date = date,
                startsAt = startsAt,
                isOngoing = !startsAt.isAfter(now),
                minutesUntil = Duration.between(now, startsAt).toMinutes().coerceAtLeast(0),
            )
        }
    }
    .minByOrNull { it.startsAt }

/**
 * The Schedule preview can come from either cached week. Resolve that exact
 * source week instead of assuming the lesson belongs to the current week.
 * Missing or changed cache must never highlight an unrelated class.
 */
internal fun homeLessonFocus(
    highlight: HomeLessonHighlight,
    weeks: Map<Int, ScheduleWeek>,
    requestId: Int,
): ScheduleLessonFocus? {
    val date = highlight.date.toString()
    val offset = weeks.entries.firstOrNull { (_, week) ->
        week.days.any { day ->
            day.date == date && day.lessons.any { lesson ->
                lesson.name == highlight.lesson.name &&
                    lesson.startTime == highlight.lesson.startTime &&
                    lesson.place == highlight.lesson.place
            }
        }
    }?.key ?: return null
    return ScheduleLessonFocus(
        requestId = requestId,
        weekOffset = offset,
        date = date,
        name = highlight.lesson.name,
        startTime = highlight.lesson.startTime,
        place = highlight.lesson.place,
    )
}

