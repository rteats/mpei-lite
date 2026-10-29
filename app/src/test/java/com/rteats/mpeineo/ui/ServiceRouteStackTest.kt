package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceRouteStackTest {
    @Test fun normalNavigationReplacesTheActiveService() {
        assertEquals(listOf(AppTab.MAIL.name),
            serviceRouteAfterOpen(emptyList(), AppTab.MAIL, false))
        assertEquals(listOf(AppTab.BARS.name),
            serviceRouteAfterOpen(listOf(AppTab.MAIL.name), AppTab.BARS, false))
    }

    @Test fun classroomOpensMapAboveScheduleOnly() {
        val nested = serviceRouteAfterOpen(
            listOf(AppTab.SCHEDULE.name), AppTab.MAPS, true)
        assertEquals(listOf(AppTab.SCHEDULE.name, AppTab.MAPS.name), nested)
        assertEquals(listOf(AppTab.SCHEDULE.name), serviceRouteAfterBack(nested))
    }

    @Test fun leavingRegularServiceReturnsSchedule() {
        assertEquals(listOf(AppTab.SCHEDULE.name),
            serviceRouteAfterBack(listOf(AppTab.BARS.name)))
        assertEquals(listOf(AppTab.MAIL.name, AppTab.MAPS.name),
            serviceRouteAfterOpen(listOf(AppTab.MAIL.name), AppTab.MAPS, true))
    }
    @Test fun snapshotKeepsCurrentAndPreviousDestinationsConsistent() {
        val home = ServiceRouteSnapshot(emptyList())
        assertEquals(null, home.current)
        assertEquals(null, home.beneath)
        val schedule = ServiceRouteSnapshot(home.open(AppTab.SCHEDULE))
        assertEquals(AppTab.SCHEDULE, schedule.current)
        val map = ServiceRouteSnapshot(schedule.open(AppTab.MAPS, viaClassroom = true))
        assertEquals(AppTab.MAPS, map.current)
        assertEquals(AppTab.SCHEDULE, map.beneath)
        assertEquals(listOf(AppTab.SCHEDULE.name), map.back())
        assertEquals(listOf(AppTab.SCHEDULE.name), ServiceRouteSnapshot(map.back()).back())
    }
}
