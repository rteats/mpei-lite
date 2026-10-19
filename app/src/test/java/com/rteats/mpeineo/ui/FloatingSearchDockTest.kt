package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingSearchDockTest {
    @Test fun searchHintFollowsActualNavigationDestination() {
        assertEquals(SearchScope.ALL, floatingSearchContext(null).scope)
        assertEquals("Поиск по МЭИ", floatingSearchContext(null).placeholder)
        assertEquals(SearchScope.SCHEDULE, floatingSearchContext(AppTab.SCHEDULE).scope)
        assertEquals("Группа, преподаватель, аудитория",
            floatingSearchContext(AppTab.SCHEDULE).placeholder)
        assertEquals(SearchScope.BARS, floatingSearchContext(AppTab.BARS).scope)
        assertEquals("Поиск дисциплины в БАРС",
            floatingSearchContext(AppTab.BARS).placeholder)
        assertEquals(SearchScope.MAIL, floatingSearchContext(AppTab.MAIL).scope)
        assertEquals("Поиск по почте", floatingSearchContext(AppTab.MAIL).placeholder)
        assertEquals(SearchScope.MAPS, floatingSearchContext(AppTab.MAPS).scope)
        assertEquals("Корпус или аудитория",
            floatingSearchContext(AppTab.MAPS).placeholder)
        assertEquals(SearchScope.ALL, floatingSearchContext(AppTab.SETTINGS).scope)
    }

    @Test fun campusSearchRecognizesKnownCyrillicBuildingsAndRooms() {
        assertEquals(listOf("А"), campusBuildingHits("а"))
        assertEquals(listOf("А"), campusBuildingHits("корпус А"))
        assertEquals(listOf("В"), campusBuildingHits("В-312"))
        assertEquals(listOf("Г"), campusBuildingHits("корп. г"))
        assertEquals(listOf("Э"), campusBuildingHits("Э — 105"))
        assertTrue(campusBuildingHits("аудитория 203").isEmpty())
        assertTrue(campusBuildingHits("неизвестный").isEmpty())
        assertEquals(campusSearchSymbols, campusBuildingHits(""))
    }

    @Test fun mapScopeIncludesScheduleSearchButNotMailOrBars() {
        assertTrue(SearchScope.MAPS.includesMaps)
        assertTrue(SearchScope.MAPS.includesSchedule)
        assertFalse(SearchScope.MAPS.includesMail)
        assertFalse(SearchScope.MAPS.includesBars)
        assertTrue(SearchScope.ALL.includesMaps)
    }
}
