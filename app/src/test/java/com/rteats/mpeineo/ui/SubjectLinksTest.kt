package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubjectLinksTest {
    @Test fun sameSubjectWithCaseAndTypographyLinks() {
        val bars = BarsDiscipline(disciplineName = "Электрические цепи (теория)")
        assertEquals(
            bars, findBarsSubject("  ЭЛЕКТРИЧЕСКИЕ  ЦЕПИ — теория", listOf(bars))
                // An em dash is not removed, so this stays conservative.
        )
    }

    @Test fun unrelatedAndPartialMatchesAreNotLinked() {
        val courses = listOf(
            BarsDiscipline(disciplineName = "Математика"),
            BarsDiscipline(disciplineName = "Высшая математика"),
        )
        assertNull(findBarsSubject("Математика 2", courses))
        assertNull(findBarsSubject("математ", courses))
    }

    @Test fun ambiguousDuplicateNamesAreNotLinked() {
        val courses = listOf(
            BarsDiscipline(disciplineName = "Физика"),
            BarsDiscipline(disciplineName = "физика"),
        )
        assertNull(findBarsSubject("Физика", courses))
    }
}
