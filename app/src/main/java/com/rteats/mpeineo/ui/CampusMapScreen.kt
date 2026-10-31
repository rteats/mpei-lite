package com.rteats.mpeineo.ui

import android.content.Intent
import android.os.SystemClock
import android.graphics.Bitmap
import java.lang.ref.SoftReference
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cached OpenStreetMap geometry, with the bundled campus as offline fallback.
 * The geometry and POIs share a local decimetre coordinate system.
 * Copyright OpenStreetMap contributors, ODbL 1.0.
 */
internal data class CampusGeometry(
    val bbox: List<Double> = emptyList(),
    val buildings: List<CampusFeature> = emptyList(),
    val roads: List<CampusFeature> = emptyList(),
    val land: List<CampusFeature> = emptyList(),
    val pois: List<CampusPoi> = emptyList(),
)

internal data class CampusPoi(
    val id: String = "",
    val name: String = "",
    val category: String = "other",
    val kind: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val address: String = "",
) {
    val center: Offset get() = Offset(x / 10f, y / 10f)
}

internal data class CampusFeature(
    val id: Long = 0L,
    val name: String = "",
    val ref: String = "",
    val kind: String = "",
    val p: List<List<Int>> = emptyList(),
)

/** Converts geometry (stored as decimeters relative to a common origin) to metres. */
private fun pointMeters(p: List<Int>) = Offset(p[0] / 10f, p[1] / 10f)

private fun footprintCenter(points: List<List<Int>>): Offset {
    if (points.size < 3) return points.firstOrNull()?.let(::pointMeters) ?: Offset.Zero
    var areaTwice = 0.0
    var xWeighted = 0.0
    var yWeighted = 0.0
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        val cross = a[0].toDouble() * b[1] - b[0].toDouble() * a[1]
        areaTwice += cross
        xWeighted += (a[0] + b[0]) * cross
        yWeighted += (a[1] + b[1]) * cross
    }
    if (abs(areaTwice) < 1.0) {
        val x = points.map { it[0] }.average()
        val y = points.map { it[1] }.average()
        return Offset((x / 10).toFloat(), (y / 10).toFloat())
    }
    return Offset((xWeighted / (3 * areaTwice) / 10).toFloat(),
        (yWeighted / (3 * areaTwice) / 10).toFloat())
}

private fun insideFootprint(point: Offset, polygon: List<List<Int>>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = pointMeters(polygon[i])
        val b = pointMeters(polygon[j])
        if ((a.y > point.y) != (b.y > point.y)) {
            val intersectX = (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
            if (point.x < intersectX) inside = !inside
        }
        j = i
    }
    return inside
}

private val CAMPUS_SYMBOLS = listOf(
    "А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К",
    "Л", "М", "Н", "Р", "С", "Т", "Ф", "Х", "Э",
)
/** Ordered letter keys, arranged into rows for different device widths. */
internal fun campusKeyboardRows(symbols: List<String>, columns: Int): List<List<String>> =
    symbols.distinct().chunked(columns.coerceAtLeast(1))

private val CAMPUS_ADDRESSES = mapOf(
    "А" to "Красноказарменная ул., 17",
    "Б" to "Красноказарменная ул., 17",
    "В" to "Красноказарменная ул., 17",
    "Г" to "Красноказарменная ул., 17",
    "Д" to "Красноказарменная ул., 17",
    "Е" to "Красноказарменная ул., 13Е",
    "Ж" to "Красноказарменная ул., 14",
    "З" to "Красноказарменная ул., 14",
    "И" to "Красноказарменная ул., 14",
    "К" to "Красноказарменная ул., 14",
    "Л" to "Красноказарменная ул., 14",
    "М" to "Красноказарменная ул., 13 (библиотека)",
    "Н" to "Красноказарменная ул., 13Н",
    "Р" to "Красноказарменная ул., 14, стр. 10",
    "С" to "Красноказарменная ул., 13С",
    "Т" to "Красноказарменная ул., 17Т",
    "Ф" to "Энергетический пр., 3, стр. 2",
    "Х" to "Красноказарменная ул., 17Х",
    "Э" to "Красноказарменная ул., 13",
)

private data class CampusPlace(
    val symbol: String,
    val address: String,
    val footprint: CampusFeature,
    val center: Offset,
) {
    val name get() = "Корпус $symbol"
}

/** OSM names replace inaccurate/mismatched hand-entered marker coordinates. */
private fun placesFromGeometry(data: CampusGeometry): List<CampusPlace> {
    val byName = data.buildings.associateBy { it.name.trim() }
    return CAMPUS_SYMBOLS.mapNotNull { symbol ->
        val footprint = byName["Корпус $symbol"]?.takeIf { it.p.size >= 3 }
            ?: return@mapNotNull null
        CampusPlace(symbol, CAMPUS_ADDRESSES[symbol].orEmpty(), footprint,
            footprintCenter(footprint.p))
    }
}

private fun screenPoint(
    point: Offset,
    scale: Float,
    pan: Offset,
    viewport: IntSize,
): Offset = Offset(
    viewport.width / 2f + pan.x + point.x * scale,
    viewport.height / 2f + pan.y + point.y * scale,
)

