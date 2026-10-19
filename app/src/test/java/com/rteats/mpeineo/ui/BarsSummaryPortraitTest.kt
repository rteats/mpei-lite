package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The real Summary has two header rows and responsive duplicate tbody rows. */
class BarsSummaryPortraitTest {
    private val gradeRaw = """
        Семестр: Осенний 2026
        === Матрица оценок по неделям (ТБ, ПА, И) ===
        1: Дисциплина [rowspan=2] | Неделя обучения [colspan=3] | ТБ [rowspan=2] | ПА [rowspan=2] | И [rowspan=2]
        2: 1 | 2 [current-week] | 3
        3: ∅ | Дублирующий мобильный заголовок [colspan=6]
        4: Математика | 5 | 4 [mark-highlight] | ∅ | 4,5 | 5 | ∅
        5: ∅ | Дублирующий мобильный заголовок [colspan=6]
        6: Физика | ∅ | 3 | 5 | 4 | ∅ | 5
    """.trimIndent()

    @Test fun weeksAndSummaryAreReadFromSeparateHeaderColumns() {
        val report = BarsReportParser.parseSummary(gradeRaw)
        assertEquals(4, report.gradeRows.size)
        val matrix = barsPortraitMatrix(report.gradeRows, grades = true)
        assertEquals(listOf("1", "2", "3"), matrix.weekColumns.map { it.label })
        assertEquals(listOf("ТБ", "ПА", "И"), matrix.summaryColumns.map { it.label })
        assertEquals("2", matrix.weekColumns.single { it.isCurrentWeek }.label)
        assertEquals(listOf("Математика", "Физика"), matrix.subjects.map { it.name })
        assertEquals("4", matrix.subjects[0].cell(matrix.weekColumns[1]).value)
        assertEquals("5", matrix.subjects[0].cell(matrix.summaryColumns[1]).value)
        assertEquals("", matrix.subjects[1].cell(matrix.weekColumns[0]).value)
        assertTrue(matrix.subjects[0].cell(matrix.weekColumns[1]).isEmphasized)
    }

    @Test fun attendanceGroupsRetainSixDistinctIndicatorsAndTheirValues() {
        val raw = """
            === Посещаемость по журналу и учебному плану ===
            1: Дисциплина [rowspan=2] | По журналу (внесено занятий) [colspan=4] | По учебному плану [colspan=2]
            2: Занятий | Пропущено | По уважительной причине | Без уважительной причины, % | Занятий | Пропущено без уважительной причины, %
            3: ∅ | Мобильный заголовок [colspan=6]
            4: Физика | 8 | 2 | 1 | 12,5 | 16 | 6,25
        """.trimIndent()
        val matrix = barsPortraitMatrix(
            BarsReportParser.parseSummary(raw).attendanceRows, grades = false,
        )
        assertEquals(6, matrix.columns.size)
        assertEquals(1, matrix.subjects.size)
        assertEquals(listOf("8", "2", "1", "12,5", "16", "6,25"),
            matrix.columns.map { matrix.subjects[0].cell(it).value })
        assertEquals(4, matrix.columns.count { "журналу" in it.group })
        assertEquals(2, matrix.columns.count { "плану" in it.group })
    }

    @Test fun emptyReportDoesNotInventWeekNumbersOrSubjects() {
        val model = barsPortraitMatrix(emptyList(), grades = true)
        assertTrue(model.weekColumns.isEmpty())
        assertTrue(model.subjects.isEmpty())
    }

    @Test fun extractorExcludesExtraResponsiveRowsAndDuplicateShortLabels() {
        assertTrue(BARS_SUMMARY_RAW_SCRIPT.contains("summary-header-min"))
        assertTrue(BARS_SUMMARY_RAW_SCRIPT.contains("canonical.querySelectorAll"))
        assertTrue(BARS_SUMMARY_RAW_SCRIPT.contains("d-inline.d-sm-none"))
        assertFalse(BARS_SUMMARY_RAW_SCRIPT.contains(
            "Array.from(table.querySelectorAll('tr')).slice(0, maxRows)",
        ))
    }
}
