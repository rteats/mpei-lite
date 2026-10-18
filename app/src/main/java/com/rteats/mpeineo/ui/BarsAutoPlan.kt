package com.rteats.mpeineo.ui

import kotlin.math.abs

/**
 * Local-only planning for a subject's weighted control activities.
 * All marks already reported by BARS are immutable; future marks are 3–5.
 * The 4.2 target is the weighted score for THIS subject, not a university-
 * wide average or a guarantee of an automatic exam grade.
 */
internal data class AutoPlanSlot(
    val sourceIndex: Int,
    val name: String,
    val week: String?,
    val weight: Double,
    val actualMark: Double?,
)

internal sealed interface AutoPlanResult {
    data class Unavailable(val explanation: String) : AutoPlanResult

    data class Available(
        val slots: List<AutoPlanSlot>,
        val totalWeight: Double,
        val earned: Double,
        val lowerBound: Double,
        val upperBound: Double,
        val recommended: List<Int>,
        val target: Double,
    ) : AutoPlanResult {
        val upcoming: List<AutoPlanSlot> get() = slots.filter { it.actualMark == null }
        val canReachTarget: Boolean get() = upperBound + EPS >= target
        val baselineMeetsTarget: Boolean get() = lowerBound + EPS >= target
    }
}

private const val EPS = 1e-8

/**
 * A grade-step costs one unit independently of the test.
 * Investing a step in a heavier test always gains at least as much weighted
 * score as investing it in a lighter test. Therefore greedy descending-weight
 * allocation minimizes the number of requested grade-step improvements.
 * This does not claim to know the real difficulty of any particular test.
 */
internal fun buildAutoPlan(
    discipline: BarsDiscipline,
    target: Double = TARGET_EXAM_SUM,
): AutoPlanResult {
    require(target.isFinite() && target > 0.0)
    val controls = discipline.activities.filter { it.type == BarsActivityType.CONTROL_ACTIVITY }
    if (controls.isEmpty()) {
        return AutoPlanResult.Unavailable("Для этой дисциплины нет контрольных мероприятий.")
    }
    val slots = mutableListOf<AutoPlanSlot>()
    controls.forEachIndexed { index, activity ->
        val weight = parseBarsWeight(activity.weight)
            ?: return AutoPlanResult.Unavailable(
                "Не указан корректный вес «${activity.name?.takeIf(String::isNotBlank) ?: "КМ ${index + 1}"}». " +
                    "Расчёт без него был бы неточным.",
            )
        val mark = activity.markValue?.toDouble()
        if (mark != null && (!mark.isFinite() || mark !in 0.0..5.0)) {
            return AutoPlanResult.Unavailable("БАРС вернул оценку вне шкалы 0–5.")
        }
        slots += AutoPlanSlot(
            sourceIndex = index,
            name = activity.name?.takeIf(String::isNotBlank) ?: "КМ ${index + 1}",
            week = activity.weekNum?.takeIf(String::isNotBlank),
            weight = weight,
            actualMark = mark,
        )
    }

    val total = slots.sumOf { it.weight }
    if (total <= 0.0) {
        return AutoPlanResult.Unavailable("У всех контрольных мероприятий нулевой вес.")
    }
    if (total > 1.0001) {
        return AutoPlanResult.Unavailable(
            "Сумма весов больше 100%. Нужно уточнить данные БАРС.",
        )
    }
    val earned = slots.filter { it.actualMark != null }
        .sumOf { it.weight * it.actualMark!! }
    val upcoming = slots.filter { it.actualMark == null }
    val baseline = earned + upcoming.sumOf { it.weight * 3.0 }
    val upper = earned + upcoming.sumOf { it.weight * 5.0 }
    val recommendation = IntArray(upcoming.size) { 3 }
    // Repeatedly fill the highest-leverage work first. An individual slot may
    // receive two grade steps: 3 -> 4 and 4 -> 5.
    if (baseline + EPS < target) {
        upcoming.indices.sortedWith(compareByDescending<Int> { upcoming[it].weight }
            .thenBy { it }).forEach { index ->
            while (recommendation[index] < 5 &&
                earned + upcoming.indices.sumOf {
                    upcoming[it].weight * recommendation[it]
                } + EPS < target
            ) {
                recommendation[index]++
            }
        }
    }
    return AutoPlanResult.Available(
        slots = slots, totalWeight = total, earned = earned,
        lowerBound = baseline, upperBound = upper,
        recommended = recommendation.toList(), target = target,
    )
}

