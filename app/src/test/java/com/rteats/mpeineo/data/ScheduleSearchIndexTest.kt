package com.rteats.mpeineo.data

import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSearchIndexTest {
    private val stored = listOf(
        ScheduleTarget(1, "А-12-24", "Студенческая группа", ScheduleTargetType.GROUP),
        ScheduleTarget(2, "Акимов", "Преподаватель физики", ScheduleTargetType.PERSON),
        ScheduleTarget(3, "А-305", "Аудитория", ScheduleTargetType.ROOM),
        ScheduleTarget(4, "Физика", "Профиль кафедры", ScheduleTargetType.GROUP),
    )

    @Test fun findsKnownGroupOffline() {
        assertEquals(listOf("А-12-24"),
            searchIndexedScheduleTargets(stored, "а-12", null).map { it.name })
    }

    @Test fun categoryFilterDoesNotMixSamePrefixes() {
        assertEquals(listOf("А-305"),
            searchIndexedScheduleTargets(stored, "а-", ScheduleTargetType.ROOM)
                .map { it.name })
    }

    @Test fun searchesPreviouslyDiscoveredDescriptions() {
        assertEquals(listOf("Акимов"),
            searchIndexedScheduleTargets(stored, "физик", ScheduleTargetType.PERSON)
                .map { it.name })
    }

    @Test fun aShortQueryDoesNotSearchWholeIndex() {
        assertTrue(searchIndexedScheduleTargets(stored, "а", null).isEmpty())
    }

    @Test fun unknownTargetIsNotInvented() {
        assertTrue(searchIndexedScheduleTargets(stored, "Несуществующая", null).isEmpty())
    }
}
