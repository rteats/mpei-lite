package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceNavigationStateTest {
    @Test fun rootBackReturnsScheduleAndThenYieldsToSystem() {
        val nav = ServiceNavigationState()
        assertEquals(AppTab.SCHEDULE, nav.current)
        assertFalse(nav.canPop)
        nav.open(AppTab.BARS)
        assertEquals(AppTab.BARS, nav.current)
        assertEquals(null, nav.beneath)
        assertEquals(AppTab.SCHEDULE, nav.peekBack())
        assertTrue(nav.pop())
        assertEquals(AppTab.SCHEDULE, nav.current)
        assertFalse(nav.pop())
    }

    @Test fun classroomNavigationRemembersScheduleBeneathMap() {
        val nav = ServiceNavigationState()
        nav.open(AppTab.SCHEDULE)
        nav.open(AppTab.MAPS, viaClassroom = true)
        assertEquals(listOf("SCHEDULE", "MAPS"), nav.routes)
        assertEquals(AppTab.MAPS, nav.current)
        assertEquals(AppTab.SCHEDULE, nav.beneath)
        assertEquals(AppTab.SCHEDULE, nav.peekBack())
        assertTrue(nav.pop())
        assertEquals(AppTab.SCHEDULE, nav.current)
    }

    @Test fun otherServicePushResetsPreviousServiceStack() {
        val nav = ServiceNavigationState()
        nav.open(AppTab.SCHEDULE)
        nav.open(AppTab.MAPS, viaClassroom = true)
        nav.open(AppTab.SETTINGS)
        assertEquals(listOf("MAPS", "SETTINGS"), nav.routes)
    }

    @Test fun stateReconstructionUsesOnlyPersistableRouteNames() {
        val restored = ServiceNavigationState(listOf("SCHEDULE", "MAPS"))
        assertEquals(AppTab.MAPS, restored.current)
        assertEquals(AppTab.SCHEDULE, restored.peekBack())
        assertEquals(listOf("SCHEDULE", "MAPS"), restored.routes)
    }
}
