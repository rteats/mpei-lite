package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAppBarTest {
    @Test fun barActionsStartChangingBeforeTheOutgoingPageHasClosed() {
        assertEquals(AppTab.HOME, searchAppBarDestinationAfterBack(listOf(AppTab.BARS.name)))
        assertEquals(AppTab.SCHEDULE, searchAppBarDestinationAfterBack(
            listOf(AppTab.SCHEDULE.name, AppTab.MAPS.name),
        ))
        assertEquals(AppTab.HOME, searchAppBarDestinationAfterBack(emptyList()))
    }

    @Test fun searchHintFollowsActualNavigationDestination() {
        assertEquals(SearchScope.SCHEDULE, searchAppBarContext(null).scope)
        assertEquals("Поиск расписания", searchAppBarContext(null).placeholder)
        assertEquals(SearchScope.SCHEDULE, searchAppBarContext(AppTab.SCHEDULE).scope)
        assertEquals("Группа, преподаватель, аудитория",
            searchAppBarContext(AppTab.SCHEDULE).placeholder)
        assertEquals(SearchScope.BARS, searchAppBarContext(AppTab.BARS).scope)
        assertEquals("Поиск дисциплины в БАРС",
            searchAppBarContext(AppTab.BARS).placeholder)
        assertEquals(SearchScope.MAIL, searchAppBarContext(AppTab.MAIL).scope)
        assertEquals("Поиск по почте", searchAppBarContext(AppTab.MAIL).placeholder)
        assertEquals(SearchScope.MAPS, searchAppBarContext(AppTab.MAPS).scope)
        assertEquals("Корпус или аудитория",
            searchAppBarContext(AppTab.MAPS).placeholder)
        assertEquals(AppBarMode.TITLE, appBarMode(AppTab.SETTINGS))
        assertEquals(AppBarMode.SEARCH, appBarMode(null))
        assertEquals(AppBarMode.SEARCH, appBarMode(AppTab.BARS))
        assertEquals(AppBarMode.SEARCH, appBarMode(AppTab.MAIL))
        assertEquals(AppBarMode.SEARCH, appBarMode(AppTab.SCHEDULE))
        assertEquals(AppBarMode.SEARCH, appBarMode(AppTab.MAPS))
    }

    @Test fun contextualAppBarUsesHomeAndServiceActions() {
        assertEquals(SearchAppBarAction.REFRESH_WEEK,
            searchAppBarAction(AppTab.SCHEDULE, mailListReady = true))
        assertEquals(SearchAppBarAction.COMPOSE_MAIL,
            searchAppBarAction(AppTab.MAIL, mailListReady = true))
        assertEquals(SearchAppBarAction.NONE,
            searchAppBarAction(AppTab.MAIL, mailListReady = false))
        assertEquals(SearchAppBarAction.NONE,
            searchAppBarAction(AppTab.MAPS, mailListReady = true))
        assertEquals(SearchAppBarAction.NONE,
            searchAppBarAction(null, mailListReady = true))
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

    @Test fun mapScopeIncludesBuildingsAndRoomTargetsOnlyWhenSearching() {
        assertTrue(SearchScope.MAPS.includesMaps)
        assertTrue(SearchScope.MAPS.includesSchedule)
        assertEquals(com.rteats.mpeineo.model.ScheduleTargetType.ROOM,
            SearchScope.MAPS.scheduleTargetTypeFilter(null))
        assertFalse(SearchScope.MAPS.showScheduleTargets(""))
        assertTrue(SearchScope.MAPS.showScheduleTargets("В-206"))
        assertFalse(SearchScope.MAPS.includesMail)
        assertFalse(SearchScope.MAPS.includesBars)
        assertTrue(SearchScope.MAPS.includesMaps)
    }
}
