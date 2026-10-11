package com.rteats.mpeineo.ui

import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import com.rteats.mpeineo.model.ScheduleWeek
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaRequestPolicyTest {
    private val target = ScheduleTarget(
        id = 13L,
        name = "А-12",
        type = ScheduleTargetType.GROUP,
    )

    private fun week(offset: Int) = ScheduleWeek(
        target = target,
        weekStart = "2026-10-05",
        days = emptyList(),
        fetchedAtEpochMillis = offset.toLong(),
    )

    @Test fun cannotFetchWithoutSelectedSchedule() {
        assertFalse(MainUiState().shouldLoadAgendaWeek(0))
    }

    @Test fun missingWeekIsRequestedWithoutBlockingAdjacentWeeks() {
        val state = MainUiState(
            selected = target,
            agendaWeeks = mapOf(0 to week(0)),
            agendaLoadingOffsets = setOf(-1),
            agendaFailedOffsets = setOf(2),
        )
        assertFalse(state.shouldLoadAgendaWeek(0))
        assertFalse(state.shouldLoadAgendaWeek(-1))
        assertFalse(state.shouldLoadAgendaWeek(2))
        assertTrue(state.shouldLoadAgendaWeek(1))
        assertTrue(state.shouldLoadAgendaWeek(-2))
    }

    @Test fun retryClearingFailureAllowsRequest() {
        val failed = MainUiState(
            selected = target,
            agendaFailedOffsets = setOf(3),
        )
        assertFalse(failed.shouldLoadAgendaWeek(3))
        assertTrue(
            failed.copy(agendaFailedOffsets = failed.agendaFailedOffsets - 3)
                .shouldLoadAgendaWeek(3),
        )
    }

    @Test fun newSelectionWithClearedWeeksIsFetchable() {
        val former = MainUiState(
            selected = target,
            agendaWeeks = mapOf(0 to week(0)),
        )
        val newTarget = ScheduleTarget(
            id = 14L,
            name = "А-13",
            type = ScheduleTargetType.GROUP,
        )
        assertTrue(
            former.copy(selected = newTarget, agendaWeeks = emptyMap())
                .shouldLoadAgendaWeek(0),
        )
    }
}