/**
 * Immutable paths in campus metres. Unlike constructing screen-space Paths
 * every frame, these are built once after loading the bundled JSON and reused
 * for every pan, zoom, and Material You redraw.
 */
private data class PreparedFeature(
    val source: CampusFeature,
    val path: Path,
    val bounds: Rect,
)

private data class PreparedCampus(
    val buildings: List<PreparedFeature>,
    val roads: List<PreparedFeature>,
    val land: List<PreparedFeature>,
    val bounds: Rect,
)

private fun prepareFeature(feature: CampusFeature, close: Boolean): PreparedFeature? {
    if (feature.p.size < if (close) 3 else 2) return null
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    val path = Path()
    feature.p.forEachIndexed { index, pair ->
        if (pair.size < 2) return null
        val point = pointMeters(pair)
        if (index == 0) path.moveTo(point.x, point.y)
        else path.lineTo(point.x, point.y)
        left = min(left, point.x)
        top = min(top, point.y)
        right = max(right, point.x)
        bottom = max(bottom, point.y)
    }
    if (close) path.close()
    return PreparedFeature(feature, path, Rect(left, top, right, bottom))
}

private fun prepareCampus(data: CampusGeometry): PreparedCampus {
    val buildings = data.buildings.mapNotNull { prepareFeature(it, true) }
    val roads = data.roads.mapNotNull { prepareFeature(it, false) }
    val land = data.land.mapNotNull { prepareFeature(it, true) }
    val features = buildings + roads + land
    val geometryBounds = Rect(
        features.minOfOrNull { it.bounds.left } ?: -250f,
        features.minOfOrNull { it.bounds.top } ?: -350f,
        features.maxOfOrNull { it.bounds.right } ?: 250f,
        features.maxOfOrNull { it.bounds.bottom } ?: 350f,
    )
    // Fit the exact requested rectangle, even if the OSM features do not
    // reach its edges. Avoid including geometry outside the requested area.
    val bounds = if (data.bbox.size == 4) {
        val west = ((data.bbox[0] - 37.70605) * 62600).toFloat()
        val north = ((55.7559 - data.bbox[3]) * 111200).toFloat()
        val east = ((data.bbox[2] - 37.70605) * 62600).toFloat()
        val south = ((55.7559 - data.bbox[1]) * 111200).toFloat()
        Rect(west, north, east, south)
    } else geometryBounds
    return PreparedCampus(buildings, roads, land, bounds)
}

private fun Rect.visibleWithin(view: Rect, margin: Float = 0f): Boolean =
    right + margin >= view.left && left - margin <= view.right &&
        bottom + margin >= view.top && top - margin <= view.bottom

/**
 * Don't let dragging expose a completely empty map. On an axis where the
 * geometry is smaller than the viewport, keep it centered instead.
 */
internal fun clampCampusPan(
    pan: Offset,
    scale: Float,
    viewport: IntSize,
    world: Rect,
    marginPx: Float = 0f,
): Offset {
    if (viewport.width <= 0 || viewport.height <= 0 || scale <= 0f) return pan
    fun axis(value: Float, minWorld: Float, maxWorld: Float, widthPx: Float): Float {
        val contentPx = (maxWorld - minWorld) * scale
        return if (contentPx <= widthPx - 2f * marginPx) {
            -(minWorld + maxWorld) / 2f * scale
        } else {
            value.coerceIn(
                widthPx / 2f - maxWorld * scale - marginPx,
                -widthPx / 2f - minWorld * scale + marginPx,
            )
        }
    }
    return Offset(
        axis(pan.x, world.left, world.right, viewport.width.toFloat()),
        axis(pan.y, world.top, world.bottom, viewport.height.toFloat()),
    )
}

/**
 * Keeps the world point below the fingers stationary when changing scale.
 * The pan delta is measured in screen pixels; constraints still apply at the edges.
 */
internal data class CampusViewportGesture(val zoom: Float, val pan: Offset)

/** Fit the full OSM rectangle to the display; zoom is a multiplier of this fit. */
internal fun campusBaseScale(world: Rect, viewport: IntSize): Float {
    if (viewport.width <= 0 || viewport.height <= 0) return 0f
    return min(
        viewport.width / (world.width.coerceAtLeast(1f) * 1.04f),
        viewport.height / (world.height.coerceAtLeast(1f) * 1.04f),
    )
}

private fun campusPoiPriority(category: String): Int = when (category) {
    "lodging", "food" -> 0
    "education", "health" -> 1
    "shop", "transport" -> 2
    "work", "leisure" -> 3
    else -> 4
}

