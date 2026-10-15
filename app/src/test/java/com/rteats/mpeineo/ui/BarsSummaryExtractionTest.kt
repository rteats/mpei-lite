package com.rteats.mpeineo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protects the DOM contracts used to fetch both native BARS Summary sections. */
class BarsSummaryExtractionTest {
    @Test fun summaryReadinessSupportsShortPopulatedTables() {
        assertTrue(BARS_PAGE_STATE_SCRIPT.contains("hasReportData(markMatrix)"))
        assertTrue(BARS_PAGE_STATE_SCRIPT.contains("hasReportData(attendance)"))
        assertTrue(BARS_PAGE_STATE_SCRIPT.contains("rows.length >= 2"))
        assertFalse(BARS_PAGE_STATE_SCRIPT.contains("querySelectorAll(\"tr\").length >= 4"))
    }

    @Test fun summarySnapshotUsesSameReadinessContract() {
        assertTrue(BARS_SUMMARY_RAW_SCRIPT.contains("hasReportData(grades)"))
        assertTrue(BARS_SUMMARY_RAW_SCRIPT.contains("hasReportData(attendance)"))
        assertTrue(BARS_SUMMARY_RAW_SCRIPT.contains("rows.length >= 2"))
        assertTrue(BARS_SUMMARY_RAW_SCRIPT.contains("onRawSnapshot('SUMMARY'"))
        assertFalse(BARS_SUMMARY_RAW_SCRIPT.contains("querySelectorAll('tr').length < 4"))
    }
}
