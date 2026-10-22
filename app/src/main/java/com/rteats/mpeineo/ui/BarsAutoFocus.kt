package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/**
 * A few concrete next moves, across disciplines, instead of a second grid
 * of repeated grade pills. The full what-if plan is inside each discipline.
 */
@Composable
internal fun BarsAutoFocus(
    disciplines: List<BarsDiscipline>,
    onOpenDiscipline: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var orderByWeek by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val priorities = remember(disciplines, orderByWeek) {
        val candidates = semesterAutoFocus(disciplines)
        if (orderByWeek) candidates.sortedWith(
            compareBy<SemesterAutoFocus> {
                it.week?.filter(Char::isDigit)?.toIntOrNull() ?: Int.MAX_VALUE
            }.thenByDescending { it.weight * (it.recommendedMark - 3) }
        ).take(3) else candidates.take(3)
    }
    if (priorities.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(
                    AppIcons.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Фокус на автомат",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Свернуть" else "Все")
                }
                BarsInfoButton(
                    title = "Как выбираются приоритеты",
                    sections = listOf(
                        BarsInfoSection(
                            "По влиянию",
                            "Первыми показаны контрольные мероприятия, где повышение " +
                                "предполагаемой оценки сильнее увеличивает взвешенную сумму. " +
                                "Учитываются доступные оценки и веса БАРС.",
                        ),
                        BarsInfoSection(
                            "По неделям",
                            "Сначала ближайшие КМ с указанной учебной неделей. " +
                                "КМ без номера недели располагаются в конце.",
                        ),
                        BarsInfoSection(
                            "Ограничения",
                            "Это рекомендации для планирования, а не назначенные " +
                                "преподавателем оценки и не гарантия автомата.",
                        ),
                    ),
                    tag = "bars-auto-focus-info",
                )
            }
            if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !orderByWeek,
                    onClick = { orderByWeek = false },
                    label = { Text("По влиянию") },
                )
                FilterChip(
                    selected = orderByWeek,
                    onClick = { orderByWeek = true },
                    label = { Text("По неделям") },
                )
            }
            }
            priorities.take(if (expanded) 3 else 1).forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                }
                Surface(
                    onClick = { onOpenDiscipline(item.disciplineName) },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                item.disciplineName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.activityName + " · " +
                                    "вес ${(item.weight * 100).toInt()}%" +
                                    (item.week?.let { " · нед. $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "Цель: ${item.recommendedMark}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        AppIcon(
                            AppIcons.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