internal data class AutoPlanScenario(
    val sum: Double,
    val gap: Double,
    val reachesTarget: Boolean,
    val stepsAboveThree: Int,
)

internal fun assessAutoScenario(
    plan: AutoPlanResult.Available,
    marks: List<Int>,
): AutoPlanScenario {
    require(marks.size == plan.upcoming.size && marks.all { it in 3..5 })
    val sum = plan.earned + plan.upcoming.indices.sumOf { i ->
        marks[i] * plan.upcoming[i].weight
    }
    return AutoPlanScenario(
        sum = sum,
        gap = (plan.target - sum).coerceAtLeast(0.0).let {
            if (it < EPS) 0.0 else it
        },
        reachesTarget = sum + EPS >= plan.target,
        stepsAboveThree = marks.sumOf { it - 3 },
    )
}

/** Portable, validation-only scenario format used by the saveable Compose state. */
internal fun encodeAutoScenario(marks: List<Int>): String =
    marks.joinToString(",").also { require(marks.all { it in 3..5 }) }

internal fun restoreAutoScenario(value: String?, expectedSize: Int): List<Int>? {
    if (value == null) return null
    if (expectedSize == 0) return emptyList<Int>().takeIf { value.isEmpty() }
    val values = value.split(',').map { it.toIntOrNull() ?: return null }
    return values.takeIf { it.size == expectedSize && it.all { grade -> grade in 3..5 } }
}

/**
 * Saved plans are tied to the account's semester and all BARS activities.
 * A newly reported grade/changed weight makes the old hypothetical marks stale
 * and automatically selects a fresh recommendation instead.
 */
internal fun autoScenarioKey(discipline: BarsDiscipline, account: String, semester: String): String =
    buildString {
        append(account)
        append('|')
        append(semester)
        append('|')
        append(discipline.disciplineName)
        discipline.activities.filter { it.type == BarsActivityType.CONTROL_ACTIVITY }.forEach {
            append('|')
            append(it.name)
            append(':')
            append(it.weight)
            append(':')
            append(it.weekNum)
            append(':')
            append(it.markAndDate)
        }
    }

/** Only future graded assessments can be prioritized. */
internal fun autoPriorities(plan: AutoPlanResult.Available): List<AutoPlanSlot> =
    plan.upcoming.sortedWith(compareByDescending<AutoPlanSlot> { it.weight }
        .thenBy { it.week?.filter(Char::isDigit)?.toIntOrNull() ?: Int.MAX_VALUE }
        .thenBy { it.sourceIndex })


/** Highest-value actionable KM for each discipline, for the BARS overview. */
internal data class SemesterAutoFocus(
    val disciplineName: String,
    val activityName: String,
    val recommendedMark: Int,
    val week: String?,
    val weight: Double,
)

internal fun semesterAutoFocus(disciplines: List<BarsDiscipline>): List<SemesterAutoFocus> =
    disciplines.mapNotNull { discipline ->
        if (discipline.finalMarkValue != null ||
            !shouldShowGradeForecastForAssessment(discipline.assessmentType)
        ) return@mapNotNull null
        val plan = buildAutoPlan(discipline) as? AutoPlanResult.Available
            ?: return@mapNotNull null
        if (!plan.canReachTarget) return@mapNotNull null
        val priorities = plan.upcoming.mapIndexedNotNull { index, slot ->
            val desired = plan.recommended[index]
            if (desired <= 3) null else SemesterAutoFocus(
                disciplineName = discipline.disciplineName,
                activityName = slot.name,
                recommendedMark = desired,
                week = slot.week,
                weight = slot.weight,
            )
        }
        priorities.maxWithOrNull(
            compareBy<SemesterAutoFocus> { it.weight * (it.recommendedMark - 3) }
                .thenBy { it.weight },
        )
    }.sortedWith(
        compareByDescending<SemesterAutoFocus> {
            it.weight * (it.recommendedMark - 3)
        }.thenBy { it.week?.filter(Char::isDigit)?.toIntOrNull() ?: Int.MAX_VALUE }
            .thenBy { it.disciplineName },
    )
