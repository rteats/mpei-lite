package com.rteats.mpeineo.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusAreaDataTest {
    private val osm = """
        {
          "elements": [
            {"type":"way","id":101,"tags":{"building":"yes","name":"Корпус А"},
             "geometry":[
               {"lat":55.7551,"lon":37.7050},{"lat":55.7551,"lon":37.7053},
               {"lat":55.7553,"lon":37.7053},{"lat":55.7553,"lon":37.7050},
               {"lat":55.7551,"lon":37.7050}]},
            {"type":"way","id":102,"tags":{"highway":"footway","name":"Walk"},
             "geometry":[{"lat":55.7561,"lon":37.7040},{"lat":55.7562,"lon":37.7042}]},
            {"type":"node","id":201,"lat":55.7560,"lon":37.7052,
             "tags":{"tourism":"hostel","name":"Test hostel",
                     "addr:street":"Лефортовская","addr:housenumber":"5"}},
            {"type":"node","id":202,"lat":55.7561,"lon":37.7052,
             "tags":{"amenity":"cafe","name":"Test cafe"}},
            {"type":"way","id":203,"tags":{"shop":"convenience","name":"Test shop"},
             "center":{"lat":55.7559,"lon":37.7062}},
            {"type":"node","id":204,"lat":55.7460,"lon":37.7052,
             "tags":{"amenity":"restaurant","name":"Outside"}},
            {"type":"node","id":205,"lat":55.7560,"lon":37.7052,
             "tags":{"building":"yes","name":"Named building"}},
            {"type":"node","id":202,"lat":55.7561,"lon":37.7052,
             "tags":{"amenity":"cafe","name":"Test cafe"}}
          ]
        }
    """.trimIndent()

    @Test fun parsesWaysAndAllPoiElementTypesWithoutDuplication() {
        val area = CampusAreaData.parseOverpass(osm)
        assertEquals(1, area.buildings.size)
        assertEquals("Корпус А", area.buildings.single().name)
        assertEquals(4, area.buildings.single().p.size)
        assertEquals(1, area.roads.size)
        assertEquals(4, area.pois.size)
        assertEquals(setOf("lodging", "food", "shop", "other"), area.pois.map { it.category }.toSet())
        assertEquals("Лефортовская, 5", area.pois.first { it.name == "Test hostel" }.address)
        assertFalse(area.pois.any { it.name == "Outside" })
        assertTrue(area.pois.any { it.name == "Named building" })
        assertEquals(listOf(CampusAreaData.WEST, CampusAreaData.SOUTH,
            CampusAreaData.EAST, CampusAreaData.NORTH), area.bbox)
    }

    @Test fun denseMarkersRemainAvailableAsZoomIncreases() {
        val world = Rect(-1000f, -1500f, 1000f, 1500f)
        val viewport = IntSize(1000, 1000)
        val points = listOf(
            CampusPoi("n/1", "Cafe", "food", x = 0, y = 0),
            CampusPoi("n/2", "Hostel", "lodging", x = 200, y = 0),
            CampusPoi("n/3", "Shop", "shop", x = 800, y = 0),
            CampusPoi("n/4", "Offscreen", "food", x = 250000, y = 0),
        )
        val initial = visibleCampusPois(
            points, 1f, Offset.Zero, viewport, world, spacingPx = 30f,
        )
        val zoomed = visibleCampusPois(
            points, 10f, Offset.Zero, viewport, world, spacingPx = 30f,
        )
        assertEquals(1, initial.size)
        assertTrue(initial.any { it.category == "food" })
        assertEquals(3, zoomed.size)
        assertFalse(zoomed.any { it.name == "Offscreen" })
    }

    @Test fun overviewUsesWholeAreaInsteadOfOldCampusScale() {
        val viewport = IntSize(400, 800)
        val world = Rect(-760f, -1160f, 760f, 1160f)
        val scale = campusBaseScale(world, viewport)
        assertTrue(world.width * scale <= viewport.width)
        assertTrue(world.height * scale <= viewport.height)
        assertTrue(scale > 0f)
    }
}
