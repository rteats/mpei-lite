package com.rteats.mpeineo.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Interprets only existing, cached, visible BARS Main/Summary snapshots. */
internal data class BarsMainExtras(
    val indicators: List<String> = emptyList(),
    val studentFields: List<String> = emptyList(),
)

/**
 * Semantic profile facts derived from the existing Main snapshot and the
 * already-structured student card. Rendering never depends on raw prose.
 */
internal enum class BarsProfileKind {
    COURSE, RATING, SCORE, FORM, STATUS, FACULTY, INSTITUTE,
    PROGRAM, GROUP, SEMESTER, STUDENT_ID, OTHER,
}

internal data class BarsProfileFact(
    val label: String,
    val value: String,
    val kind: BarsProfileKind,
)

private fun profileFact(label: String, value: String): BarsProfileFact? {
    val cleanLabel = label.trim().removeSuffix(":").trim()
    val cleanValue = value.trim().replace(Regex("""\s+"""), " ")
    if (cleanLabel.isBlank() || cleanValue.isBlank() ||
        cleanLabel.length > 70 || cleanValue.length > 200
    ) return null
    val key = cleanLabel.lowercase().replace('ё', 'е')
        .replace(Regex("""\s+"""), " ")
    val kind = when {
        key == "курс" || key == "текущий курс" || key == "курс обучения" ->
            BarsProfileKind.COURSE
        key == "рейтинг" || key.contains("в рейтинге") ->
            BarsProfileKind.RATING
        key == "балл" || key.contains("средний балл") ||
            key.contains("текущий балл") -> BarsProfileKind.SCORE
        key.contains("форма обучения") -> BarsProfileKind.FORM
        key.startsWith("статус") -> BarsProfileKind.STATUS
        key.startsWith("факультет") -> BarsProfileKind.FACULTY
        key.startsWith("институт") -> BarsProfileKind.INSTITUTE
        key.startsWith("направление") || key.startsWith("специальность") ||
            key.startsWith("профиль") || key.startsWith("уровень образования") ||
            key.startsWith("кафедра") -> BarsProfileKind.PROGRAM
        key == "группа" || key == "учебная группа" -> BarsProfileKind.GROUP
        key == "семестр" || key == "текущий семестр" -> BarsProfileKind.SEMESTER
        key.contains("зачетн") || key.contains("зачётн") ||
            key.contains("студенческий билет") -> BarsProfileKind.STUDENT_ID
        else -> BarsProfileKind.OTHER
    }
    val displayLabel = when (kind) {
        BarsProfileKind.COURSE -> "Курс"
        BarsProfileKind.RATING -> "Рейтинг"
        BarsProfileKind.FORM -> "Форма обучения"
        BarsProfileKind.STATUS -> "Статус"
        BarsProfileKind.GROUP -> "Группа"
        BarsProfileKind.SEMESTER -> "Семестр"
        else -> cleanLabel
    }
    // Avoid repeating a unit in the label and value on the overview.
    val displayValue = if (kind == BarsProfileKind.COURSE) {
        cleanValue.replace(Regex("""\s*(?:-?(?:й|ый))?\s*курс(?:а)?$""",
            RegexOption.IGNORE_CASE), "").ifBlank { cleanValue }
    } else cleanValue
    return BarsProfileFact(displayLabel, displayValue, kind)
}

