package com.rteats.mpeineo.data

import com.google.gson.Gson
import com.rteats.mpeineo.ui.BarsActivity
import com.rteats.mpeineo.ui.BarsActivityType
import com.rteats.mpeineo.ui.BarsDiscipline
import com.rteats.mpeineo.ui.BarsExtractionPayload
import com.rteats.mpeineo.ui.BarsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsCachedSnapshotTest {
    @Test fun savedGradesRoundTripWithoutWebCredentials() {
        val original = BarsCachedSnapshot(
            updatedAtMillis = 1_797_000_000_000L,
            payload = BarsExtractionPayload(
                name = "Студент",
                group = "А-12",
                semester = "Семестр",
                disciplines = listOf(
                    BarsDiscipline(
                        disciplineName = "Математика",
                        activities = listOf(
                            BarsActivity(
                                type = BarsActivityType.CONTROL_ACTIVITY,
                                markAndDate = "5",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val json = Gson().toJson(original)
        val restored = Gson().fromJson(json, BarsCachedSnapshot::class.java)
        assertEquals(original, restored)
        assertFalse(json.contains("cookie", ignoreCase = true))
        assertFalse(json.contains("password", ignoreCase = true))
    }

    @Test fun cachedDashboardIsImmediatelyAvailableWhileValidatingSession() {
        val initial = BarsUiState(
            disciplines = listOf(BarsDiscipline(disciplineName = "Математика")),
            isLoading = true,
            lastUpdatedAtMillis = 1_797_000_000_000L,
        )
        assertEquals(1, initial.disciplines.size)
        assertTrue(initial.isLoading)
    }

    @Test fun expiredSessionCanRetainSavedGrades() {
        val cached = BarsUiState(
            disciplines = listOf(BarsDiscipline(disciplineName = "Математика")),
            loginRequired = true,
            isLoading = false,
        )
        assertTrue(cached.loginRequired)
        assertEquals(1, cached.disciplines.size)
    }
}
