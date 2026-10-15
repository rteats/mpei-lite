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


    @Test fun parsesProfileIndicatorsIntoSemanticFacts() {
        val extras = BarsMainExtras(
            indicators = listOf(
                "Текущий курс: 3 курс",
                "Рейтинг 9",
                "Средний балл: 4,52",
                "Форма обучения: Очная",
                "3 курс",
                "неразмеченный текст из DOM",
            ),
        )
        val facts = barsProfileFacts(extras, emptyList())
        assertEquals(BarsProfileKind.COURSE, facts[0].kind)
        assertEquals("Курс", facts[0].label)
        assertEquals("3", facts[0].value)
        assertEquals(BarsProfileKind.RATING, facts[1].kind)
        assertEquals("9", facts[1].value)
        assertEquals(BarsProfileKind.SCORE, facts[2].kind)
        assertEquals("Средний балл", facts[2].label)
        assertEquals("4,52", facts[2].value)
        assertEquals(BarsProfileKind.FORM, facts[3].kind)
        assertEquals("Очная", facts[3].value)
        assertEquals(4, facts.size) // Duplicate course and opaque DOM text are omitted.
    }

    @Test fun studentCardTakesPrecedenceOverRepeatedRawFields() {
        val facts = barsProfileFacts(
            BarsMainExtras(
                indicators = listOf("Курс: 2", "Рейтинг: 5"),
                studentFields = listOf("Факультет: другой", "Непонятная строка"),
            ),
            listOf(
                BarsStudentDetail("Курс", "3"),
                BarsStudentDetail("Факультет", "ИРЭ"),
                BarsStudentDetail("Форма обучения", "Очная"),
            ),
        )
        assertEquals("3", facts.first { it.kind == BarsProfileKind.COURSE }.value)
        assertEquals("ИРЭ", facts.first { it.kind == BarsProfileKind.FACULTY }.value)
        assertEquals(1, facts.count { it.kind == BarsProfileKind.COURSE })
        assertEquals(1, facts.count { it.kind == BarsProfileKind.FACULTY })
        assertTrue(facts.none { it.label == "Непонятная строка" })
    }

    @Test fun ignoresUnstructuredPrivateOrOversizedData() {
        assertEquals(null, parseBarsProfileFact("Вход в систему"))
        assertEquals(null, parseBarsProfileFact("Статус: "))
        assertEquals(null, parseBarsProfileFact("Профиль: " + "x".repeat(250)))
        assertEquals(BarsProfileKind.COURSE, parseBarsProfileFact("4 курс")?.kind)
        assertEquals(BarsProfileKind.FORM,
            parseBarsProfileFact("Очно-заочная форма обучения")?.kind)
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
