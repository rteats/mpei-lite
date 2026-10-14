package com.rteats.mpeineo.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline OpenStreetMap geometry, extracted once into assets/campus_osm.json.
 * Copyright OpenStreetMap contributors, ODbL 1.0.
 * The app never contacts an OSM/map endpoint or uses a mapping SDK.
 */
private data class CampusGeometry(
    val buildings: List<CampusFeature> = emptyList(),
    val roads: List<CampusFeature> = emptyList(),
    val land: List<CampusFeature> = emptyList(),
)

private data class CampusFeature(
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
    val bounds = Rect(
        features.minOfOrNull { it.bounds.left } ?: -250f,
        features.minOfOrNull { it.bounds.top } ?: -350f,
        features.maxOfOrNull { it.bounds.right } ?: 250f,
        features.maxOfOrNull { it.bounds.bottom } ?: 350f,
    )
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

/** Map view is precomposed as a tab; unlike MapKit it is safe to render offscreen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampusMapScreen(
    active: Boolean,
    focus: CampusMapFocus? = null,
    onFocusConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val data = remember(context) {
        runCatching {
            context.assets.open("campus_osm.json").bufferedReader().use { reader ->
                Gson().fromJson(reader, CampusGeometry::class.java)
            }
        }.getOrNull()
    }

    if (data == null || data.buildings.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Не удалось загрузить схему корпусов",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val prepared = remember(data) { prepareCampus(data) }
    val places = remember(data) { placesFromGeometry(data) }
    // OSM records the adjacent southern wing of building В separately as
    // "Главный учебный корпус". Both footprints belong to the same campus block.
    val buildingVSouth = remember(data) {
        data.buildings.firstOrNull { it.name == "Главный учебный корпус" }
    }
    val colors = MaterialTheme.colorScheme
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
    val raster by produceState<CampusRaster?>(null, prepared, campusIds, palette) {
        value = withContext(Dispatchers.Default) {
            renderCampusRaster(prepared, campusIds, palette)
        }
    }
    var zoom by rememberSaveable { mutableFloatStateOf(1.45f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember { mutableStateOf<CampusPlace?>(null) }
    var selectedRoom by remember { mutableStateOf<String?>(null) }
    var showList by remember { mutableStateOf(false) }

    // The fixed 600m wide / 850m tall viewport gives a portrait-friendly
    // default overview without enormous empty margins above and below.
    fun mapScale(): Float = max(viewport.width / 600f, viewport.height / 850f) * zoom
    fun fitPan(nextPan: Offset, nextZoom: Float): Offset {
        val scale = max(viewport.width / 600f, viewport.height / 850f) * nextZoom
        return clampCampusPan(nextPan, scale, viewport, prepared.bounds)
    }
    fun changeZoom(factor: Float) {
        zoom = (zoom * factor).coerceIn(.65f, 5f)
        pan = fitPan(pan, zoom)
    }
    fun project(p: Offset) = screenPoint(p, mapScale(), pan, viewport)

    // Apply cross-tab focus only after the map is visible and measured.
    // Consume it once; later gestures and manual building selection remain free.
    LaunchedEffect(active, focus?.id, viewport, places) {
        val request = focus ?: return@LaunchedEffect
        if (!active || viewport.width <= 0 || viewport.height <= 0) return@LaunchedEffect
        val place = places.firstOrNull { it.symbol == request.building }
        if (place != null) {
            val targetZoom = 2.2f
            val scale = max(viewport.width / 600f, viewport.height / 850f) * targetZoom
            zoom = targetZoom
            pan = fitPan(
                Offset(-place.center.x * scale, -place.center.y * scale),
                targetZoom,
            )
            selected = place
            selectedRoom = request.classroom.takeIf { it.isNotBlank() }
        }
        onFocusConsumed()
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
                    viewport = it
                    pan = fitPan(pan, zoom)
                }
                .pointerInput(prepared) {
                    detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                        val nextZoom = (zoom * gestureZoom).coerceIn(.65f, 5f)
                        val ratio = nextZoom / zoom
                        val cursor = Offset(
                            centroid.x - size.width / 2f,
                            centroid.y - size.height / 2f,
                        )
                        val nextPan = (pan - cursor) * ratio + cursor + gesturePan
                        zoom = nextZoom
                        pan = fitPan(nextPan, nextZoom)
                    }
                }
                .pointerInput(prepared, places) {
                    detectTapGestures { tap ->
                        val scale = mapScale().takeIf { it > 0 } ?: return@detectTapGestures
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
                    }
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

            // Selection stays sharp and costs only one vector path redraw.
            selected?.let { place ->
                val selectedPaths = prepared.buildings.filter {
                    it.source.id == place.footprint.id ||
                        (place.symbol == "В" && it.source.id == buildingVSouth?.id)
                }.map { it.path }
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
            }

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

        FilledTonalButton(
            onClick = { showList = true },
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        ) { Text("Корпуса") }

        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
            shadowElevation = 2.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { changeZoom(1.25f) }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = { changeZoom(1f / 1.25f) }) {
                    Text("−", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = {
                    zoom = 1.45f
                    pan = fitPan(Offset.Zero, zoom)
                }) {
                    AppIcon(AppIcons.Refresh, contentDescription = "Показать весь кампус")
                }
            }
        }

        selected?.let { place ->
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 30.dp),
                onClick = {
                    selected = null
                    selectedRoom = null
                },
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerHigh,
                shadowElevation = 3.dp,
            ) {
                Column(Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium)
                    selectedRoom?.let { room ->
                        Text(room, style = MaterialTheme.typography.labelLarge,
                            color = colors.primary)
                    }
                    Text(place.address, style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant)
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 6.dp)
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

    if (showList) {
        ModalBottomSheet(onDismissRequest = { showList = false }) {
            LazyColumn(
                Modifier.fillMaxWidth().testTag("campus-building-list"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(places, key = { it.symbol }) { place ->
                    Surface(
                        onClick = {
                            selected = place
                            selectedRoom = null
                            zoom = 2.2f
                            val scale = mapScale()
                            pan = fitPan(
                                Offset(-place.center.x * scale, -place.center.y * scale),
                                zoom,
                            )
                            showList = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = colors.surfaceContainerLow,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(place.symbol,
                                color = colors.primary,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.widthIn(min = 24.dp))
                            Column {
                                Text(place.name, style = MaterialTheme.typography.bodyLarge)
                                Text(place.address, color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
