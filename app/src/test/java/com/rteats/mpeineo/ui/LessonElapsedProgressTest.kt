package com.rteats.mpeineo.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonElapsedProgressTest {
    private val day = LocalDate.of(2026, 10, 20)
    private fun at(hour: Int, minute: Int, second: Int = 0): LocalDateTime =
        day.atTime(hour, minute, second)

    @Test fun beginningMidpointAndNearingEndInterpolateLinearly() {
        assertEquals(0f, lessonElapsedFraction(day, "9:20", "10:55", at(9, 20))!!, 0.0001f)
        assertEquals(0.5f, lessonElapsedFraction(day, "9:20", "10:55", at(10, 7, 30))!!, 0.0001f)
        assertEquals(0.8f, lessonElapsedFraction(day, "9:20", "10:55", at(10, 36))!!, 0.0001f)
        assertTrue(lessonElapsedFraction(day, "9:20", "10:55", at(10, 54, 59))!! > 0.99f)
    }

    @Test fun doesNotShowProgressBeforeOrAfterClass() {
        assertNull(lessonElapsedFraction(day, "09:20", "10:55", at(9, 19, 59)))
        assertNull(lessonElapsedFraction(day, "09:20", "10:55", at(10, 55)))
        assertNull(lessonElapsedFraction(day, "09:20", "10:55", at(11, 0)))
    }

    @Test fun dateIsRequiredEvenWhenClockTimeMatches() {
        val anotherDay = day.minusDays(1)
        assertNull(lessonElapsedFraction(anotherDay, "09:20", "10:55", at(10, 0)))
        assertNull(lessonElapsedFraction(day.plusDays(1), "09:20", "10:55", at(10, 0)))
    }

    @Test fun rejectsInvalidAndZeroLengthIntervals() {
        assertNull(lessonElapsedFraction(day, "bad", "10:55", at(10, 0)))
        assertNull(lessonElapsedFraction(day, "09:20", "bad", at(10, 0)))
        assertNull(lessonElapsedFraction(day, "", "", at(10, 0)))
        assertNull(lessonElapsedFraction(day, "10:55", "09:20", at(10, 0)))
        assertNull(lessonElapsedFraction(day, "09:20", "09:20", at(9, 20)))
    }

    @Test fun keepsFractionalMinutesNotJustRoundedMinuteCount() {
        val fraction = lessonElapsedFraction(day, "9:20", "9:21", at(9, 20, 15))
        assertEquals(0.25f, fraction!!, 0.0001f)
    }

    @Test fun usesUniversityTimeZoneEvenWhenDeviceIsElsewhere() {
        assertEquals(ZoneId.of("Europe/Moscow"), mpeiTimeZone)
        val moscowInstant = day.atTime(10, 0)
            .atZone(mpeiTimeZone).toInstant()
        val phoneClockTime = moscowInstant.atZone(ZoneId.of("Europe/Berlin"))
        assertTrue(phoneClockTime.toLocalTime() != at(10, 0).toLocalTime())
        assertEquals(40f / 95f,
            lessonElapsedFraction(
                day, "09:20", "10:55", moscowInstant.atZone(mpeiTimeZone).toLocalDateTime(),
            )!!, 0.0001f)
    }
}
