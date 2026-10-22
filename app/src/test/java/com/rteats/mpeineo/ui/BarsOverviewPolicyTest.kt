package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsOverviewPolicyTest {
    @Test fun disciplinesAreAlwaysThePrimaryDestination() {
        assertEquals("subjects", BarsOverviewSection.entries.first().key)
        assertEquals("Дисциплины", barsSectionTitle("subjects"))
    }

    @Test fun reportLabelsAndIdsHaveOneSource() {
        assertEquals(
            listOf("subjects", "grades", "attendance", "controls"),
            BarsOverviewSection.entries.map { it.key },
        )
        assertEquals("По неделям", barsSectionTitle("grades"))
        assertEquals("Посещаемость", barsSectionTitle("attendance"))
        assertEquals("Контрольные", barsSectionTitle("controls"))
        assertEquals("БАРС", barsSectionTitle("unrecognized"))
        assertTrue(BarsOverviewSection.entries.map { it.key }.distinct().size ==
            BarsOverviewSection.entries.size)
    }
}
