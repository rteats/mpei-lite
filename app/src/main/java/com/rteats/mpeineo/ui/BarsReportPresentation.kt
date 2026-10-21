package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

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
                    // Old cached snapshots included BARS's extra responsive
                    // .summary-header-min row for every discipline (an empty
                    // leading cell followed by one very wide heading). Drop
                    // these before resolving rowspan positions.
                    val responsiveHeading = cells.size <= 3 &&
                        cells.firstOrNull()?.value.isNullOrBlank() &&
                        cells.drop(1).any { it.columnSpan >= 5 }
                    if (!responsiveHeading) {
                        if (section == "grades") grades.add(cells)
                        else attendance.add(cells)
                    }
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
    selectedWeekColumn: Int,
    onSelectWeek: (Int) -> Unit,
    onOpenDiscipline: (String) -> Unit,
    isSummaryLoading: Boolean,
    summaryError: String?,
    onRetrySummary: () -> Unit,
    lastVerifiedAtMillis: Long = 0L,
) {
    if (section == "controls") {
        item(key = "controls-heading") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    BarsReportHeading("Контрольные мероприятия", "")
                }
                BarsInfoButton(
                    title = "О контрольных мероприятиях",
                    sections = listOf(
                        BarsInfoSection(
                            "Порядок",
                            "КМ показаны в порядке учебных недель, если БАРС указал неделю. " +
                                "Откройте карточку для подробных оценок и сценария.",
                        ),
                        BarsInfoSection(
                            "Вес",
                            "Вес из БАРС показывает вклад отдельного КМ в прогноз. " +
                                "Дата и оценка выводятся только при наличии в источнике.",
                        ),
                    ),
                    tag = "bars-controls-info",
                )
            }
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
    // The official Summary uses two header rows and up to 19 columns.
    // Project the complete aligned matrix once, then render native portrait rows.
    val matrix = barsPortraitMatrix(rows, grades = isGrades)
    val week = matrix.weekColumns.firstOrNull { it.index == selectedWeekColumn }
        ?: matrix.weekColumns.firstOrNull { it.isCurrentWeek }
        ?: matrix.weekColumns.firstOrNull()
        ?: matrix.columns.firstOrNull()
    val verifiedText = lastVerifiedAtMillis.takeIf { it > 0L }?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(it))
    } ?: "Данные ещё не подтверждены"
    val infoSections = listOf(
        BarsInfoSection(
            "Источник и обновление",
            "Данные взяты из «Сводки» БАРС. " +
                "Последняя успешная загрузка: $verifiedText.",
        ),
        BarsInfoSection(
            if (isGrades) "Недели" else "Посещаемость",
            if (isGrades)
                "Выберите учебную неделю, чтобы сравнить оценки по дисциплинам. " +
                    "Значения соответствуют загруженной таблице БАРС."
            else
                "Отображаются сведения о посещаемости по данным БАРС. " +
                    "Приложение не изменяет и не пересчитывает официальные отметки.",
        ),
    ) + report.notes.mapIndexed { index, note ->
        BarsInfoSection(
            if (index == 0) "Пояснения из БАРС" else "",
            note,
        )
    }
    item(key = "report-heading") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BarsReportHeading(
                    if (isGrades) "Оценки по неделям" else "Посещаемость",
                    report.semester,
                )
            }
            BarsInfoButton(
                title = if (isGrades) "Об оценках по неделям" else "О посещаемости",
                sections = infoSections,
                tag = "bars-summary-info",
            )
            if (!isSummaryLoading) {
                TextButton(onClick = onRetrySummary) { Text("Обновить") }
            } else {
                CircularProgressIndicator(modifier = Modifier.width(24.dp))
            }
        }
    }
    if (summaryError != null) {
        item(key = "summary-error") {
            Text(
                summaryError,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (rows.isEmpty()) {
        item(key = "report-empty") {
            BarsReportEmpty(
                if (isSummaryLoading) "Получаем данные из Сводки БАРС…"
                else "Данные Сводки пока не получены. Нажмите «Обновить»."
            )
        }
    } else {
        if (isGrades && matrix.weekColumns.isNotEmpty()) {
            item(key = "report-week-picker") {
                BarsGradeWeekPicker(
                    matrix = matrix,
                    selectedIndex = week?.index ?: -1,
                    onSelected = onSelectWeek,
                )
            }
        }
        if (matrix.subjects.isEmpty()) {
            item(key = "report-no-subjects") {
                BarsReportEmpty("В таблице БАРС нет строк с дисциплинами.")
            }
        }
        items(count = matrix.subjects.size, key = { "report-$section-$it" }) { index ->
            val subject = matrix.subjects[index]
            if (isGrades) {
                BarsGradeSubjectCard(subject, matrix, week)
            } else {
                BarsAttendanceSubjectCard(subject, matrix)
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