/** Screen-space de-overlap keeps the dense city map usable without dropping POI data. */
internal fun visibleCampusPois(
    pois: List<CampusPoi>,
    zoom: Float,
    pan: Offset,
    viewport: IntSize,
    world: Rect,
    spacingPx: Float,
): List<CampusPoi> {
    if (viewport.width <= 0 || viewport.height <= 0) return emptyList()
    val scale = campusBaseScale(world, viewport) * zoom
    if (scale <= 0f) return emptyList()
    val spacing = spacingPx.coerceAtLeast(16f)
    val accepted = ArrayList<Pair<CampusPoi, Offset>>()
    for (poi in pois.sortedWith(compareBy<CampusPoi> { campusPoiPriority(it.category) }
        .thenBy { it.name })) {
        val position = screenPoint(poi.center, scale, pan, viewport)
        if (position.x < 0f || position.x > viewport.width ||
            position.y < 0f || position.y > viewport.height) continue
        // Checking distance also across cell boundaries prevents overlapping
        // circles that a single-cell spatial hash would permit.
        if (accepted.none {
                abs(it.second.x - position.x) < spacing &&
                    abs(it.second.y - position.y) < spacing
            }) accepted.add(poi to position)
    }
    return accepted.map { it.first }
}

internal fun applyCampusGesture(
    zoom: Float,
    pan: Offset,
    zoomChange: Float,
    panChange: Offset,
    centroid: Offset,
    viewport: IntSize,
    world: Rect,
): CampusViewportGesture {
    if (viewport.width <= 0 || viewport.height <= 0 || zoom <= 0f) {
        return CampusViewportGesture(zoom, pan)
    }
    val nextZoom = (zoom * zoomChange).coerceIn(.65f, 18f)
    val factor = nextZoom / zoom
    val screenCenter = Offset(viewport.width / 2f, viewport.height / 2f)
    val nextPan = pan + panChange + (centroid - screenCenter - pan) * (1f - factor)
    val scale = campusBaseScale(world, viewport) * nextZoom
    return CampusViewportGesture(nextZoom, clampCampusPan(nextPan, scale, viewport, world))
}

private fun roadMeters(kind: String): Float = when (kind) {
    "primary", "secondary" -> 13f
    "tertiary", "residential", "unclassified" -> 9f
    "service", "living_street" -> 5.5f
    "pedestrian" -> 5f
    "cycleway", "track" -> 3f
    else -> 2.2f
}

private fun isWalkingRoute(kind: String): Boolean =
    kind in setOf("footway", "path", "steps", "cycleway", "pedestrian", "track")

/** Prepared once per Monet palette, then drawn as one hardware-accelerated image. */
private data class CampusRaster(val bitmap: Bitmap, val bounds: Rect)

private data class MapPaintColors(
    val background: Int,
    val grass: Int,
    val forest: Int,
    val pitch: Int,
    val road: Int,
    val roadOutline: Int,
    val walkway: Int,
    val ordinaryBuilding: Int,
    val campusBuilding: Int,
    val buildingOutline: Int,
)

private fun renderCampusRaster(
    prepared: PreparedCampus,
    campusIds: Set<Long>,
    palette: MapPaintColors,
): CampusRaster {
    val bounds = prepared.bounds
    val w = bounds.width.coerceAtLeast(1f)
    val h = bounds.height.coerceAtLeast(1f)
    // High enough for detailed pinch zoom; no large scene graph redraw each frame.
    val resolution = min(2.9f, 2600f / max(w, h))
    val bitmap = Bitmap.createBitmap(
        max(1, (w * resolution).toInt()),
        max(1, (h * resolution).toInt()),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(palette.background)
    canvas.save()
    canvas.scale(resolution, resolution)
    canvas.translate(-bounds.left, -bounds.top)
    val fill = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    val outline = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
    }
    prepared.land.forEach { feature ->
        fill.color = when (feature.source.kind) {
            "forest", "wood", "scrub" -> palette.forest
            "pitch", "playground" -> palette.pitch
            else -> palette.grass
        }
        canvas.drawPath(feature.path.asAndroidPath(), fill)
    }
    prepared.roads.forEach { feature ->
        val walking = isWalkingRoute(feature.source.kind)
        val width = roadMeters(feature.source.kind)
        val path = feature.path.asAndroidPath()
        if (!walking) {
            outline.color = palette.roadOutline
            outline.strokeWidth = width + 1f
            outline.strokeCap = AndroidPaint.Cap.ROUND
            canvas.drawPath(path, outline)
        }
        outline.color = if (walking) palette.walkway else palette.road
        outline.strokeWidth = width
        outline.strokeCap = AndroidPaint.Cap.ROUND
        canvas.drawPath(path, outline)
    }
    prepared.buildings.forEach { feature ->
        fill.color = if (feature.source.id in campusIds) {
            palette.campusBuilding
        } else {
            palette.ordinaryBuilding
        }
        val path = feature.path.asAndroidPath()
        canvas.drawPath(path, fill)
        outline.color = palette.buildingOutline
        outline.strokeWidth = .9f
        outline.strokeCap = AndroidPaint.Cap.BUTT
        canvas.drawPath(path, outline)
    }
    canvas.restore()
    return CampusRaster(bitmap, bounds)
}

private data class PreparedCampusScene(
    val geometry: CampusGeometry,
    val prepared: PreparedCampus,
    val places: List<CampusPlace>,
)

