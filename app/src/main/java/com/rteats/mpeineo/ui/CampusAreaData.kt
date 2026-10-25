package com.rteats.mpeineo.ui

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A fixed-size OpenStreetMap extract. Store the converted geometry, not the large
 * Overpass response. The bundled campus remains available without internet.
 *
 * OSM contributors / ODbL 1.0: https://www.openstreetmap.org/copyright
 */
internal object CampusAreaData {
    internal const val NORTH = 55.767230
    internal const val SOUTH = 55.746344
    internal const val WEST = 37.696083
    internal const val EAST = 37.720361

    private const val ORIGIN_LAT = 55.7559
    private const val ORIGIN_LON = 37.70605
    private const val FILE_NAME = "lefortovo_osm_v1.json"
    private const val MAX_RESPONSE_BYTES = 32_000_000
    private const val REFRESH_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    // Filter ordinary residential addresses out of the POI index, but preserve
    // amenity, shop, tourism, office and other mapped public/organizational tags.
    private val POI_TAGS = listOf(
        "amenity", "shop", "tourism", "office", "craft", "healthcare",
        "historic", "public_transport", "railway", "leisure", "man_made",
    )
    private val RAILWAY_POIS = setOf("station", "halt", "subway_entrance", "tram_stop")
    private val LEISURE_POIS = setOf(
        "fitness_centre", "sports_centre", "stadium", "swimming_pool",
        "playground", "park", "garden", "fitness_station",
    )
    private val MAN_MADE_POIS = setOf("tower", "water_tower", "chimney", "works")
    private val ROAD_TYPES = setOf(
        "primary", "secondary", "tertiary", "residential", "unclassified",
        "living_street", "service", "pedestrian", "footway", "path",
        "steps", "cycleway", "track", "platform",
    )
    private val LAND_TYPES = setOf(
        "grass", "meadow", "forest", "recreation_ground", "village_green",
        "cemetery",
    )
    private val LAND_LEISURE = setOf("park", "garden", "pitch", "playground")
    private val LAND_NATURAL = setOf("wood", "grassland", "scrub")

    // Two outputs prevent large POI geometry (including relations) from being
    // downloaded. Only base-map ways need their vertices.
    private val query = """
        [out:json][timeout:90];
        (
          way["building"]($SOUTH,$WEST,$NORTH,$EAST);
          way["highway"]($SOUTH,$WEST,$NORTH,$EAST);
          way["landuse"~"^(grass|meadow|forest|recreation_ground|village_green|cemetery)$"]($SOUTH,$WEST,$NORTH,$EAST);
          way["leisure"~"^(park|garden|pitch|playground)$"]($SOUTH,$WEST,$NORTH,$EAST);
          way["natural"~"^(wood|grassland|scrub)$"]($SOUTH,$WEST,$NORTH,$EAST);
        );
        out geom($SOUTH,$WEST,$NORTH,$EAST);
        (
          nwr["amenity"]($SOUTH,$WEST,$NORTH,$EAST);
          nwr["shop"]($SOUTH,$WEST,$NORTH,$EAST);
          nwr["tourism"]($SOUTH,$WEST,$NORTH,$EAST);
          nwr["office"]($SOUTH,$WEST,$NORTH,$EAST);
          nwr["craft"]($SOUTH,$WEST,$NORTH,$EAST);
          nwr["healthcare"]($SOUTH,$WEST,$NORTH,$EAST);
          nwr["historic"]($SOUTH,$WEST,$NORTH,$EAST);
          nwr["public_transport"]($SOUTH,$WEST,$NORTH,$EAST);
          nwr["railway"~"^(station|halt|subway_entrance|tram_stop)$"]($SOUTH,$WEST,$NORTH,$EAST);
          nwr["leisure"~"^(fitness_centre|sports_centre|stadium|swimming_pool|playground|park|garden|fitness_station)$"]($SOUTH,$WEST,$NORTH,$EAST);
          nwr["man_made"~"^(tower|water_tower|chimney|works)$"]($SOUTH,$WEST,$NORTH,$EAST);
        );
        out center;
    """.trimIndent()

