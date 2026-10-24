package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import kotlin.math.roundToInt

/** The full calculation and limitations stay accessible through the info affordance. */
internal fun barsAutoExplanations(plan: AutoPlanResult): List<BarsInfoSection> {
    return when (plan) {
        is AutoPlanResult.Unavailable -> listOf(
            BarsInfoSection(
                "Почему нет плана",
                plan.explanation,
            ),
            BarsInfoSection(
                "Что такое автомат",
                "Цель 4,20 используется для ориентировочного планирования оценок. " +
                    "Официальное решение зависит от правил дисциплины и преподавателя.",
            ),
        )
        is AutoPlanResult.Available -> listOf(
            BarsInfoSection(
                "Расчёт по весам БАРС",
                "Уже получено: ${formatBarsWeightedSum(plan.earned)}. " +
                    "Если все оставшиеся оценки — 3: ${formatBarsWeightedSum(plan.lowerBound)}. " +
                    "Если все — 5: ${formatBarsWeightedSum(plan.upperBound)}. " +
                    "Полученные оценки нельзя менять в сценарии.",
            ),
            BarsInfoSection(
                "Как работает рекомендация",
                if (plan.canReachTarget)
                    "План подбирает минимум повышений относительно троек, " +
                        "чтобы достичь 4,20. Работы с большим весом сильнее влияют " +
                        "на результат. ${plan.upcoming.size} КМ доступны для моделирования."
                else
                    "Даже максимальные оставшиеся оценки не позволяют набрать 4,20 " +
                        "по известным весам. Можно проверить возможный результат вручную.",
            ),
            BarsInfoSection(
                "Что означает вес",
                "Вес показывает вклад КМ в итоговую взвешенную сумму. " +
                    "Изменение оценки на один балл добавляет ровно вес этого КМ. " +
                    "Чем выше вес мероприятия, тем сильнее оценка влияет на результат.",
            ),
            BarsInfoSection(
                "Полнота данных",
                if (plan.totalWeight < 0.9999)
                    "Сумма известных весов: ${(plan.totalWeight * 100).roundToInt()}%. " +
                        "Недостающие веса не выдумываются и не включаются в прогноз."
                else
                    "Все предусмотренные расчётом веса учтены. " +
                        "Это не гарантирует соответствие правилам выставления автомата.",
            ),
            BarsInfoSection(
                "Важно",
                "Это лишь прогноз на основе данных БАРС, а не официальная оценка. " +
                    "Фактические условия автомата устанавливают дисциплина и преподаватель. " +
                    "Нажатие на 3/4/5 изменяет только ваш сценарий, а не оценки в БАРС.",
            ),
        )
    }
}

/**
 * A what-if planner, not an official BARS final grade.
 * There are no network writes: grade buttons only change hypothetical scores.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BarsAutoPlanner(
    discipline: BarsDiscipline,
    savedScenario: String?,
    onScenarioChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = remember(discipline) { buildAutoPlan(discipline) }
    val explanations = barsAutoExplanations(plan)
    Column(
        modifier = modifier.fillMaxWidth().testTag("bars-auto-planner"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                icon = AppIcons.Analytics,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Прогноз 4,2",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Цель 4,20",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BarsInfoButton(
                title = "Как работает «Автомат»",
                sections = explanations,
                tag = "bars-auto-info",
            )
        }

        when (plan) {
            is AutoPlanResult.Unavailable -> {
                Text(
                    plan.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is AutoPlanResult.Available -> {
                val chosen = restoreAutoScenario(savedScenario, plan.upcoming.size)
                    ?: plan.recommended
                val scenario = assessAutoScenario(plan, chosen)
                val colors = MaterialTheme.colorScheme

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            formatBarsWeightedSum(scenario.sum),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                !plan.canReachTarget -> colors.error
                                scenario.reachesTarget -> colors.primary
                                else -> colors.onSurface
                            },
                        )
                        Text(
                            when {
                                !plan.canReachTarget ->
                                    "До 4,20 не дойти даже с пятёрками"
                                scenario.reachesTarget && plan.upcoming.isEmpty() ->
                                    "Порог уже достигнут"
                                scenario.reachesTarget ->
                                    "Порог достигнут по вашему плану"
                                else ->
                                    "Не хватает ${formatBarsWeightedSum(scenario.gap)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (!plan.canReachTarget) colors.error
                                else colors.onSurfaceVariant,
                        )
                    }
                    AppIcon(
                        if (scenario.reachesTarget) AppIcons.Verified else AppIcons.Analytics,
                        contentDescription = null,
                        tint = if (scenario.reachesTarget) colors.primary
                            else colors.onSurfaceVariant,
                    )
                }

                if (plan.upcoming.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Будущие оценки",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                onScenarioChange(encodeAutoScenario(plan.recommended))
                            },
                            modifier = Modifier.testTag("bars-auto-optimal"),
                        ) { Text("Рекомендуемые") }
                    }
                    autoPriorities(plan).forEachIndexed { _, slot ->
                        val index = plan.upcoming.indexOf(slot)
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.45f))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .testTag("bars-auto-slot-${slot.sourceIndex}"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    slot.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildList {
                                        add("Вес ${(slot.weight * 100).roundToInt()}%")
                                        slot.week?.let { add("Неделя ${it}") }
                                    }.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            // M3 connected single-choice toggles use pressed/
                            // checked shape transitions from MotionScheme.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    ButtonGroupDefaults.ConnectedSpaceBetween,
                                ),
                            ) {
                                listOf(3, 4, 5).forEachIndexed { i, grade ->
                                    ToggleButton(
                                        checked = chosen[index] == grade,
                                        onCheckedChange = {
                                            if (chosen[index] != grade) {
                                                val next = chosen.toMutableList()
                                                next[index] = grade
                                                onScenarioChange(encodeAutoScenario(next))
                                            }
                                        },
                                        modifier = Modifier.semantics { role = Role.RadioButton },
                                        shapes = when (i) {
                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            2 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                        },
                                        colors = ToggleButtonDefaults.colors(
                                            containerColor = colors.surfaceContainerHighest,
                                            contentColor = colors.onSurface,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                    ) { Text(grade.toString()) }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "Все контрольные мероприятия уже оценены. " +
                            "Изменить официальный результат здесь нельзя.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