/** A single soft raster entry; it can be reclaimed on memory pressure. */
internal class SingleEntrySoftCache<K : Any, V : Any> {
    private var key: K? = null
    private var value: SoftReference<V>? = null

    @Synchronized fun peek(request: K): V? =
        if (key == request) value?.get() else null

    @Synchronized fun getOrCreate(request: K, create: () -> V): Pair<V, Boolean> {
        peek(request)?.let { return it to true }
        val created = create()
        key = request
        value = SoftReference(created)
        return created to false
    }

    @Synchronized fun clear() {
        key = null
        value?.clear()
        value = null
    }
}

private object CampusRenderCache {
    @Volatile var scene: PreparedCampusScene? = null
        private set
    private val raster = SingleEntrySoftCache<Pair<PreparedCampus, MapPaintColors>, CampusRaster>()

    @Synchronized fun keepScene(value: PreparedCampusScene) {
        if (scene !== value) raster.clear()
        scene = value
    }
    fun peekRaster(scene: PreparedCampus, palette: MapPaintColors): CampusRaster? =
        raster.peek(scene to palette)
    fun raster(
        scene: PreparedCampus, palette: MapPaintColors, create: () -> CampusRaster,
    ): Pair<CampusRaster, Boolean> = raster.getOrCreate(scene to palette, create)
    fun clearRaster() = raster.clear()
}

/** Called from Application.onTrimMemory; geometry is small and remains reusable. */
internal fun clearCampusRasterOnLowMemory() = CampusRenderCache.clearRaster()