    private fun point(lat: Double, lon: Double): List<Int> = listOf(
        ((lon - ORIGIN_LON) * 626000).roundToInt(),
        ((ORIGIN_LAT - lat) * 1112000).roundToInt(),
    )

    private fun JsonObject.tag(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun category(tags: JsonObject): Pair<String, String>? {
        val tag = POI_TAGS.firstOrNull { key ->
            val value = tags.tag(key)
            value.isNotBlank() && value != "no" && when (key) {
                "railway" -> value in RAILWAY_POIS
                "leisure" -> value in LEISURE_POIS
                "man_made" -> value in MAN_MADE_POIS
                else -> true
            }
        } ?: return null
        val kind = tags.tag(tag)
        val group = when {
            tag == "tourism" && kind in setOf(
                "hostel", "hotel", "guest_house", "motel", "apartment", "camp_site",
            ) -> "lodging"
            tag == "amenity" && kind in setOf(
                "cafe", "restaurant", "fast_food", "food_court", "bar", "pub",
                "ice_cream", "biergarten",
            ) -> "food"
            tag == "shop" -> "shop"
            tag == "amenity" && kind in setOf(
                "university", "college", "school", "kindergarten", "library",
            ) -> "education"
            tag == "amenity" && kind in setOf(
                "hospital", "clinic", "doctors", "dentist", "pharmacy",
            ) || tag == "healthcare" -> "health"
            tag == "public_transport" || tag == "railway" ||
                (tag == "amenity" && kind in setOf(
                    "bus_station", "parking", "bicycle_parking",
                )) -> "transport"
            tag == "office" || tag == "craft" -> "work"
            tag == "leisure" || tag == "historic" || tag == "tourism" -> "leisure"
            else -> "other"
        }
        return group to kind
    }

    private val fallbackLabels = mapOf(
        "hostel" to "Хостел", "hotel" to "Гостиница",
        "guest_house" to "Гостевой дом", "cafe" to "Кафе",
        "restaurant" to "Ресторан", "fast_food" to "Быстрое питание",
        "pharmacy" to "Аптека", "school" to "Школа",
        "university" to "Университет", "library" to "Библиотека",
        "parking" to "Парковка", "bus_stop" to "Остановка",
        "tram_stop" to "Трамвайная остановка", "toilets" to "Туалет",
        "atm" to "Банкомат", "bank" to "Банк", "post_office" to "Почта",
        "bench" to "Скамейка", "playground" to "Детская площадка",
    )

    private fun address(tags: JsonObject): String {
        val street = tags.tag("addr:street")
        val number = tags.tag("addr:housenumber")
        val address = listOf(street, number).filter { it.isNotBlank() }.joinToString(", ")
        return address.ifBlank { tags.tag("addr:full") }
    }

    /** Parse both geometry ways and POI nodes/ways/relations in one OSM response. */
    internal fun parseOverpass(json: String): CampusGeometry {
        val root = JsonParser.parseString(json).asJsonObject
        val buildings = ArrayList<CampusFeature>()
        val roads = ArrayList<CampusFeature>()
        val land = ArrayList<CampusFeature>()
        val pois = LinkedHashMap<String, CampusPoi>()

        for (element in root.getAsJsonArray("elements")) {
            val objectValue = element.asJsonObject
            val tags = objectValue.getAsJsonObject("tags") ?: JsonObject()
            val type = objectValue.tag("type")
            val id = objectValue.get("id")?.asLong ?: continue
            val coords = objectValue.getAsJsonArray("geometry")
            if (type == "way" && coords != null) {
                val original = coords.mapNotNull { raw ->
                    val coordinate = raw.asJsonObject
                    val lat = coordinate.get("lat")?.asDouble ?: return@mapNotNull null
                    val lon = coordinate.get("lon")?.asDouble ?: return@mapNotNull null
                    point(lat, lon)
                }
                val vertices = original.filterIndexed { index, p ->
                    index == 0 || p != original[index - 1]
                }
                if (vertices.size >= 2) {
                    val closed = vertices.first() == vertices.last()
                    val path = if (closed) vertices.dropLast(1) else vertices
                    val feature = CampusFeature(
                        id = id, name = tags.tag("name"), ref = tags.tag("ref"),
                        kind = tags.tag("highway"), p = path,
                    )
                    val landKind = listOf("landuse", "leisure", "natural")
                        .map(tags::tag).firstOrNull { it.isNotBlank() }.orEmpty()
                    when {
                        closed && path.size >= 3 && tags.tag("building").let {
                            it.isNotEmpty() && it != "no"
                        } -> buildings += feature
                        closed && path.size >= 3 && (
                            tags.tag("landuse") in LAND_TYPES ||
                                tags.tag("leisure") in LAND_LEISURE ||
                                tags.tag("natural") in LAND_NATURAL
                            ) -> land += feature.copy(kind = landKind)
                        tags.tag("highway") in ROAD_TYPES -> roads += feature
                    }
                }
            }

            val (group, kind) = category(tags) ?: continue
            val center = objectValue.getAsJsonObject("center")
            val lat = (center ?: objectValue).get("lat")?.asDouble ?: continue
            val lon = (center ?: objectValue).get("lon")?.asDouble ?: continue
            if (lat !in SOUTH..NORTH || lon !in WEST..EAST) continue
            val centerPoint = point(lat, lon)
            val name = listOf("name:ru", "name", "brand", "operator")
                .asSequence().map(tags::tag).firstOrNull { it.isNotBlank() }
                ?: fallbackLabels[kind] ?: kind.replace('_', ' ')
            val key = "$type/$id"
            pois[key] = CampusPoi(
                id = key,
                name = name,
                category = group,
                kind = kind,
                x = centerPoint[0],
                y = centerPoint[1],
                address = address(tags),
            )
        }
        return CampusGeometry(
            bbox = listOf(WEST, SOUTH, EAST, NORTH),
            buildings = buildings,
            roads = roads,
            land = land,
            pois = pois.values.toList(),
        )
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    internal suspend fun loadLocal(context: Context): CampusGeometry? =
        withContext(Dispatchers.IO) {
            val cached = file(context).takeIf { it.isFile }
                ?.let { runCatching {
                    it.bufferedReader().use { reader ->
                        Gson().fromJson(reader, CampusGeometry::class.java)
                    }
                }.getOrNull() }
            cached?.takeIf { it.buildings.isNotEmpty() }
                ?: runCatching {
                    context.assets.open("campus_osm.json").bufferedReader().use { reader ->
                        Gson().fromJson(reader, CampusGeometry::class.java)
                    }
                }.getOrNull()
        }

    internal fun needsRefresh(context: Context): Boolean {
        val cached = file(context)
        return !cached.exists() ||
            System.currentTimeMillis() - cached.lastModified() >= REFRESH_INTERVAL_MS
    }

    internal suspend fun refresh(context: Context): CampusGeometry? =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("User-Agent", "MPEILiteAndroid/0.5 (OSM area cache)")
                    connection.setRequestProperty("Accept-Encoding", "gzip")
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 95_000
                    connection.doOutput = true
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                        it.write("data=" + URLEncoder.encode(query, "UTF-8"))
                    }
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw IOException("Overpass HTTP " + connection.responseCode)
                    }
                    val stream = connection.inputStream.let {
                        if (connection.contentEncoding.equals("gzip", ignoreCase = true))
                            GZIPInputStream(it) else it
                    }
                    val payload = stream.use {
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = it.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_RESPONSE_BYTES) {
                                throw IOException("Map response too large")
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                    val result = parseOverpass(payload)
                    if (result.buildings.size < 100 || result.roads.size < 30 ||
                        result.pois.size < 30
                    ) throw IOException("Incomplete map extract")
                    val cached = file(context)
                    val tmp = File(cached.parentFile, cached.name + ".tmp")
                    tmp.writeText(Gson().toJson(result))
                    if (!tmp.renameTo(cached)) {
                        tmp.copyTo(cached, overwrite = true)
                        tmp.delete()
                    }
                    result
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
}
