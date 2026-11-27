package com.rteats.mpeineo.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rteats.mpeineo.model.Lesson
import com.rteats.mpeineo.model.ScheduleDay
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import com.rteats.mpeineo.model.ScheduleWeek
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimetableUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun dragAndReverseAcrossSundayKeepCalendarOrder() {
        showTimetable(sunday)
        rule.onNodeWithTag("schedule-jump-date").assertTextContains("16 нояб.", substring = true)
        val grid = rule.onNodeWithTag("schedule-timetable-grid")
        grid.performTouchInput {
            swipe(Offset(width * .81f, height * .35f), Offset(width * .19f, height * .35f), 1_000)
        }
        rule.onNodeWithTag("schedule-jump-date").assertTextContains("23 нояб.", substring = true)
        grid.performTouchInput {
            swipe(Offset(width * .19f, height * .35f), Offset(width * .81f, height * .35f), 1_000)
        }
        rule.onNodeWithTag("schedule-jump-date").assertTextContains("16 нояб.", substring = true)
    }

    @Test fun lessonDetailsAllowAliasThenTeacherWithoutLosingOriginalDay() {
        val requestedTeacher = AtomicReference<Pair<ScheduleTarget, LocalDate>>()
        showTimetable(monday, onOpenTeacher = { target, date -> requestedTeacher.set(target to date) })
        rule.onNodeWithText("ТОЭ").performClick()
        rule.onNodeWithText(lesson.name).assertIsDisplayed()
        rule.onNodeWithText("Аудитория · А-404").assertIsDisplayed()
        rule.onNodeWithTag("schedule-edit-alias").performClick()
        rule.onNodeWithTag("schedule-alias-input").performTextReplacement("Электрика")
        rule.onNodeWithText("Сохранить").performClick()
        rule.onNodeWithTag("schedule-open-teacher").performClick()
        rule.waitUntil(8_000) { requestedTeacher.get() != null }
        assertEquals(teacher to monday, requestedTeacher.get())
        rule.onNodeWithTag("schedule-lesson-details").assertDoesNotExist()
        rule.onNodeWithText("Электрика").assertIsDisplayed()
        rule.onNodeWithTag("schedule-jump-date").assertTextContains("23 нояб.", substring = true)
    }

    @Test fun lessonSheetAndUnfinishedAliasSurviveStateRestoration() {
        val restoration = StateRestorationTester(rule)
        showTimetable(monday, restoration = restoration)
        rule.onNodeWithText("ТОЭ").performClick()
        rule.onNodeWithTag("schedule-edit-alias").performClick()
        rule.onNodeWithTag("schedule-alias-input").performTextReplacement("Новый текст")
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText(lesson.name).assertIsDisplayed()
        rule.onNodeWithTag("schedule-alias-input").assertTextContains("Новый текст")
    }

    @Test fun dateHeaderStaysVisibleWhenScrollingToLatePeriodsAtLargeFontSize() {
        showTimetable(monday, fontScale = 1.6f)
        val header = rule.onNodeWithTag("schedule-day-header-$monday")
        val start = header.fetchSemanticsNode().positionInRoot
        rule.onNodeWithTag("schedule-timetable-grid").performTouchInput {
            swipe(Offset(width * .5f, height * .84f), Offset(width * .5f, height * .2f), 180)
        }
        header.assertIsDisplayed()
        assertEquals(start, header.fetchSemanticsNode().positionInRoot)
    }

    @Test fun overlappingLessonsKeepRegularRowHeightsAndEachLessonIsReachable() {
        showTimetable(monday, lessons = listOf(
            lesson,
            lesson.copy(name = "Оптика", kind = "Лабораторная работа", place = "Б-212"),
            lesson.copy(name = "Механика", kind = "Практическое занятие"),
            lesson.copy(name = "Химия", startTime = "11:10"),
            lesson.copy(name = "Геометрия", startTime = "13:45"),
        ))

        val firstRow = rule.onNodeWithText("ТОЭ").fetchSemanticsNode()
        val secondRow = rule.onNodeWithText("Химия").fetchSemanticsNode()
        val thirdRow = rule.onNodeWithText("Геометрия").fetchSemanticsNode()
        assertEquals(
            thirdRow.positionInRoot.y - secondRow.positionInRoot.y,
            secondRow.positionInRoot.y - firstRow.positionInRoot.y,
            1f,
        )

        rule.onNodeWithText("+2").performClick()
        rule.onNodeWithText("Механика").assertIsDisplayed()
        rule.onNodeWithText("Оптика").performClick()
        rule.onNodeWithText("Тип · Лабораторная работа").assertIsDisplayed()
        rule.onNodeWithText("Аудитория · Б-212").assertIsDisplayed()
    }

    @Test fun phoneGridFitsFourFullDaysAndUsesTheHeightBelowTheHeader() {
        showTimetable(monday, width = 392.dp, height = 680.dp)
        val grid = rule.onNodeWithTag("schedule-timetable-grid").fetchSemanticsNode().boundsInRoot
        val fourthDate = rule.onNodeWithTag("schedule-day-header-${monday.plusDays(3)}")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(grid.right, fourthDate.right, with(rule.density) { 5.dp.toPx() })
        rule.onNodeWithTag("schedule-day-header-${monday.plusDays(4)}").assertIsNotDisplayed()
        rule.onNodeWithText("20:30").assertIsDisplayed()
        val timetable = rule.onNodeWithTag("schedule-timetable").fetchSemanticsNode().boundsInRoot
        val bottomPadding = with(rule.density) { AppSpacing.sm.roundToPx() }
        val leftoverPixels = timetable.bottom - grid.bottom - bottomPadding
        assertTrue("Only pixel rounding should remain above the bottom padding",
            leftoverPixels in 0f..<timetablePeriodStarts.size.toFloat())
    }

    @Test fun weekIndicatorFollowsDragThenSettlesOrReturns() {
        showTimetable(sunday)
        val weekBar = rule.onNodeWithTag("schedule-week-bar")
        val date = rule.onNodeWithTag("schedule-jump-date")
        val initialLeft = date.fetchSemanticsNode().positionInRoot.x
        weekBar.performTouchInput {
            down(center)
            moveTo(center - Offset(width * .26f, 0f))
        }
        assertTrue(date.fetchSemanticsNode().positionInRoot.x < initialLeft)
        weekBar.performTouchInput { cancel() }
        date.assertTextContains("16 нояб.", substring = true)

        weekBar.performTouchInput {
            swipe(Offset(width * .84f, height * .5f), Offset(width * .13f, height * .5f), 600)
        }
        date.assertTextContains("23 нояб.", substring = true)
        rule.onNodeWithTag("schedule-day-header-$monday").assertIsDisplayed()

        weekBar.performTouchInput {
            swipe(Offset(width * .13f, height * .5f), Offset(width * .84f, height * .5f), 600)
        }
        date.assertTextContains("16 нояб.", substring = true)
        rule.onNodeWithTag("schedule-day-header-${sunday.minusDays(6)}").assertIsDisplayed()
        date.performClick()
        rule.onNodeWithText("Перейти").assertIsDisplayed()
    }

    @Test fun pullingDownRefreshesTheWeekShownInTheGrid() {
        val requestedWeek = AtomicReference<Int>()
        showTimetable(monday, onRefresh = requestedWeek::set)
        rule.onNodeWithTag("schedule-week-refresh").assertDoesNotExist()
        rule.onNodeWithTag("schedule-timetable-grid").performTouchInput { swipeDown() }
        rule.runOnIdle { assertEquals(1, requestedWeek.get()) }
    }

    private fun showTimetable(
        start: LocalDate,
        onOpenTeacher: (ScheduleTarget, LocalDate) -> Unit = { _, _ -> },
        restoration: StateRestorationTester? = null,
        fontScale: Float = 1f,
        lessons: List<Lesson> = listOf(lesson),
        width: Dp = 320.dp,
        height: Dp = 570.dp,
        onRefresh: (Int) -> Unit = {},
    ) {
        val target = ScheduleTarget(1, "ЭЛ-12-24", type = ScheduleTargetType.GROUP)
        val data = ScheduleWeek(target, monday.toString(),
            listOf(ScheduleDay(monday.toString(), lessons)), 1)
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            var aliases by remember { mutableStateOf(emptyMap<String, String>()) }
            MpeiNeoTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.width(width).height(height)) {
                    ScheduleScreen(
                        state = MainUiState(
                            selected = target,
                            agendaWeeks = mapOf(1 to data),
                            lessonAliases = aliases,
                            agendaReferenceMonday = sunday.minusDays(6),
                        ),
                        onEnsureAgendaWeek = {},
                        onRefreshAgendaWeek = onRefresh,
                        onRetryAgendaWeek = {},
                        onAliasChanged = { key, alias -> aliases = mapOf(timetableAliasKey(key) to alias) },
                        onFindTeachers = { listOf(teacher) },
                        onOpenTeacher = onOpenTeacher,
                        todayJumpRequest = 0,
                        startDate = start,
                    )
                    }
                }
            }
        }
        if (restoration == null) rule.setContent(content) else restoration.setContent(content)
    }

    private val sunday = LocalDate.of(2026, 11, 22)
    private val monday = sunday.plusDays(1)
    private val teacher = ScheduleTarget(2, "Иванов Иван Иванович", type = ScheduleTargetType.LECTURER)
    private val lesson = Lesson(
        "Теоретические основы электротехники", "лк", "9:20", "10:55", "А-404", "Иванов И.И.", "ЭЛ-12-24",
    )
}