/** Map view is precomposed as a tab; unlike MapKit it is safe to render offscreen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CampusMapScreen(
    active: Boolean,
    focus: CampusMapFocus? = null,
    onFocusConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var mapLoadFailed by remember(context) { mutableStateOf(false) }
    var mapRefreshing by remember(context) { mutableStateOf(false) }
    var mapRetryRequest by remember { mutableIntStateOf(0) }
    var mapDownloadEvent by remember(context) { mutableStateOf<String?>(null) }
    val mapDiagnostics = remember(context) {
        (context.applicationContext as? com.rteats.mpeineo.MpeiNeoApplication)
            ?.container?.diagnostics
    }
    val scene by produceState<PreparedCampusScene?>(
        CampusRenderCache.scene, context, active, mapRetryRequest,
    ) {
        val started = SystemClock.elapsedRealtime()
        if (value == null) {
            val geometry = CampusAreaData.loadLocal(context)
            val result = withContext(Dispatchers.Default) {
                geometry?.takeIf { it.buildings.isNotEmpty() }?.let {
                    PreparedCampusScene(it, prepareCampus(it), placesFromGeometry(it))
                }
            }
            mapLoadFailed = result == null
            if (result != null) {
                CampusRenderCache.keepScene(result)
                value = result
            }
            (context.applicationContext as? com.rteats.mpeineo.MpeiNeoApplication)
                ?.container?.diagnostics?.log(
                    "PERF", "campus_prepare local durationMs=" +
                        (SystemClock.elapsedRealtime() - started) +
                        " buildings=" + (geometry?.buildings?.size ?: 0),
                )
        }
        // Only request the fixed area while the tab is visible. Once fetched,
        // the compact extract stays usable offline for 30 days and beyond.
        if (active && (mapRetryRequest > 0 || withContext(Dispatchers.IO) {
                CampusAreaData.needsRefresh(context)
            })) {
            mapRefreshing = true
            val refreshed = try {
                CampusAreaData.refresh(context) { event ->
                    mapDownloadEvent = event
                    mapDiagnostics?.log("MAP_NET", event)
                }
            } finally {
                mapRefreshing = false
            }
            val newScene = withContext(Dispatchers.Default) {
                refreshed?.let {
                    PreparedCampusScene(it, prepareCampus(it), placesFromGeometry(it))
                }
            }
            if (newScene != null) {
                CampusRenderCache.keepScene(newScene)
                value = newScene
            }
            (context.applicationContext as? com.rteats.mpeineo.MpeiNeoApplication)
                ?.container?.diagnostics?.log(
                    "MAP_UI", "area_refresh result=" +
                        (if (newScene != null) "success" else "offline_fallback") +
                        " poiCount=" + (refreshed?.pois?.size ?: 0) +
                        " error=" + (CampusAreaData.lastRefreshError ?: "none"),
                )
        }
    }

    val ready = scene
    if (ready == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (mapLoadFailed) {
                Text("Не удалось загрузить схему корпусов",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else ContainedLoadingIndicator()
        }
        return
    }

    val data = ready.geometry
    val prepared = ready.prepared
    val places = ready.places
    // OSM records the adjacent southern wing of building В separately as
    // "Главный учебный корпус". Both footprints belong to the same campus block.
    val buildingVSouth = remember(data) {
        data.buildings.firstOrNull { it.name == "Главный учебный корпус" }
    }
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val palette = MapPaintColors(
        background = colors.surface.toArgb(),
        grass = colors.secondaryContainer.copy(alpha = .43f).compositeOver(colors.surface).toArgb(),
        forest = colors.tertiaryContainer.copy(alpha = .58f).compositeOver(colors.surface).toArgb(),
        pitch = colors.secondaryContainer.copy(alpha = .72f).compositeOver(colors.surface).toArgb(),
        road = colors.surfaceContainerHigh.toArgb(),
        roadOutline = colors.outlineVariant.copy(alpha = .5f).compositeOver(colors.surface).toArgb(),
        walkway = colors.secondary.copy(alpha = .36f).compositeOver(colors.surface).toArgb(),
        ordinaryBuilding = colors.surfaceContainerHighest.toArgb(),
        campusBuilding = colors.primaryContainer.toArgb(),
        buildingOutline = colors.outlineVariant.toArgb(),
    )
    val campusIds = remember(places, buildingVSouth) {
        places.map { it.footprint.id }.toSet() +
            listOfNotNull(buildingVSouth?.id)
    }
    val raster by produceState<CampusRaster?>(
        CampusRenderCache.peekRaster(prepared, palette), prepared, campusIds, palette,
    ) {
        val started = SystemClock.elapsedRealtime()
        var cacheHit = value != null
        if (!cacheHit) {
            val obtained = withContext(Dispatchers.Default) {
                CampusRenderCache.raster(prepared, palette) {
                    renderCampusRaster(prepared, campusIds, palette)
                }
            }
            value = obtained.first
            cacheHit = obtained.second
        }
        (context.applicationContext as? com.rteats.mpeineo.MpeiNeoApplication)
            ?.container?.diagnostics?.log(
                "PERF", "campus_raster cache=${if (cacheHit) "hit" else "miss"}" +
                    " durationMs=${SystemClock.elapsedRealtime() - started}" +
                    " pixels=${value?.bitmap?.width ?: 0}x${value?.bitmap?.height ?: 0}",
            )
    }
    val flingScope = rememberCoroutineScope()
    val flingDecay = rememberSplineBasedDecay<Offset>()
    var flingJob by remember { mutableStateOf<Job?>(null) }
    fun stopFling() {
        flingJob?.cancel()
        flingJob = null
    }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var pan by rememberSaveable(
        stateSaver = Saver<Offset, ArrayList<Float>>(
            save = { arrayListOf(it.x, it.y) },
            restore = { Offset(it[0], it[1]) },
        ),
    ) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember { mutableStateOf<CampusPlace?>(null) }
    var selectedPoi by remember { mutableStateOf<CampusPoi?>(null) }
    var selectedRoom by remember { mutableStateOf<String?>(null) }
    var keyboardExpanded by rememberSaveable { mutableStateOf(false) }

    fun mapScale(): Float = campusBaseScale(prepared.bounds, viewport) * zoom
    fun fitPan(nextPan: Offset, nextZoom: Float): Offset {
        val scale = campusBaseScale(prepared.bounds, viewport) * nextZoom
        return clampCampusPan(nextPan, scale, viewport, prepared.bounds)
    }
    fun centeredPan(nextZoom: Float): Offset {
        val scale = campusBaseScale(prepared.bounds, viewport) * nextZoom
        return Offset(-prepared.bounds.center.x * scale, -prepared.bounds.center.y * scale)
    }
    fun changeZoom(factor: Float) {
        stopFling()
        zoom = (zoom * factor).coerceIn(.65f, 18f)
        pan = fitPan(pan, zoom)
    }
    fun project(p: Offset) = screenPoint(p, mapScale(), pan, viewport)
    val diagnostics = remember(context) {
        (context.applicationContext as? com.rteats.mpeineo.MpeiNeoApplication)
            ?.container?.diagnostics
    }
    fun focusBuilding(place: CampusPlace, room: String?, source: String) {
        stopFling()
        // Change the selected vector overlay immediately; no modal-dismiss or
        // bitmap regeneration is involved.
        selected = place
        selectedPoi = null
        selectedRoom = room
        keyboardExpanded = false
        if (viewport.width > 0 && viewport.height > 0) {
            val targetZoom = 11f
            val scale = campusBaseScale(prepared.bounds, viewport) * targetZoom
            zoom = targetZoom
            pan = fitPan(Offset(-place.center.x * scale, -place.center.y * scale), zoom)
        }
        diagnostics?.log(
            "MAP_UI", "select source=$source building=${place.symbol}" +
                " viewportReady=${viewport.width > 0 && viewport.height > 0}" +
                " zoom=$zoom",
        )
    }

    LaunchedEffect(prepared) {
        // A fresh city extract replaces the smaller bundled campus. Fit the
        // new rectangle once; never leave the user looking at its old crop.
        if (viewport.width > 0 && viewport.height > 0) {
            zoom = 1f
            pan = fitPan(centeredPan(zoom), zoom)
        }
    }

    val visibleMarkers = remember(data.pois, zoom, pan, viewport, density.density) {
        visibleCampusPois(data.pois, zoom, pan, viewport, prepared.bounds,
            spacingPx = if (zoom < 2f) 44f * density.density else 30f * density.density)
    }
    val poiLabelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
    }

    // This tab stays composed when hidden. It must not keep moving offscreen.
    LaunchedEffect(active) {
        if (!active) stopFling()
    }

    // Apply cross-tab focus only after the map is visible and measured.
    // Consume it once; later gestures and manual building selection remain free.
    LaunchedEffect(active, focus?.id, viewport, places) {
        val request = focus ?: return@LaunchedEffect
        if (!active || viewport.width <= 0 || viewport.height <= 0) return@LaunchedEffect
        val place = places.firstOrNull { it.symbol == request.building }
        if (place != null) {
            focusBuilding(place, request.classroom.takeIf { it.isNotBlank() }, "schedule")
        } else {
            diagnostics?.log("MAP_UI", "focus_unknown building=${request.building}")
        }
        onFocusConsumed()
    }

    val selectedPaths = remember(prepared, selected?.footprint?.id, buildingVSouth?.id) {
        val chosen = selected ?: return@remember emptyList<Path>()
        prepared.buildings.filter {
            it.source.id == chosen.footprint.id ||
                (chosen.symbol == "В" && it.source.id == buildingVSouth?.id)
        }.map { it.path }
    }

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
    }

    Box(modifier.fillMaxSize().clipToBounds()
        .testTag(if (active) "campus-map-screen" else "campus-map-precomposed")) {
        Canvas(
            modifier = Modifier.fillMaxSize()
                .onSizeChanged {
                    stopFling()
                    val initialSize = viewport == IntSize.Zero
                    viewport = it
                    pan = fitPan(if (initialSize) centeredPan(zoom) else pan, zoom)
                }
                // Handle single taps as soon as the finger is released.
                // A double-tap recognizer previously held every tap for the
                // second-tap timeout before updating the building highlight.
                // Pinch and the dedicated zoom controls remain available.
                .pointerInput(prepared, places, visibleMarkers) {
                    detectTapGestures(
                        onTap = mapTap@{ tap ->
                            val started = SystemClock.elapsedRealtime()
                            val scale = mapScale().takeIf { it > 0 } ?: return@mapTap
                            // Visible POIs receive taps before building footprints.
                            val poiHit = visibleMarkers.minByOrNull {
                                val p = project(it.center)
                                hypot((tap.x - p.x).toDouble(), (tap.y - p.y).toDouble())
                            }?.takeIf {
                                val p = project(it.center)
                                hypot((tap.x - p.x).toDouble(), (tap.y - p.y).toDouble()) <=
                                    20.dp.toPx()
                            }
                            if (poiHit != null) {
                                selectedPoi = poiHit
                                selected = null
                                selectedRoom = null
                                keyboardExpanded = false
                                return@mapTap
                            }
                            selectedPoi = null
                            val point = Offset(
                                (tap.x - viewport.width / 2f - pan.x) / scale,
                                (tap.y - viewport.height / 2f - pan.y) / scale,
                            )
                            val polygonHit = places.asReversed().firstOrNull {
                                insideFootprint(point, it.footprint.p)
                            } ?: if (buildingVSouth != null &&
                                insideFootprint(point, buildingVSouth.p)) {
                                places.firstOrNull { it.symbol == "В" }
                            } else null
                            val nearest = places.minByOrNull {
                                hypot((point.x - it.center.x).toDouble(),
                                    (point.y - it.center.y).toDouble())
                            }
                            val threshold = 23.dp.toPx() / scale
                            val nearby = nearest?.takeIf {
                                hypot((point.x - it.center.x).toDouble(),
                                    (point.y - it.center.y).toDouble()) <= threshold
                            }
                            selected = polygonHit ?: nearby
                            selectedRoom = null
                            if (selected != null) keyboardExpanded = false
                            diagnostics?.log(
                                "MAP_UI", "select source=tap building=${selected?.symbol ?: "none"}" +
                                    " hit=${if (polygonHit != null) "polygon" else if (nearby != null) "nearby" else "none"}" +
                                    " resolveMs=${SystemClock.elapsedRealtime() - started}",
                            )
                        },
                    )
                }
                // Keep pinch zoom anchored to the fingers. Only one-finger
                // drags carry momentum after release; a new touch interrupts it.
                .pointerInput(prepared.bounds, viewport) {
                    detectCampusGestures(
                        onDown = { stopFling() },
                        onTransform = { centroid, panDelta, zoomDelta ->
                            val next = applyCampusGesture(
                                zoom, pan, zoomDelta, panDelta, centroid, viewport, prepared.bounds,
                            )
                            zoom = next.zoom
                            pan = next.pan
                        },
                        onFling = { velocity ->
                            val startPan = pan
                            val startZoom = zoom
                            flingJob = flingScope.launch {
                                val movement = Animatable(Offset.Zero, Offset.VectorConverter)
                                movement.animateDecay(velocity, flingDecay) {
                                    pan = fitPan(startPan + value, startZoom)
                                }
                            }
                        },
                    )
                },
        ) {
            if (viewport.width == 0 || viewport.height == 0) return@Canvas
            val scale = mapScale()
            drawRect(colors.surface)

            val origin = Offset(
                viewport.width / 2f + pan.x,
                viewport.height / 2f + pan.y,
            )
            // All static OSM polygons are flattened into one palette-aware
            // bitmap. Pan/zoom only transforms that texture on the GPU.
            raster?.let { image ->
                val bounds = image.bounds
                val destination = RectF(
                    origin.x + bounds.left * scale,
                    origin.y + bounds.top * scale,
                    origin.x + bounds.right * scale,
                    origin.y + bounds.bottom * scale,
                )
                val canvas = drawContext.canvas.nativeCanvas
                val save = canvas.save()
                canvas.clipRect(0f, 0f, size.width, size.height)
                canvas.drawBitmap(
                    image.bitmap,
                    null,
                    destination,
                    AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG),
                )
                canvas.restoreToCount(save)
            }

            // Cached vector geometry changes only when selection changes.
            if (selectedPaths.isNotEmpty()) {
                clipRect {
                    withTransform({
                        translate(origin.x, origin.y)
                        scale(scale, scale, pivot = Offset.Zero)
                    }) {
                        selectedPaths.forEach { path ->
                            drawPath(path, colors.primary)
                            drawPath(path, colors.primary,
                                style = Stroke(width = 2.dp.toPx() / scale))
                        }
                    }
                }
            }

            // Compact Monet pins remain a fixed screen size at any map zoom.
            // Markers are filtered in screen space, but no POI is deleted from
            // the underlying cached dataset.
            poiLabelPaint.textSize = 10.sp.toPx()
            val poiCanvas = drawContext.canvas.nativeCanvas
            val poiSave = poiCanvas.save()
            poiCanvas.clipRect(0f, 0f, size.width, size.height)
            visibleMarkers.forEach { poi ->
                val center = project(poi.center)
                val marker = when (poi.category) {
                    "lodging" -> colors.tertiaryContainer
                    "food" -> colors.primaryContainer
                    "shop" -> colors.secondaryContainer
                    "education" -> colors.primaryContainer
                    "health" -> colors.tertiaryContainer
                    "transport" -> colors.secondaryContainer
                    "work" -> colors.surfaceContainerHighest
                    "leisure" -> colors.secondaryContainer
                    else -> colors.surfaceContainerHigh
                }
                val foreground = when (poi.category) {
                    "lodging", "health" -> colors.onTertiaryContainer
                    "food", "education" -> colors.onPrimaryContainer
                    "shop", "transport", "leisure" -> colors.onSecondaryContainer
                    else -> colors.onSurface
                }
                drawCircle(marker, radius = 12.dp.toPx(), center = center)
                drawCircle(colors.outlineVariant, radius = 12.dp.toPx(), center = center,
                    style = Stroke(width = 1.dp.toPx()))
                poiLabelPaint.color = foreground.toArgb()
                val glyph = when (poi.category) {
                    "lodging" -> "Н"
                    "food" -> "Е"
                    "shop" -> "М"
                    "education" -> "У"
                    "health" -> "+"
                    "transport" -> "Т"
                    "work" -> "О"
                    "leisure" -> "П"
                    else -> "•"
                }
                val font = poiLabelPaint.fontMetrics
                poiCanvas.drawText(glyph, center.x,
                    center.y - (font.ascent + font.descent) / 2f, poiLabelPaint)
            }
            poiCanvas.restoreToCount(poiSave)

            // Readable, individually selectable building labels, derived from
            // named OSM footprints instead of approximate external pins.
            labelPaint.textSize = 12.sp.toPx()
            val metrics = labelPaint.fontMetrics
            val labelsCanvas = drawContext.canvas.nativeCanvas
            val labelsSave = labelsCanvas.save()
            labelsCanvas.clipRect(0f, 0f, size.width, size.height)
            places.forEach { place ->
                val point = project(place.center)
                if (point.x < -14f || point.x > size.width + 14f ||
                    point.y < -14f || point.y > size.height + 14f) return@forEach
                labelPaint.color = if (selected?.symbol == place.symbol) {
                    colors.onPrimary.toArgb()
                } else {
                    colors.onPrimaryContainer.toArgb()
                }
                labelsCanvas.drawText(
                    place.symbol,
                    point.x, point.y - (metrics.ascent + metrics.descent) / 2f,
                    labelPaint,
                )
            }
            labelsCanvas.restoreToCount(labelsSave)
        }

        // Tell users when only the bundled campus fallback is available.
        if (active && !CampusAreaData.coversLefortovo(data)) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .padding(start = 12.dp, end = 96.dp, top = 12.dp)
                    .testTag("lefortovo-map-status"),
                shape = MaterialTheme.shapes.medium,
                color = colors.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (mapRefreshing) "Загрузка карты Лефортово…"
                            else "Доступна только карта кампуса",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        val detail = if (mapRefreshing) mapDownloadEvent
                            else CampusAreaData.refreshError(context)
                        if (!detail.isNullOrBlank()) {
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (mapRefreshing) colors.onSurfaceVariant
                                    else colors.error,
                                maxLines = 4,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("lefortovo-map-error"),
                            )
                        }
                    }
                    if (!mapRefreshing) {
                        TextButton(onClick = {
                            mapDiagnostics?.log("MAP_UI", "manual_retry")
                            mapRetryRequest++
                        }) { Text("Повторить") }
                    }
                }
            }
        }

        // The bottom dock is the only search. Keep direct alphabet building
        // shortcuts behind a small, discoverable button instead of a second
        // full-width search-like header at the top of the map.
        BoxWithConstraints(
            modifier = Modifier.align(Alignment.TopEnd)
                .fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag("campus-building-keyboard"),
        ) {
            val columns = ((maxWidth.value - 16f + 4f) / 48f).toInt().coerceIn(1, 10)
            val rows = remember(places, columns) {
                campusKeyboardRows(places.map { it.symbol }, columns)
            }
            val bySymbol = remember(places) { places.associateBy { it.symbol } }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = {
                        stopFling()
                        keyboardExpanded = !keyboardExpanded
                    },
                    modifier = Modifier.width(76.dp).height(48.dp)
                        .testTag("campus-keyboard-toggle"),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceContainerHigh,
                    shadowElevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("А–Я", style = MaterialTheme.typography.labelLarge)
                        AppIcon(
                            if (keyboardExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                            contentDescription = if (keyboardExpanded)
                                "Скрыть буквы корпусов" else "Показать буквы корпусов",
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = keyboardExpanded,
                    enter = androidx.compose.animation.expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    ) + androidx.compose.animation.fadeIn(
                        // Temporary spatial-motion trial: use the same family as panel height.
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    ),
                    exit = androidx.compose.animation.shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    ) + androidx.compose.animation.fadeOut(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    ),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = colors.surfaceContainerHigh,
                        shadowElevation = 3.dp,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            rows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    val missing = columns - row.size
                                    repeat(missing / 2) {
                                        Spacer(Modifier.weight(1f).height(44.dp))
                                    }
                                    row.forEach { symbol ->
                                        val place = bySymbol.getValue(symbol)
                                        val isSelected = selected?.symbol == symbol
                                        Surface(
                                            onClick = { focusBuilding(place, null, "keyboard") },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                                .testTag("campus-key-$symbol")
                                                .semantics { contentDescription = "Корпус $symbol" },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) colors.primaryContainer
                                                else colors.surfaceContainerLow,
                                            contentColor = if (isSelected) colors.onPrimaryContainer
                                                else colors.onSurface,
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    symbol,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold
                                                        else FontWeight.Medium,
                                                )
                                            }
                                        }
                                    }
                                    repeat(missing - missing / 2) {
                                        Spacer(Modifier.weight(1f).height(44.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Native M3 Expressive floating toolbar uses its own container, spacing
        // and 48dp touch targets. Vector symbols share the same 24dp optical box.
        VerticalFloatingToolbar(
            expanded = true,
            modifier = Modifier.align(Alignment.CenterEnd)
                .padding(end = FloatingToolbarDefaults.ScreenOffset)
                .testTag("campus-zoom-toolbar"),
        ) {
            IconButton(
                onClick = { changeZoom(1.25f) },
                modifier = Modifier.testTag("campus-zoom-in"),
            ) {
                AppIcon(AppIcons.ZoomIn, contentDescription = "Приблизить")
            }
            IconButton(
                onClick = { changeZoom(1f / 1.25f) },
                modifier = Modifier.testTag("campus-zoom-out"),
            ) {
                AppIcon(AppIcons.ZoomOut, contentDescription = "Отдалить")
            }
            IconButton(
                onClick = {
                    stopFling()
                    zoom = 1f
                    pan = fitPan(centeredPan(zoom), zoom)
                },
                modifier = Modifier.testTag("campus-zoom-reset"),
            ) {
                AppIcon(AppIcons.Refresh, contentDescription = "Показать весь кампус")
            }
        }

        val selectedTitle = selectedPoi?.name ?: selected?.name
        selectedTitle?.let { title ->
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 92.dp),
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerHigh,
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp,
                        bottom = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        selectedRoom?.let { room ->
                            Text(room, style = MaterialTheme.typography.labelLarge,
                                color = colors.primary)
                        }
                        selectedPoi?.let { poi ->
                            Text(when (poi.category) {
                                "lodging" -> "Гостиница / хостел"
                                "food" -> "Кафе / питание"
                                "shop" -> "Магазин"
                                "education" -> "Образование"
                                "health" -> "Медицина"
                                "transport" -> "Транспорт"
                                "work" -> "Организация / услуги"
                                "leisure" -> "Досуг / достопримечательность"
                                else -> "Объект на карте"
                            }, style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant)
                        }
                        val address = selectedPoi?.address ?: selected?.address.orEmpty()
                        if (address.isNotBlank()) {
                            Text(address, style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = {
                        selected = null
                        selectedPoi = null
                        selectedRoom = null
                    }) { Text("Скрыть") }
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 82.dp)
                .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.openstreetmap.org/copyright")))
                },
            color = colors.surfaceContainerLow.copy(alpha = .92f),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                "© OpenStreetMap",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }

}
