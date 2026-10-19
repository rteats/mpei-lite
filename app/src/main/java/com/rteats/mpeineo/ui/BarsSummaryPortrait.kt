package com.rteats.mpeineo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A fixed-column projection of the actual BARS table, never calculated marks. */
internal data class BarsPortraitColumn(
    val index: Int,
    val label: String,
    val group: String = "",
    val isCurrentWeek: Boolean = false,
)

internal data class BarsPortraitSubject(
    val name: String,
    val values: List<BarsReportCell>,
) {
    fun cell(column: BarsPortraitColumn): BarsReportCell =
        values.getOrNull(column.index) ?: BarsReportCell("")
}

internal data class BarsPortraitMatrix(
    val columns: List<BarsPortraitColumn>,
    val subjects: List<BarsPortraitSubject>,
    val weekColumns: List<BarsPortraitColumn> = emptyList(),
    val summaryColumns: List<BarsPortraitColumn> = emptyList(),
)

private fun BarsReportCell.asDisplay(): String = value.ifBlank { "—" }

private fun flatCells(row: List<BarsReportCell>): List<BarsReportCell> =
    row.flatMap { cell -> List(cell.columnSpan.coerceIn(1, 64)) { cell } }

private fun headerCount(
    rows: List<List<BarsReportCell>>,
    grades: Boolean,
): Int {
    if (rows.isEmpty()) return 0
    val first = flatCells(rows.first()).map { it.value.lowercase() }
    if (rows.size < 2) return 0
    val second = flatCells(rows[1]).drop(1)
    return if (grades) {
        if (first.any { "недел" in it } &&
            second.any { it.value.trim().toIntOrNull() in 1..54 }) 2 else 0
    } else {
        if (first.any { "по журналу" in it } &&
            first.any { "по учебному плану" in it }) 2 else 0
    }
}

/**
 * Derive week positions from numeric labels in the SECOND header row,
 * not from their physical ordinal position. This also isolates ТБ/ПА/И.
 * The first header contributes group names and current-week highlighting.
 */
internal fun barsPortraitMatrix(
    rows: List<List<BarsReportCell>>,
    grades: Boolean,
): BarsPortraitMatrix {
    if (rows.isEmpty()) return BarsPortraitMatrix(emptyList(), emptyList())
    val headers = headerCount(rows, grades)
    val top = if (headers > 0) flatCells(rows[0]) else emptyList()
    val leaf = if (headers > 1) flatCells(rows[1]) else emptyList()
    val width = rows.maxOfOrNull { flatCells(it).size }?.coerceAtMost(120) ?: 0
    val columns = (1 until width).map { index ->
        val upper = top.getOrNull(index)
        val lower = leaf.getOrNull(index)
        val title = lower?.value?.takeIf(String::isNotBlank)
            ?: upper?.value?.takeIf(String::isNotBlank)
            ?: "Столбец $index"
        val group = upper?.value.orEmpty().takeUnless { it == title }.orEmpty()
        BarsPortraitColumn(
            index = index,
            label = title,
            group = group,
            isCurrentWeek = upper?.isCurrentWeek == true || lower?.isCurrentWeek == true,
        )
    }
    val subjects = rows.drop(headers).mapNotNull { row ->
        val expanded = flatCells(row)
        val title = expanded.firstOrNull()?.value?.trim().orEmpty()
        if (title.isBlank()) return@mapNotNull null
        BarsPortraitSubject(title, expanded)
    }
    val weeks = if (grades && headers == 2) {
        columns.filter { col ->
            col.group.contains("недел", ignoreCase = true) &&
                col.label.trim().toIntOrNull() in 1..54
        }
    } else emptyList()
    return BarsPortraitMatrix(
        columns = columns,
        subjects = subjects,
        weekColumns = weeks,
        summaryColumns = if (grades) columns.filterNot { it in weeks } else emptyList(),
    )
}

private fun BarsPortraitMatrix.attendanceValue(group: String, title: String,
    subject: BarsPortraitSubject): String? = columns.firstOrNull {
        it.group.contains(group, ignoreCase = true) &&
            it.label.contains(title, ignoreCase = true)
    }?.let { subject.cell(it).value.takeIf(String::isNotBlank) }

@Composable
internal fun BarsGradeWeekPicker(
    matrix: BarsPortraitMatrix,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    if (matrix.weekColumns.isEmpty()) return
    val current = matrix.weekColumns.indexOfFirst { it.isCurrentWeek }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = current)
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Неделя обучения",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            modifier = Modifier.fillMaxWidth().testTag("bars-week-picker"),
        ) {
            items(matrix.weekColumns, key = { it.index }) { week ->
                FilterChip(
                    selected = week.index == selectedIndex,
                    onClick = { onSelected(week.index) },
                    label = {
                        Text(
                            week.label + if (week.isCurrentWeek) " · сейчас" else "",
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun BarsGradeSubjectCard(
    subject: BarsPortraitSubject,
    matrix: BarsPortraitMatrix,
    selectedColumn: BarsPortraitColumn?,
) {
    var expanded by rememberSaveable(subject.name) { mutableStateOf(false) }
    val value = selectedColumn?.let(subject::cell) ?: BarsReportCell("")
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .testTag("bars-grade-subject"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    subject.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selectedColumn != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (value.isEmphasized) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            value.asDisplay(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (value.isEmphasized) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (matrix.summaryColumns.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    matrix.summaryColumns.forEach { column ->
                        val result = subject.cell(column)
                        Text(
                            column.label + " " + result.asDisplay(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (expanded) {
                Text(
                    "По неделям",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    matrix.weekColumns.forEach { week ->
                        val cell = subject.cell(week)
                        val isSelected = week.index == selectedColumn?.index
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                week.label + ": " + cell.asDisplay(),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    // If an unknown BARS header was present, don't discard it.
                    if (matrix.weekColumns.isEmpty()) {
                        matrix.columns.forEach { column ->
                            Surface(shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(column.label + ": " + subject.cell(column).asDisplay(),
                                    modifier = Modifier.padding(9.dp),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BarsAttendanceSubjectCard(
    subject: BarsPortraitSubject,
    matrix: BarsPortraitMatrix,
) {
    var expanded by rememberSaveable(subject.name) { mutableStateOf(false) }
    val journalCount = matrix.attendanceValue("журнал", "Занят", subject)
    val journalMissed = matrix.attendanceValue("журнал", "Пропущено", subject)
    val journalPercent = matrix.attendanceValue("журнал", "Без уважительной", subject)
    val planCount = matrix.attendanceValue("плану", "Занят", subject)
    val planPercent = matrix.attendanceValue("плану", "Пропущено", subject)
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            .testTag("bars-attendance-subject"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(subject.name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Пропущено: " + (journalMissed ?: "—") +
                    (journalCount?.let { " / $it" } ?: ""),
                    style = MaterialTheme.typography.labelLarge)
            }
            Text(
                listOfNotNull(
                    journalPercent?.let { "Без ув.: $it%" },
                    planCount?.let { "По плану: $it занятий" },
                    planPercent?.let { "Без ув. (план): $it%" },
                ).joinToString(" · ").ifBlank { "Нажмите, чтобы раскрыть показатели" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                matrix.columns.groupBy { it.group }.forEach { (group, columns) ->
                    Text(group.ifBlank { "Показатели" },
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    columns.forEach { column ->
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(column.label, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(subject.cell(column).asDisplay(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
