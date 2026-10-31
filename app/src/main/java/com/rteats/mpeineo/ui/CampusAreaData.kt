package com.rteats.mpeineo.ui

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private const val FILE_NAME = "lefortovo_osm_v2.json"
    private const val MAX_RESPONSE_BYTES = 32_000_000
    private const val REFRESH_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000
    private const val FAILED_RETRY_DELAY_MS = 15L * 60 * 1000
    private const val PREFS_NAME = "lefortovo_map_download"
    private const val KEY_RETRY_AFTER = "retry_after"
    private const val KEY_LAST_ERROR = "last_error"
    // A live internet connection is not proof that a single Overpass instance works.
    // A bounded attempt is preferable to an indefinitely pending map download.
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    internal data class DownloadState(
        val loading: Boolean = false,
        val detail: String? = null,
        val error: String? = null,
        val completed: Int = 0,
    )

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _downloadState = MutableStateFlow(DownloadState())
    internal val downloadState: StateFlow<DownloadState> = _downloadState
    private var downloadJob: Job? = null

    /**
     * One application-lifetime download. Switching navigation screens does not
     * cancel an in-flight HTTP request or start duplicate downloads.
     */
    internal fun requestRefresh(
        context: Context,
        force: Boolean = false,
        log: (String) -> Unit = {},
    ) {
        val appContext = context.applicationContext
        synchronized(this) {
            if (downloadJob?.isActive == true) {
                log("download_reused_in_flight")
                return
            }
            downloadJob = downloadScope.launch {
                try {
                    if (!force && !needsRefresh(appContext)) {
                        log("download_skipped_cache_or_backoff")
                        return@launch
                    }
                    _downloadState.value = _downloadState.value.copy(
                        loading = true, detail = "Preparing request", error = null,
                    )
                    val result = refresh(appContext) { event ->
                        log(event)
                        _downloadState.value = _downloadState.value.copy(detail = event)
                    }
                    _downloadState.value = _downloadState.value.copy(
                        loading = false,
                        detail = if (result != null) "Map saved" else "All servers failed",
                        error = if (result == null) refreshError(appContext) else null,
                        completed = _downloadState.value.completed + if (result != null) 1 else 0,
                    )
                    log(if (result != null) "download_completed" else "download_exhausted")
                } catch (error: CancellationException) {
                    _downloadState.value = _downloadState.value.copy(
                        loading = false, detail = "Download cancelled",
                    )
                    log("download_cancelled type=${error.javaClass.simpleName}")
                    throw error
                } catch (error: Exception) {
                    val reason = failureSummary(error)
                    lastRefreshError = reason
                    _downloadState.value = _downloadState.value.copy(
                        loading = false, detail = "Download failed", error = reason,
                    )
                    log("download_unexpected_error reason=$reason")
                }
            }
        }
    }

    @Volatile internal var lastRefreshError: String? = null
        private set

    internal fun coversLefortovo(geometry: CampusGeometry): Boolean =
        geometry.bbox.size == 4 &&
            geometry.bbox[0] <= WEST + 0.00001 &&
            geometry.bbox[1] <= SOUTH + 0.00001 &&
            geometry.bbox[2] >= EAST - 0.00001 &&
            geometry.bbox[3] >= NORTH - 0.00001


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
        [out:json][timeout:35];
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
        } ?: if (tags.tag("building") in setOf("dormitory", "hotel", "hostel") ||
            (tags.tag("name").isNotBlank() && tags.tag("building").let {
                it.isNotBlank() && it != "no"
            })
        ) "building" else return null
        val kind = tags.tag(tag)
        val group = when {
            tag == "building" && kind in setOf("dormitory", "hotel", "hostel") -> "lodging"
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
        "dormitory" to "Общежитие",
        "guest_house" to "Гостевой дом", "cafe" to "Кафе",
        "restaurant" to "Ресторан", "fast_food" to "Быстрое питание",
        "pharmacy" to "Аптека", "school" to "Школа",
        "university" to "Университет", "library" to "Библиотека",
        "parking" to "Парковка", "bus_stop" to "Остановка",
        "tram_stop" to "Трамвайная остановка", "toilets" to "Туалет",
        "atm" to "Банкомат", "bank" to "Банк", "post_office" to "Почта",
        "bench" to "Скамейка", "playground" to "Детская площадка",
    )

    private fun tagIsCampusBuilding(name: String, category: String): Boolean =
        category == "other" && Regex("^Корпус [А-Я]$").matches(name)

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
                        .map { tags.tag(it) }.firstOrNull { it.isNotBlank() }.orEmpty()
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
            // The geometry output has no "center" field. Use the centroid
            // of its coordinates for named buildings without a POI tag.
            val lat = (center ?: objectValue).get("lat")?.asDouble
                ?: coords?.takeIf { it.size() > 0 }?.map {
                    it.asJsonObject.get("lat").asDouble
                }?.average() ?: continue
            val lon = (center ?: objectValue).get("lon")?.asDouble
                ?: coords?.takeIf { it.size() > 0 }?.map {
                    it.asJsonObject.get("lon").asDouble
                }?.average() ?: continue
            if (lat !in SOUTH..NORTH || lon !in WEST..EAST) continue
            val centerPoint = point(lat, lon)
            val name = listOf("name:ru", "name", "brand", "operator")
                .asSequence().map { tags.tag(it) }.firstOrNull { it.isNotBlank() }
                ?: fallbackLabels[kind] ?: kind.replace('_', ' ')
            val key = "$type/$id"
            // The existing alphabet labels already identify MPEI buildings.
            if (tagIsCampusBuilding(name, group)) continue
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
                ?.let {
                    runCatching {
                        it.bufferedReader().use { reader ->
                            Gson().fromJson(reader, CampusGeometry::class.java)
                        }
                    }.getOrNull()
                }
            cached?.takeIf { it.buildings.isNotEmpty() && coversLefortovo(it) }
                ?: runCatching {
                    context.assets.open("campus_osm.json").bufferedReader().use { reader ->
                        Gson().fromJson(reader, CampusGeometry::class.java)
                    }
                }.getOrNull()
        }

    internal fun refreshError(context: Context): String? = lastRefreshError
        ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_ERROR, null)

    internal fun needsRefresh(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() < prefs.getLong(KEY_RETRY_AFTER, 0L)) return false
        val cached = file(context)
        if (!cached.isFile) return true
        if (System.currentTimeMillis() - cached.lastModified() >= REFRESH_INTERVAL_MS) return true
        // Do not trust an older campus-only extract as a full Lefortovo map.
        return runCatching {
            cached.bufferedReader().use { reader ->
                val data = Gson().fromJson(reader, CampusGeometry::class.java)
                !coversLefortovo(data) || data.buildings.isEmpty()
            }
        }.getOrDefault(true)
    }

    /** Each endpoint has an OkHttp hard deadline, including response and upload. */
    internal suspend fun refresh(
        context: Context,
        onEvent: (String) -> Unit = {},
    ): CampusGeometry? = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        lastRefreshError = null
        onEvent("download_start endpoints=${ENDPOINTS.size}")
        for (endpoint in ENDPOINTS) {
            val host = URL(endpoint).host
            val started = android.os.SystemClock.elapsedRealtime()
            onEvent("connecting host=$host maxSeconds=45")
            val attempt = runCatching {
                val body = FormBody.Builder().add("data", query).build()
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "MPEILiteAndroid (OSM area cache)")
                    .post(body)
                    .build()
                onEvent("request_sent host=$host")
                http.newCall(request).execute().use { response ->
                    onEvent("response host=$host http=${response.code}")
                    if (!response.isSuccessful) {
                        val excerpt = response.body?.charStream()?.use { reader ->
                            val chars = CharArray(160)
                            val count = reader.read(chars)
                            if (count > 0) String(chars, 0, count)
                                .replace(Regex("\\s+"), " ").trim() else ""
                        }.orEmpty()
                        throw IOException("HTTP ${response.code} ${excerpt.take(120)}".trim())
                    }
                    val bytes = response.body?.byteStream()?.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_RESPONSE_BYTES) {
                                throw IOException("Map response too large (32 MB limit)")
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: throw IOException("Empty HTTP response body")
                    onEvent("received host=$host bytes=${bytes.size}")
                    val result = parseOverpass(String(bytes, Charsets.UTF_8))
                    if (!coversLefortovo(result) || result.buildings.size < 100 ||
                        result.roads.size < 30 || result.pois.size < 30
                    ) throw IOException(
                        "Incomplete map: buildings=${result.buildings.size}" +
                            " roads=${result.roads.size} pois=${result.pois.size}",
                    )
                    val cached = file(context)
                    val tmp = File(cached.parentFile, cached.name + ".tmp")
                    tmp.writeText(Gson().toJson(result))
                    if (!tmp.renameTo(cached)) {
                        tmp.copyTo(cached, overwrite = true)
                        tmp.delete()
                    }
                    onEvent("saved host=$host buildings=${result.buildings.size}" +
                        " roads=${result.roads.size} pois=${result.pois.size}" +
                        " elapsedMs=${android.os.SystemClock.elapsedRealtime() - started}")
                    result
                }
            }
            if (attempt.isSuccess) {
                lastRefreshError = null
                prefs.edit().remove(KEY_LAST_ERROR).remove(KEY_RETRY_AFTER).apply()
                return@withContext attempt.getOrThrow()
            }
            val exception = attempt.exceptionOrNull()!!
            if (exception is CancellationException) throw exception
            val reason = failureSummary(exception)
            failures += "$host: $reason"
            onEvent("failed host=$host reason=$reason" +
                " elapsedMs=${android.os.SystemClock.elapsedRealtime() - started}")
        }
        val summary = failures.joinToString(" | ").take(1000)
        lastRefreshError = summary
        prefs.edit().putString(KEY_LAST_ERROR, summary)
            .putLong(KEY_RETRY_AFTER, System.currentTimeMillis() + FAILED_RETRY_DELAY_MS)
            .apply()
        onEvent("download_failed servers=${failures.size} retryAfterMinutes=15")
        null
    }

    internal fun failureSummary(error: Throwable): String {
        val message = error.message.orEmpty().replace(Regex("\\s+"), " ").trim()
        return "${error.javaClass.simpleName}: ${message.take(180)}"
    }

}
