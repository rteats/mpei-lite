package com.rteats.mpeineo.ui

import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

private fun shapePath(
    points: List<List<Int>>,
    scale: Float,
    pan: Offset,
    viewport: IntSize,
    close: Boolean,
): Path = Path().apply {
    points.forEachIndexed { index, coordinates ->
        val p = screenPoint(pointMeters(coordinates), scale, pan, viewport)
        if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    if (close) close()
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

/** Map view is precomposed as a tab; unlike MapKit it is safe to render offscreen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampusMapScreen(active: Boolean, modifier: Modifier = Modifier) {
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

    val places = remember(data) { placesFromGeometry(data) }
    val placesByOsmId = remember(places) { places.associateBy { it.footprint.id } }
    val colors = MaterialTheme.colorScheme
    var zoom by rememberSaveable { mutableFloatStateOf(1.45f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember { mutableStateOf<CampusPlace?>(null) }
    var showList by remember { mutableStateOf(false) }

    // The fixed 600m wide / 850m tall viewport gives a portrait-friendly
    // default overview without enormous empty margins above and below.
    fun mapScale(): Float = max(viewport.width / 600f, viewport.height / 850f) * zoom
    fun project(p: Offset) = screenPoint(p, mapScale(), pan, viewport)

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
    }

    Box(modifier.fillMaxSize().testTag(if (active) "campus-map-screen" else "campus-map-precomposed")) {
        Canvas(
            modifier = Modifier.fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(data, places) {
                    detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                        val nextZoom = (zoom * gestureZoom).coerceIn(.65f, 5f)
                        val ratio = nextZoom / zoom
                        val cursor = Offset(
                            centroid.x - size.width / 2f,
                            centroid.y - size.height / 2f,
                        )
                        pan = (pan - cursor) * ratio + cursor + gesturePan
                        zoom = nextZoom
                    }
                }
                .pointerInput(data, places) {
                    detectTapGestures { tap ->
                        val scale = mapScale().takeIf { it > 0 } ?: return@detectTapGestures
                        val point = Offset(
                            (tap.x - viewport.width / 2f - pan.x) / scale,
                            (tap.y - viewport.height / 2f - pan.y) / scale,
                        )
                        val polygonHit = places.asReversed().firstOrNull {
                            insideFootprint(point, it.footprint.p)
                        }
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
                    }
                },
        ) {
            if (viewport.width == 0 || viewport.height == 0) return@Canvas
            val scale = mapScale()
            drawRect(colors.surface)

            data.land.forEach { land ->
                if (land.p.size < 3) return@forEach
                val color = when (land.kind) {
                    "forest", "wood", "scrub" -> colors.tertiaryContainer.copy(alpha = .58f)
                    "pitch", "playground" -> colors.secondaryContainer.copy(alpha = .72f)
                    else -> colors.secondaryContainer.copy(alpha = .43f)
                }
                drawPath(shapePath(land.p, scale, pan, viewport, true), color)
            }

            // Every street/path centerline comes from OSM, not illustration.
            data.roads.forEach { road ->
                if (road.p.size < 2) return@forEach
                val path = shapePath(road.p, scale, pan, viewport, false)
                val walking = isWalkingRoute(road.kind)
                val width = (roadMeters(road.kind) * scale).coerceAtLeast(1.5.dp.toPx())
                if (!walking) {
                    drawPath(path, colors.outlineVariant.copy(alpha = .5f),
                        style = Stroke(width = width + 1.dp.toPx(), cap = StrokeCap.Round))
                }
                drawPath(
                    path,
                    if (walking) colors.secondary.copy(alpha = .36f)
                    else colors.surfaceContainerHigh,
                    style = Stroke(width = width, cap = StrokeCap.Round),
                )
            }

            data.buildings.forEach { building ->
                if (building.p.size < 3) return@forEach
                val place = placesByOsmId[building.id]
                val selectedHere = selected?.footprint?.id == building.id
                val fill = when {
                    selectedHere -> colors.primary
                    place != null -> colors.primaryContainer
                    else -> colors.surfaceContainerHighest
                }
                val path = shapePath(building.p, scale, pan, viewport, true)
                drawPath(path, fill)
                drawPath(
                    path,
                    if (selectedHere) colors.primary else colors.outlineVariant,
                    style = Stroke(width = (if (selectedHere) 2f else .9f).dp.toPx()),
                )
            }

            // Readable, individually selectable building labels, derived from
            // named OSM footprints instead of approximate external pins.
            labelPaint.textSize = 12.sp.toPx()
            val metrics = labelPaint.fontMetrics
            places.forEach { place ->
                val point = project(place.center)
                if (point.x < -24f || point.x > size.width + 24f ||
                    point.y < -24f || point.y > size.height + 24f) return@forEach
                labelPaint.color = if (selected?.symbol == place.symbol) {
                    colors.onPrimary.toArgb()
                } else {
                    colors.onPrimaryContainer.toArgb()
                }
                drawContext.canvas.nativeCanvas.drawText(
                    place.symbol,
                    point.x, point.y - (metrics.ascent + metrics.descent) / 2f,
                    labelPaint,
                )
            }
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
                IconButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(5f) }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(.65f) }) {
                    Text("−", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = {
                    zoom = 1.45f
                    pan = Offset.Zero
                }) {
                    AppIcon(AppIcons.Refresh, contentDescription = "Показать весь кампус")
                }
            }
        }

        selected?.let { place ->
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 30.dp),
                onClick = { selected = null },
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerHigh,
                shadowElevation = 3.dp,
            ) {
                Column(Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium)
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
                            zoom = 2.2f
                            val scale = mapScale()
                            pan = Offset(-place.center.x * scale, -place.center.y * scale)
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
