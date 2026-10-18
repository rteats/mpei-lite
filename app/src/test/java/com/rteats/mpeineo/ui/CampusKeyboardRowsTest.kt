package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CampusKeyboardRowsTest {
    private val symbols = listOf(
        "А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К",
        "Л", "М", "Н", "Р", "С", "Т", "Ф", "Х", "Э",
    )

    @Test fun tabletKeyboardKeepsEveryLetterInOrder() {
        val rows = campusKeyboardRows(symbols, 10)
        assertEquals(listOf(10, 9), rows.map { it.size })
        assertEquals(symbols, rows.flatten())
    }

    @Test fun narrowKeyboardWrapsWithoutHidingLastBuilding() {
        val rows = campusKeyboardRows(symbols, 6)
        assertEquals(listOf(6, 6, 6, 1), rows.map { it.size })
        assertEquals(symbols, rows.flatten())
    }

    @Test fun duplicateOrInvalidColumnCountCannotLoseLetters() {
        val rows = campusKeyboardRows(listOf("А", "Б", "А", "В"), 0)
        assertEquals(listOf(listOf("А"), listOf("Б"), listOf("В")), rows)
    }
}