internal fun parseBarsProfileFact(raw: String): BarsProfileFact? {
    val text = raw.trim().replace(Regex("""\s+"""), " ")
    if (text.isBlank()) return null
    val numberedCourse = Regex("""^(\d+)(?:-?(?:й|ый))?\s+курс(?:а)?$""",
        RegexOption.IGNORE_CASE).matchEntire(text)
    if (numberedCourse != null) {
        return profileFact("Курс", numberedCourse.groupValues[1])
    }
    val formOnly = Regex("""^(очная|заочная|очно-заочная)(?:\s+форма\s+обучения)?$""",
        RegexOption.IGNORE_CASE).matchEntire(text)
    if (formOnly != null) return profileFact("Форма обучения", formOnly.groupValues[1])
    val separated = Regex("""^([^:：]{2,70})[:：]\s*(.+)$""").matchEntire(text)
    if (separated != null) {
        return profileFact(separated.groupValues[1], separated.groupValues[2])
    }
    // Some BARS inline indicators have no colon (e.g. "Текущий курс 3").
    val unseparated = Regex(
        """^(текущий курс|курс обучения|курс|место в рейтинге|рейтинг|средний балл|балл|форма обучения|статус студента|статус|факультет|институт|группа|семестр)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    ).matchEntire(text)
    return unseparated?.let { profileFact(it.groupValues[1], it.groupValues[2]) }
}

/** The official parsed student card takes precedence over snapshot fallbacks. */
internal fun barsProfileFacts(
    extras: BarsMainExtras,
    studentDetails: List<BarsStudentDetail>,
): List<BarsProfileFact> = (
    studentDetails.mapNotNull { profileFact(it.label, it.value) } +
        extras.indicators.mapNotNull(::parseBarsProfileFact) +
        extras.studentFields.mapNotNull(::parseBarsProfileFact)
).filterNot { it.label.equals("Студент", ignoreCase = true) }
    .distinctBy { fact ->
        if (fact.kind == BarsProfileKind.OTHER ||
            fact.kind == BarsProfileKind.PROGRAM
        ) fact.label.lowercase() else fact.kind.name
    }

internal fun BarsProfileKind.featurePriority(): Int = when (this) {
    BarsProfileKind.COURSE -> 0
    BarsProfileKind.RATING -> 1
    BarsProfileKind.SCORE -> 2
    BarsProfileKind.FORM -> 3
    BarsProfileKind.STATUS -> 4
    BarsProfileKind.FACULTY -> 5
    BarsProfileKind.INSTITUTE -> 6
    BarsProfileKind.PROGRAM -> 7
    else -> 100
}

internal data class BarsReportCell(
    val value: String,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
    val isCurrentWeek: Boolean = false,
    val isEmphasized: Boolean = false,
)

internal data class BarsSummaryReport(
    val semester: String = "",
    val gradeRows: List<List<BarsReportCell>> = emptyList(),
    val attendanceRows: List<List<BarsReportCell>> = emptyList(),
    val notes: List<String> = emptyList(),
)

internal object BarsReportParser {
    private val numberedRow = Regex("""^\d+:\s*(.*)$""")
    private val cellMetadata = Regex("""\s*\[([^\]]+)]$""")
    private val columnSpan = Regex("""colspan=(\d+)""")
    private val rowSpan = Regex("""rowspan=(\d+)""")

    fun parseMain(raw: String): BarsMainExtras {
        var section = ""
        val indicators = mutableListOf<String>()
        val studentFields = mutableListOf<String>()
        raw.lineSequence().forEach { line ->
            val text = line.trim()
            when {
                text.startsWith("===") -> section = when {
                    text.contains("Профиль") -> "profile"
                    text.contains("Карточка студента") -> "student"
                    else -> ""
                }
                text.isBlank() -> Unit
                section == "profile" -> indicators.add(text)
                section == "student" -> studentFields.add(text)
            }
        }
        return BarsMainExtras(indicators.distinct(), studentFields.distinct())
    }

    fun parseSummary(raw: String): BarsSummaryReport {
        var section = ""
        var semester = ""
        val grades = mutableListOf<List<BarsReportCell>>()
        val attendance = mutableListOf<List<BarsReportCell>>()
        val notes = mutableListOf<String>()
        raw.lineSequence().forEach { line ->
            val text = line.trim()
            when {
                text.startsWith("Семестр:") -> semester = text.substringAfter(":").trim()
                text.startsWith("===") -> section = when {
                    text.contains("Матрица оценок") -> "grades"
                    text.contains("Посещаемость") -> "attendance"
                    text.contains("Пояснения") -> "notes"
                    else -> ""
                }
                section == "notes" && text.isNotBlank() -> notes.add(text)
                section == "grades" || section == "attendance" -> {
                    val row = numberedRow.matchEntire(text) ?: return@forEach
                    val cells = row.groupValues[1].split(" | ").map(::parseCell)
                    if (section == "grades") grades.add(cells) else attendance.add(cells)
                }
            }
        }
        return BarsSummaryReport(
            semester, alignRows(grades), alignRows(attendance), notes.distinct(),
        )
    }

    /**
     * The official report uses rowspan/colspan. Insert carried cells into
     * subsequent rows, otherwise a discipline's multi-week assessment can
     * shift into the fixed first column and corrupt table alignment.
     */
    private fun alignRows(rows: List<List<BarsReportCell>>): List<List<BarsReportCell>> {
        data class Carry(val cell: BarsReportCell, val start: Int, val remaining: Int)
        val active = mutableMapOf<Int, Carry>()
        return rows.map { source ->
            val occupied = active.toMap()
            active.clear()
            occupied.forEach { (column, carry) ->
                if (carry.remaining > 1) {
                    active[column] = carry.copy(remaining = carry.remaining - 1)
                }
            }

            val placed = mutableMapOf<Int, BarsReportCell>()
            var cursor = 0
            source.forEach { cell ->
                while ((cursor until cursor + cell.columnSpan).any {
                    it in occupied || it in placed
                }) cursor++
                placed[cursor] = cell
                if (cell.rowSpan > 1) {
                    val carry = Carry(cell, cursor, cell.rowSpan - 1)
                    (cursor until cursor + cell.columnSpan).forEach { active[it] = carry }
                }
                cursor += cell.columnSpan
            }

            val lastColumn = maxOf(
                (placed.entries.maxOfOrNull { it.key + it.value.columnSpan } ?: 0),
                ((occupied.keys.maxOrNull() ?: -1) + 1),
            )
            buildList {
                var column = 0
                while (column < lastColumn) {
                    val cell = placed[column]
                    val carry = occupied[column]
                    when {
                        cell != null -> {
                            add(cell)
                            column += cell.columnSpan
                        }
                        carry != null && carry.start == column -> {
                            add(carry.cell.copy(rowSpan = 1))
                            column += carry.cell.columnSpan
                        }
                        else -> {
                            add(BarsReportCell(""))
                            column++
                        }
                    }
                }
            }
        }
    }

    private fun parseCell(text: String): BarsReportCell {
        val meta = cellMetadata.find(text)
        val value = text.removeSuffix(meta?.value.orEmpty()).trim().let {
            if (it == "∅") "" else it
        }
        val flags = meta?.groupValues?.get(1).orEmpty()
        return BarsReportCell(
            value = value,
            columnSpan = columnSpan.find(flags)?.groupValues?.get(1)
                ?.toIntOrNull()?.coerceIn(1, 64) ?: 1,
            rowSpan = rowSpan.find(flags)?.groupValues?.get(1)
                ?.toIntOrNull()?.coerceIn(1, 64) ?: 1,
            isCurrentWeek = "current-week" in flags,
            isEmphasized = "mark-highlight" in flags,
        )
    }
}

/** In-view navigation, not a second app bar or a nested screen. */
@Composable
internal fun BarsReportChooser(selected: String, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "subjects" to "Дисциплины",
            "grades" to "По неделям",
            "attendance" to "Посещаемость",
            "controls" to "Контрольные",
        ).forEach { (id, label) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelected(id) },
                label = { Text(label) },
            )
        }
    }
}

/**
 * Adds lazy, source-faithful rows into the parent's vertical scrolling list.
 * The first column stays visible while all other columns scroll together.
 */
internal fun LazyListScope.barsReportEntries(
    section: String,
    report: BarsSummaryReport,
    controls: List<BarsControlScheduleItem>,
    gradeScroll: ScrollState,
    attendanceScroll: ScrollState,
    onOpenDiscipline: (String) -> Unit,
) {
    if (section == "controls") {
        item(key = "controls-heading") {
            BarsReportHeading("Контрольные мероприятия", "В порядке учебных недель")
        }
        if (controls.isEmpty()) {
            item(key = "controls-empty") {
                BarsReportEmpty("Контрольных мероприятий с указанной неделей пока нет.")
            }
        } else {
            items(count = controls.size, key = { "control-$it" }) { index ->
                val control = controls[index]
                Card(
                    onClick = { onOpenDiscipline(control.discipline) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(control.discipline, style = MaterialTheme.typography.titleMedium)
                        if (control.activity.isNotBlank()) {
                            Text(control.activity, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            listOfNotNull(
                                "Неделя " + control.weekNum,
                                control.weight.takeIf { it.isNotBlank() }?.let { "Вес: $it" },
                                control.markAndDate.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        return
    }

    val isGrades = section == "grades"
    val rows = if (isGrades) report.gradeRows else report.attendanceRows
    val scroll = if (isGrades) gradeScroll else attendanceScroll
    val maxColumns = (rows.maxOfOrNull { row ->
        row.drop(1).sumOf { it.columnSpan }
    } ?: 1).coerceIn(1, 120)
    item(key = "report-heading") {
        BarsReportHeading(
            if (isGrades) "Оценки по неделям" else "Посещаемость",
            listOf(report.semester, "Данные БАРС")
                .filter { it.isNotBlank() }.joinToString(" · "),
        )
    }
    if (rows.isEmpty()) {
        item(key = "report-empty") {
            BarsReportEmpty(
                "Данные ещё не загружены. Обновите БАРС, чтобы увидеть " +
                    if (isGrades) "оценки по неделям." else "посещаемость.",
            )
        }
    } else {
        items(count = rows.size, key = { "report-$section-$it" }) { index ->
            BarsReportTableRow(rows[index], index, maxColumns, scroll)
        }
        if (report.notes.isNotEmpty()) {
            item(key = "report-notes") {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Пояснения", style = MaterialTheme.typography.titleSmall)
                    report.notes.forEach { note ->
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarsReportHeading(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BarsReportEmpty(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BarsReportTableRow(
    cells: List<BarsReportCell>,
    index: Int,
    columns: Int,
    scroll: ScrollState,
) {
    if (cells.isEmpty()) return
    val heading = index < 3
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp)
            .heightIn(min = 50.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarsReportTableCell(cells.first(), 148, heading)
        Row(modifier = Modifier.weight(1f).horizontalScroll(scroll)) {
            Row(modifier = Modifier.width((columns * 94).dp)) {
                cells.drop(1).forEach { cell ->
                    BarsReportTableCell(cell, 94 * cell.columnSpan, heading)
                }
            }
        }
    }
}

@Composable
private fun BarsReportTableCell(cell: BarsReportCell, widthDp: Int, heading: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.width(widthDp.dp).heightIn(min = 48.dp)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.small,
        color = when {
            cell.isCurrentWeek -> colors.primaryContainer
            cell.isEmphasized -> colors.tertiaryContainer
            heading -> colors.surfaceContainerHigh
            else -> colors.surfaceContainerLow
        },
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                cell.value,
                style = if (heading) MaterialTheme.typography.labelMedium
                    else MaterialTheme.typography.bodySmall,
                color = when {
                    cell.isCurrentWeek -> colors.onPrimaryContainer
                    cell.isEmphasized -> colors.onTertiaryContainer
                    else -> colors.onSurface
                },
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
