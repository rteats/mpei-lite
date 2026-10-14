package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampusBuildingResolverTest {
    @Test fun roomPrefixMapsToBuilding() {
        assertEquals("А", campusBuildingForRoom("А-305"))
        assertEquals("Ж", campusBuildingForRoom("ауд. Ж-214"))
        assertEquals("Э", campusBuildingForRoom("Э—312 (лаборатория)"))
        assertEquals("Т", campusBuildingForRoom("Т-3-15"))
    }

    @Test fun namedCampusBuildingsMapWithoutRoomNumbers() {
        assertEquals("Б", campusBuildingForRoom("Корпус Б, ауд. 104"))
        assertEquals("Н", campusBuildingForRoom("корп. Н"))
        assertEquals("М", campusBuildingForRoom("Занятие: корпус М"))
    }

    @Test fun nonCampusAndAmbiguousRoomsHaveNoLink() {
        assertNull(campusBuildingForRoom(""))
        assertNull(campusBuildingForRoom("Дистанционно"))
        assertNull(campusBuildingForRoom("ауд. 305"))
        assertNull(campusBuildingForRoom("Общежитие 5"))
        assertNull(campusBuildingForRoom("А-305, Б-105"))
        assertNull(campusBuildingForRoom("B-305"))
    }
}
