package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsAutoPlanTest {
    private fun activity(name: String, weight: String?, mark: String? = null, week: String? = null) =
        BarsActivity(
            type = BarsActivityType.CONTROL_ACTIVITY,
            name = name, weight = weight, markAndDate = mark, weekNum = week,
        )

    private fun available(vararg activities: BarsActivity) =
        buildAutoPlan(BarsDiscipline(activities = activities.toList())) as AutoPlanResult.Available

    @Test fun highestWeightGetsGradeStepBeforeLowWeight() {
        val plan = available(
            activity("Небольшой КМ", "20%", week = "4"),
            activity("Средний КМ", "30%", week = "5"),
            activity("Ключевой КМ", "50%", week = "6"),
        )
        // A grade step is mathematically most useful where the weight is largest.
        assertEquals(listOf(3, 4, 5), plan.recommended)
        assertEquals("Ключевой КМ", autoPriorities(plan).first().name)
        val score = assessAutoScenario(plan, plan.recommended)
        assertEquals(4.3, score.sum, 0.000001)
        assertTrue(score.reachesTarget)
        assertEquals(3, score.stepsAboveThree)
        assertFalse(assessAutoScenario(plan, listOf(3, 3, 5)).reachesTarget)
    }

    @Test fun priorLowGradeIsFixedAndNeverHypotheticallyOverwritten() {
        val plan = available(
            activity("Плохо", "25%", mark = "2 / 03.10"),
            activity("Следующий", "75%"),
        )
        assertEquals(0.5, plan.earned, 0.00001)
        assertEquals(listOf(5), plan.recommended)
        assertEquals(4.25, assessAutoScenario(plan, listOf(5)).sum, 0.00001)
        assertTrue(assessAutoScenario(plan, listOf(5)).reachesTarget)
        assertFalse(assessAutoScenario(plan, listOf(4)).reachesTarget)
    }

    @Test fun impossibleForecastIsExplicitRatherThanInventingWeights() {
        val plan = available(
            activity("Получено", "90%", mark = "2"),
            activity("Осталось", "10%"),
        )
        assertFalse(plan.canReachTarget)
        assertEquals(2.3, plan.upperBound, 0.00001)
        assertEquals(listOf(5), plan.recommended)
        assertFalse(assessAutoScenario(plan, plan.recommended).reachesTarget)
    }

    @Test fun alreadySafeScenarioPrescribesNothingHigherThanThree() {
        val plan = available(
            activity("Высокая", "90%", "5"),
            activity("Осталась", "10%"),
        )
        assertTrue(plan.baselineMeetsTarget)
        assertEquals(listOf(3), plan.recommended)
        assertEquals(4.8, assessAutoScenario(plan, plan.recommended).sum, 0.00001)
    }

    @Test fun existingZeroWeightsAreNotMisrepresentedAsInfluential() {
        val plan = available(
            activity("Обычный КМ", "100%"),
            activity("Без влияния", "0"),
        )
        assertEquals(listOf("Обычный КМ", "Без влияния"),
            autoPriorities(plan).map { it.name })
        assertEquals(0.0, autoPriorities(plan).last().weight, 0.00001)
        assertEquals(listOf(5, 3), plan.recommended)
    }

    @Test fun missingOrOverspecifiedWeightsNeverGetEstimated() {
        assertTrue(buildAutoPlan(BarsDiscipline(
            activities = listOf(activity("Неизвестно", null)),
        )) is AutoPlanResult.Unavailable)
        assertTrue(buildAutoPlan(BarsDiscipline(
            activities = listOf(activity("КМ 1", "80%"), activity("КМ 2", "80%")),
        )) is AutoPlanResult.Unavailable)
        assertTrue(buildAutoPlan(BarsDiscipline()) is AutoPlanResult.Unavailable)
    }

    @Test fun aPartialWeightTotalIsNotSilentlyRenormalized() {
        val plan = available(activity("Есть только этот", "70%"))
        assertEquals(0.7, plan.totalWeight, 0.00001)
        assertEquals(3.5, plan.upperBound, 0.00001)
        assertFalse(plan.canReachTarget)
    }

    @Test fun scenariosAreEditableAndRoundTripThroughSavedState() {
        val plan = available(activity("КМ 1", "50%"), activity("КМ 2", "50%"))
        val chosen = listOf(5, 4)
        assertEquals(chosen, restoreAutoScenario(encodeAutoScenario(chosen), 2))
        assertEquals(4.5, assessAutoScenario(plan, chosen).sum, 0.00001)
        assertNull(restoreAutoScenario("7,4", 2))
        assertNull(restoreAutoScenario("4,4", 3))
        assertNull(restoreAutoScenario("not_grades", 2))
    }

    @Test fun newOfficialGradeChangesScenarioIdentity() {
        val original = BarsDiscipline(
            disciplineName = "Электротехника",
            activities = listOf(
                activity("КМ 1", "40%", null),
                activity("КМ 2", "60%", null),
            ),
        )
        val updated = original.copy(activities = listOf(
            activity("КМ 1", "40%", "5 / 19.10"),
            activity("КМ 2", "60%", null),
        ))
        assertNotEquals(
            autoScenarioKey(original, "a", "semester"),
            autoScenarioKey(updated, "a", "semester"),
        )
        assertNotEquals(
            autoScenarioKey(original, "a", "semester"),
            autoScenarioKey(original, "b", "semester"),
        )
    }

    @Test fun onlyUpcomingTestIsRankedAndAlreadyGradedKeptAtActualValue() {
        val plan = available(
            activity("Готово", "50%", "5"),
            activity("Дальше", "50%"),
        )
        assertEquals(listOf("Дальше"), autoPriorities(plan).map { it.name })
        assertEquals(2.5, plan.earned, 0.00001)
        assertEquals(4.0, plan.lowerBound, 0.00001)
        assertEquals(listOf(4), plan.recommended)
        assertTrue(assessAutoScenario(plan, plan.recommended).reachesTarget)
    }
}
