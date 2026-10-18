package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import kotlin.math.roundToInt

/**
 * A what-if planner, not an official BARS final grade.
 * There are no network writes: grade buttons only change hypothetical scores.
 */
@Composable
internal fun BarsAutoPlanner(
    discipline: BarsDiscipline,
    savedScenario: String?,
    onScenarioChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = remember(discipline) { buildAutoPlan(discipline) }
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
                "План на автомат",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Цель 4,20",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    "Не хватает ${formatBarsWeightedSum(scenario.gap)}",
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
                LinearProgressIndicator(
                    progress = { (scenario.sum / plan.target).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    "Уже получено: ${formatBarsWeightedSum(plan.earned)} · " +
                        "если все 3: ${formatBarsWeightedSum(plan.lowerBound)} · " +
                        "максимум: ${formatBarsWeightedSum(plan.upperBound)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                if (plan.totalWeight < 0.9999) {
                    Text(
                        "Веса КМ суммарно составляют " +
                            "${(plan.totalWeight * 100).roundToInt()}%. " +
                            "Недостающий вес не придуман и в прогноз не включён.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                if (plan.upcoming.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Сценарий для оставшихся КМ",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "${scenario.stepsAboveThree} шагов выше 3",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
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
                        ) { Text("Рекомендация") }
                        TextButton(
                            onClick = {
                                onScenarioChange(
                                    encodeAutoScenario(List(plan.upcoming.size) { 3 }),
                                )
                            },
                        ) { Text("Все 3") }
                        TextButton(
                            onClick = {
                                onScenarioChange(
                                    encodeAutoScenario(List(plan.upcoming.size) { 5 }),
                                )
                            },
                        ) { Text("Все 5") }
                    }
                    Text(
                        if (plan.canReachTarget)
                            "Рекомендация просит минимум повышений оценок относительно троек. " +
                                "Больше всего влияет КМ с наибольшим весом."
                        else
                            "Расставьте предполагаемые оценки, чтобы оценить лучший " +
                                "возможный результат. Все полученные оценки фиксированы.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )

                    Text(
                        "Приоритеты по влиянию",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Наиболее весомые работы сверху. Неделя помогает выбрать, " +
                            "к чему готовиться сначала.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )

                    autoPriorities(plan).forEachIndexed { rank, slot ->
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
                                    fontWeight = if (rank == 0) FontWeight.SemiBold
                                        else FontWeight.Medium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildList {
                                        add("Вес ${(slot.weight * 100).roundToInt()}%")
                                        slot.week?.let { add("Неделя ${it}") }
                                        add("+${formatBarsWeightedSum(slot.weight)} за балл")
                                    }.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            SingleChoiceSegmentedButtonRow {
                                listOf(3, 4, 5).forEachIndexed { i, grade ->
                                    SegmentedButton(
                                        selected = chosen[index] == grade,
                                        onClick = {
                                            val next = chosen.toMutableList()
                                            next[index] = grade
                                            onScenarioChange(encodeAutoScenario(next))
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = i, count = 3,
                                        ),
                                        icon = {},
                                        label = {
                                            Text(grade.toString())
                                        },
                                    )
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
                Text(
                    "Прогноз по весам БАРС, не гарантия автомата: решение " +
                        "зависит от правил дисциплины и преподавателя.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
