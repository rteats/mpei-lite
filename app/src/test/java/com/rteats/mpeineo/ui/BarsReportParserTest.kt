package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsReportParserTest {
    @Test fun separatesProfileFromExistingNativeDisciplines() {
        val raw = """
            Источник: /ST_Study/Main/Main
            === Профиль и сводные показатели ===
            Рейтинг: 9
            Балл: 4,5
            === Карточка студента (видимые поля) ===
            Факультет: ИРЭ
            === Дисциплины и контрольные мероприятия ===
            Дисциплина: Математика
            1: мероприятие | 5
        """.trimIndent()
        assertEquals(
            listOf("Рейтинг: 9", "Балл: 4,5"),
            BarsReportParser.parseMain(raw).indicators,
        )
        assertEquals(
            listOf("Факультет: ИРЭ"),
            BarsReportParser.parseMain(raw).studentFields,
        )
    }

    @Test fun readsActualSummaryRowsAndSourceMetadata() {
        val raw = """
            Источник: /ST_Study/Main/Summary
            Семестр: Осенний
            === Матрица оценок по неделям (ТБ, ПА, И) ===
            1: Предмет [rowspan=2] | 5 [current-week,mark-highlight] | ∅ [colspan=2]
            2: 3 | 4
            === Посещаемость по журналу и учебному плану ===
            1: Физика | 2 [colspan=3]
            === Пояснения и примечания ===
            ТБ — текущий балл
        """.trimIndent()
        val parsed = BarsReportParser.parseSummary(raw)
        assertEquals("Осенний", parsed.semester)
        assertEquals(2, parsed.gradeRows.size)
        assertEquals(2, parsed.gradeRows[0][0].rowSpan)
        assertTrue(parsed.gradeRows[0][1].isCurrentWeek)
        assertTrue(parsed.gradeRows[0][1].isEmphasized)
        assertEquals("", parsed.gradeRows[0][2].value)
        assertEquals(2, parsed.gradeRows[0][2].columnSpan)
        // The first heading occupies two rows in the source table.
        assertEquals("Предмет", parsed.gradeRows[1][0].value)
        assertEquals(3, parsed.gradeRows[1].size)
        assertEquals(3, parsed.attendanceRows[0][1].columnSpan)
        assertEquals(listOf("ТБ — текущий балл"), parsed.notes)
    }

    @Test fun noInventedDataWithoutSnapshot() {
        val report = BarsReportParser.parseSummary("Источник: /ST_Study/Main/Summary")
        assertTrue(report.gradeRows.isEmpty())
        assertTrue(report.attendanceRows.isEmpty())
        assertFalse(report.semester.isNotBlank())
    }
}
