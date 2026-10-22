package com.rteats.mpeineo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rteats.mpeineo.ui.BarsActivity
import com.rteats.mpeineo.ui.BarsActivityType
import com.rteats.mpeineo.ui.BarsDiscipline
import com.rteats.mpeineo.ui.BarsNativeDashboard
import com.rteats.mpeineo.ui.BarsUiState
import com.rteats.mpeineo.ui.theme.MpeiNeoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BarsTaskFlowUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun overviewStartsWithDisciplinesAndOptionalReports() {
        rule.setContent { MpeiNeoTheme {
            BarsNativeDashboard(
                state = BarsUiState(
                    isLoading = false,
                    profileName = "Студент",
                    disciplines = listOf(sampleDiscipline()),
                ),
                active = true,
                onDetailOpenChange = {},
                openDisciplineName = null,
                openDisciplineRequest = 0,
                onOpenDisciplineConsumed = {},
                showCachedRefreshErrors = false,
                onRefresh = {},
                onOpenBrowser = {},
                onAcknowledgeGrades = {},
                onRefreshSummary = {},
            )
        } }
        rule.onNodeWithText("Дисциплины").assertIsDisplayed()
        rule.onNodeWithText("По неделям").assertDoesNotExist()
        rule.onNodeWithTag("bars-reports-toggle").performClick()
        rule.onNodeWithText("По неделям").assertExists()
        rule.onNodeWithTag("bars-discipline-Физика").performClick()
        rule.onNodeWithTag("bars-auto-goal-toggle").assertExists()
        rule.onNodeWithTag("bars-auto-planner").assertDoesNotExist()
        rule.onNodeWithTag("bars-auto-goal-toggle").performClick()
        rule.onNodeWithTag("bars-auto-planner").assertExists()
    }

    private fun sampleDiscipline() = BarsDiscipline(
        disciplineName = "Физика",
        assessmentType = "Экзамен",
        activities = listOf(
            BarsActivity(
                type = BarsActivityType.CONTROL_ACTIVITY,
                name = "Контрольная",
                weight = "50%",
                markAndDate = "4",
            ),
            BarsActivity(
                type = BarsActivityType.CONTROL_ACTIVITY,
                name = "Лабораторная",
                weight = "50%",
            ),
        ),
    )
}
