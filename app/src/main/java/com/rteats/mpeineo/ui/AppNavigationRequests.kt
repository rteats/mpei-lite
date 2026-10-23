package com.rteats.mpeineo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleWeek

/**
 * A typed cross-feature navigation intent, not a saved route.
 * Destinations are still owned by ServiceNavigationState and animated by
 * the app shell; the request carries only its optional feature target.
 */
internal sealed interface AppNavigationRequest {
    data class Service(val destination: AppTab) : AppNavigationRequest
    data class SelectSchedule(val target: ScheduleTarget) : AppNavigationRequest
    data class FocusScheduleLesson(val hit: ScheduleLessonHit) : AppNavigationRequest
    data class OpenMail(val hit: SearchMailHit) : AppNavigationRequest
    data class OpenMailNotification(val uid: Long, val validity: Long) : AppNavigationRequest
    data class OpenBarsDiscipline(val name: String) : AppNavigationRequest
    data class FocusCampus(val building: String, val classroom: String = "",
        val fromSchedule: Boolean = false) : AppNavigationRequest
    data class LocateClassroom(val room: String) : AppNavigationRequest
}

/**
 * Target lifecycle is separate from animation and app shell.
 * Incrementing request IDs allows repeated taps on the same destination to
 * highlight again. The consumer explicitly acknowledges one-shot requests.
 */
internal class AppNavigationTargets {
    var scheduleLessonFocus by mutableStateOf<ScheduleLessonFocus?>(null)
        private set
    var barsRequestedDiscipline by mutableStateOf<String?>(null)
        private set
    var barsSearchRequest by mutableIntStateOf(0)
        private set
    var mapFocus by mutableStateOf<CampusMapFocus?>(null)
        private set

    private var nextLessonId = 0
    private var nextMapId = 0

    fun clearScheduleFocus() {
        scheduleLessonFocus = null
    }

    /** A completed cue may only acknowledge its own request, never a newer link. */
    fun consumeScheduleFocus(requestId: Int) {
        if (scheduleLessonFocus?.requestId == requestId) {
            scheduleLessonFocus = null
        }
    }

    fun focusScheduleLesson(hit: ScheduleLessonHit) {
        scheduleLessonFocus = ScheduleLessonFocus(
            requestId = ++nextLessonId,
            weekOffset = hit.weekOffset,
            date = hit.date,
            name = hit.lesson.name,
            startTime = hit.lesson.startTime,
            place = hit.lesson.place,
        )
    }

    fun focusUpcomingLesson(highlight: HomeLessonHighlight, weeks: Map<Int, ScheduleWeek>) {
        scheduleLessonFocus = homeLessonFocus(highlight, weeks, ++nextLessonId)
    }

    fun focusBarsDiscipline(name: String) {
        barsSearchRequest++
        barsRequestedDiscipline = name
    }

    fun consumeBarsDiscipline() {
        barsRequestedDiscipline = null
    }

    fun focusCampus(building: String, classroom: String = "") {
        mapFocus = CampusMapFocus(++nextMapId, building, classroom.take(100))
    }

    fun consumeCampusFocus() {
        mapFocus = null
    }
}
