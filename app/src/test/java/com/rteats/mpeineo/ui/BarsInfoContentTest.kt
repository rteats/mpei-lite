package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsInfoContentTest {
    private fun plan(weight: String): AutoPlanResult = buildAutoPlan(
        BarsDiscipline(
            activities = listOf(
                BarsActivity(
                    type = BarsActivityType.CONTROL_ACTIVITY,
                    name = "Контрольная",
                    weight = weight,
                ),
            ),
        ),
    )

    @Test fun autoHelpPreservesCalculationBoundsAndWeightCompleteness() {
        val available = plan("70%") as AutoPlanResult.Available
        val explanations = barsAutoExplanations(available)
        val detail = explanations.joinToString("\n") { it.description }

        assertTrue(detail.contains(formatBarsWeightedSum(available.earned)))
        assertTrue(detail.contains(formatBarsWeightedSum(available.lowerBound)))
        assertTrue(detail.contains(formatBarsWeightedSum(available.upperBound)))
        assertTrue(detail.contains("70%"))
        assertTrue(detail.contains("не выдумываются"))
        assertTrue(detail.contains("не официальная оценка"))
        assertTrue(detail.contains("не оценки в БАРС"))
    }

    @Test fun fullWeightPlanDoesNotWarnAboutMissingWeights() {
        val available = plan("100%") as AutoPlanResult.Available
        val details = barsAutoExplanations(available).joinToString(" ") { it.description }
        assertTrue(details.contains("Все предусмотренные расчётом веса учтены"))
        assertTrue(!details.contains("Недостающие веса не выдумываются"))
    }

    @Test fun unavailablePlanRetainsActionableReasonInHelp() {
        val unavailable = AutoPlanResult.Unavailable("Некорректный вес контрольной.")
        val info = barsAutoExplanations(unavailable)
        assertEquals("Почему нет плана", info.first().heading)
        assertTrue(info.first().description.contains("Некорректный вес"))
        assertTrue(info.any { it.description.contains("правил дисциплины") })
    }
}
