package com.rteats.mpeineo.data

import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSearchRankingTest {
    private val group = ScheduleTarget(7, "А-17-24", "Институт ИВТИ",
        ScheduleTargetType.GROUP)
    private val teacher = ScheduleTarget(8, "Крупин Григорий", "Преподаватель",
        ScheduleTargetType.LECTURER)
    private val room = ScheduleTarget(9, "В-206", "Корпус В",
        ScheduleTargetType.ROOM)
    private val legacy = ScheduleTarget(10, "Крупин Григорий", "Личное расписание",
        ScheduleTargetType.PERSON)

    @Test fun unifiedSearchUsesOfficialPrimaryCategoriesOnly() {
        assertEquals(
            listOf(ScheduleTargetType.GROUP, ScheduleTargetType.LECTURER,
                ScheduleTargetType.ROOM),
            primaryScheduleSearchTypes,
        )
        assertFalse(primaryScheduleSearchTypes.contains(ScheduleTargetType.PERSON))
        assertEquals("lecturer", ScheduleTargetType.LECTURER.apiName)
        assertEquals("auditorium", ScheduleTargetType.ROOM.apiName)
        assertEquals("person", ScheduleTargetType.PERSON.apiName)
    }

    @Test fun exactAndCompactGroupCodesRankFirst() {
        val results = rankScheduleTargets(
            listOf(room, teacher, group), "A1724", includeUnmatched = false,
        )
        assertEquals(listOf(group), results)
        assertEquals(group, rankScheduleTargets(
            listOf(group), "а-17-24", includeUnmatched = false,
        ).single())
    }

    @Test fun savesDoNotOverruleMuchBetterMatch() {
        val other = ScheduleTarget(11, "А-17-23", "", ScheduleTargetType.GROUP)
        val ranked = rankScheduleTargets(
            listOf(group, other), "А-17-24", favorites = listOf(other),
        )
        assertEquals(group, ranked.first())
    }

    @Test fun prefixAndTyposWorkForLecturer() {
        assertEquals(teacher, rankScheduleTargets(
            listOf(teacher, group), "Круп", includeUnmatched = false,
        ).first())
        assertEquals(teacher, rankScheduleTargets(
            listOf(teacher), "Крупинн", includeUnmatched = false,
        ).single())
    }

    @Test fun personalSearchIsExplicitAndCanReturnSameName() {
        assertEquals(listOf(legacy), rankScheduleTargets(
            listOf(teacher, legacy), "крупин", type = ScheduleTargetType.PERSON,
        ))
    }

    @Test fun remoteOneCharacterOnlyWhenCategorySelected() {
        assertFalse(shouldQueryScheduleServer("а", null))
        assertTrue(shouldQueryScheduleServer("а", ScheduleTargetType.GROUP))
        assertTrue(shouldQueryScheduleServer("А-17", null))
        assertFalse(shouldQueryScheduleServer("", ScheduleTargetType.GROUP))
    }

    @Test fun dedupeUsesTypeAndId() {
        assertEquals(2, rankScheduleTargets(
            listOf(teacher, teacher, legacy), "крупин",
        ).size)
    }
}
