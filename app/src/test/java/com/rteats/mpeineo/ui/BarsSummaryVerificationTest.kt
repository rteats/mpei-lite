package com.rteats.mpeineo.ui

import com.google.gson.Gson
import com.rteats.mpeineo.data.BarsRawSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsSummaryVerificationTest {
    @Test fun incompleteSummaryDoesNotCountAsVerified() {
        assertFalse(isCompleteBarsSummarySnapshot(""))
        assertFalse(isCompleteBarsSummarySnapshot("Источник: /ST_Study/Main/Summary"))
        assertFalse(isCompleteBarsSummarySnapshot(
            "=== Матрица оценок по неделям (ТБ, ПА, И) ==="
        ))
        assertTrue(isCompleteBarsSummarySnapshot(
            "=== Матрица оценок по неделям (ТБ, ПА, И) ===\n" +
                "=== Посещаемость по журналу и учебному плану ==="
        ))
    }

    @Test fun oldRawSnapshotsHaveNoFabricatedReportVerificationTimes() {
        val json = """{"main":"main","summary":"summary","updatedAtMillis":1234,"accountKey":"a"}"""
        val snapshot = Gson().fromJson(json, BarsRawSnapshot::class.java)
        assertEquals(1234L, snapshot.updatedAtMillis)
        assertEquals(0L, snapshot.mainUpdatedAtMillis)
        assertEquals(0L, snapshot.summaryUpdatedAtMillis)
    }
}
