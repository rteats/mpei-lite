package com.rteats.mpeineo.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.min

/**
 * Standalone, offline campus sketch. Building marker coordinates: MpeiX (MIT).
 * Footprints and footways are deliberately simplified and NOT surveyed geometry.
 * https://github.com/tonykolomeytsev/mpeiapp/tree/master/statics/map
 */
internal data class CampusBuilding(
    val symbol: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val widthMeters: Float,
    val heightMeters: Float,
) {
    val name: String get() = "Корпус " + symbol
}

internal val campusBuildings = listOf(
    CampusBuilding("А", "Красноказарменная ул., 17", 55.755898, 37.707607, 52f, 26f),
    CampusBuilding("Б", "Красноказарменная ул., 17", 55.755272, 37.707478, 35f, 44f),
    CampusBuilding("В", "Красноказарменная ул., 17", 55.755260, 37.708523, 65f, 37f),
    CampusBuilding("Г", "Красноказарменная ул., 17", 55.754757, 37.709007, 33f, 37f),
    CampusBuilding("Д", "Красноказарменная ул., 17", 55.755035, 37.709950, 47f, 27f),
    CampusBuilding("Е", "Красноказарменная ул., 13, стр. 1", 55.756657, 37.703217, 32f, 40f),
    CampusBuilding("Ж", "Красноказарменная ул., 14", 55.754436, 37.707382, 42f, 36f),
    CampusBuilding("З", "Красноказарменная ул., 14", 55.754360, 37.706696, 38f, 40f),
    CampusBuilding("И", "Красноказарменная ул., 14", 55.753860, 37.708202, 48f, 33f),
    CampusBuilding("К", "Красноказарменная ул., 14", 55.753716, 37.709470, 42f, 34f),
    CampusBuilding("Л", "Красноказарменная ул., 14", 55.753380, 37.709710, 30f, 30f),
    CampusBuilding("М", "Красноказарменная ул., 13 (библиотека)", 55.756165, 37.704384, 55f, 45f),
    CampusBuilding("Н", "Красноказарменная ул., 13, стр. 6", 55.757244, 37.703747, 45f, 35f),
    CampusBuilding("Р", "Красноказарменная ул., 14, стр. 10", 55.753757, 37.706810, 40f, 32f),
    CampusBuilding("С", "Красноказарменная ул., 13С", 55.757496, 37.702530, 63f, 40f),
    CampusBuilding("Т", "Красноказарменная ул., 17, стр. 4", 55.756386, 37.709010, 48f, 44f),
    CampusBuilding("Ф", "Энергетический пр., 3, стр. 2", 55.757786, 37.705600, 55f, 35f),
    CampusBuilding("Х", "Красноказарменная ул., 17, стр. 2", 55.755997, 37.708282, 30f, 23f),
    CampusBuilding("Э", "Красноказарменная ул., 13", 55.756287, 37.703957, 34f, 30f),
)

private data class GeoPoint(val latitude: Double, val longitude: Double)

/** A local metric approximation is sufficient for this small, fixed campus. */
private fun metersAt(latitude: Double, longitude: Double): Offset = Offset(
    x = ((longitude - 37.7063) * 62_600.0).toFloat(),
    y = ((55.7560 - latitude) * 111_200.0).toFloat(),
)

private val pathways = listOf(
    listOf(GeoPoint(55.7577, 37.7019), GeoPoint(55.7568, 37.7042),
        GeoPoint(55.7560, 37.7067), GeoPoint(55.7548, 37.7099)),
    listOf(GeoPoint(55.7579, 37.7055), GeoPoint(55.7565, 37.7066),
        GeoPoint(55.7550, 37.7076), GeoPoint(55.7537, 37.7078)),
    listOf(GeoPoint(55.7562, 37.7031), GeoPoint(55.7560, 37.7050),
        GeoPoint(55.7560, 37.7076), GeoPoint(55.7564, 37.7095)),
)

private fun buildingPath(building: CampusBuilding, locate: (Offset) -> Offset): Path {
    val center = metersAt(building.latitude, building.longitude)
    val w = building.widthMeters / 2f
    val h = building.heightMeters / 2f
    // Gently varied, simplified footprint shapes; not actual building footprints.
    val points = if (building.symbol in setOf("М", "А", "В", "Т", "С")) {
        listOf(
            Offset(-w, -h), Offset(w, -h), Offset(w, h * 0.35f),
            Offset(w * 0.45f, h * 0.35f), Offset(w * 0.45f, h),
            Offset(-w, h),
        )
    } else {
        listOf(Offset(-w, -h), Offset(w, -h), Offset(w, h), Offset(-w, h))
    }
    return Path().apply {
        points.forEachIndexed { index, delta ->
            val p = locate(center + delta)
            if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
}

/**
 * Compose-only renderer: no Android WebView, native map SDK, API key, or network.
 * Single-finger drag and two-finger zoom work on the map; navbar switches tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampusMapScreen(active: Boolean, modifier: Modifier = Modifier) {
    var zoom by rememberSaveable { mutableFloatStateOf(1.18f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember { mutableStateOf<CampusBuilding?>(null) }
    var showList by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val labelPaint = remember(colors.onPrimaryContainer) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            color = android.graphics.Color.WHITE
        }
    }

    // Reserve room for buildings around the site; initial fit focuses on the campus.
    fun mapScale(): Float = min(viewport.width / 830f, viewport.height / 850f) * zoom
    fun project(p: Offset): Offset {
        val scale = mapScale()
        return Offset(viewport.width / 2f + p.x * scale + pan.x,
            viewport.height / 2f + p.y * scale + pan.y)
    }

    Box(modifier.fillMaxSize().testTag("campus-map-screen")) {
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(0.75f, 5f)
                        val ratio = newZoom / zoom
                        val fromCenter = Offset(centroid.x - size.width / 2f,
                            centroid.y - size.height / 2f)
                        pan = (pan - fromCenter) * ratio + fromCenter + gesturePan
                        zoom = newZoom
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { tap ->
                        val nearest = campusBuildings.minByOrNull { building ->
                            val point = project(metersAt(building.latitude, building.longitude))
                            hypot((tap.x - point.x).toDouble(),
                                (tap.y - point.y).toDouble())
                        }
                        val hit = nearest?.let {
                            val p = project(metersAt(it.latitude, it.longitude))
                            hypot((tap.x - p.x).toDouble(),
                                (tap.y - p.y).toDouble()) <= 28.dp.toPx()
                        } ?: false
                        selected = nearest.takeIf { hit }
                    })
                },
        ) {
            drawRect(colors.surface)

            // Deliberately schematic landscaping; unrelated buildings are omitted.
            val patches = listOf(
                listOf(Offset(-290f, -240f), Offset(-125f, -235f),
                    Offset(-80f, -120f), Offset(-275f, -110f)),
                listOf(Offset(30f, -215f), Offset(230f, -215f),
                    Offset(260f, -75f), Offset(45f, -80f)),
                listOf(Offset(-285f, 195f), Offset(-155f, 195f),
                    Offset(-150f, 260f), Offset(-290f, 255f)),
            )
            patches.forEach { patch ->
                val path = Path().apply {
                    patch.forEachIndexed { i, p ->
                        val s = project(p)
                        if (i == 0) moveTo(s.x, s.y) else lineTo(s.x, s.y)
                    }
                    close()
                }
                drawPath(path, colors.secondaryContainer.copy(alpha = .55f))
            }

            // Broad campus-adjacent roads and smaller schematic pedestrian paths.
            val road1 = listOf(
                GeoPoint(55.75310, 37.7014), GeoPoint(55.75295, 37.7050),
                GeoPoint(55.75260, 37.7115))
            val road2 = listOf(
                GeoPoint(55.75910, 37.7032), GeoPoint(55.7586, 37.7060),
                GeoPoint(55.7583, 37.7105))
            fun drawTrack(points: List<GeoPoint>, color: Color, width: Float) {
                points.zipWithNext().forEach { (a, b) ->
                    drawLine(color, project(metersAt(a.latitude, a.longitude)),
                        project(metersAt(b.latitude, b.longitude)),
                        strokeWidth = width, cap = StrokeCap.Round)
                }
            }
            listOf(road1, road2).forEach {
                drawTrack(it, colors.surfaceContainerHighest, 26.dp.toPx() * zoom)
                drawTrack(it, colors.surfaceContainerLow, 18.dp.toPx() * zoom)
            }
            pathways.forEach {
                drawTrack(it, colors.surfaceContainerHigh,
                    6.dp.toPx() * zoom)
            }

            campusBuildings.forEach { building ->
                val point = project(metersAt(building.latitude, building.longitude))
                val chosen = selected?.symbol == building.symbol
                drawPath(
                    path = buildingPath(building, ::project),
                    color = if (chosen) colors.primary else colors.surfaceContainerHighest,
                )
                drawPath(
                    path = buildingPath(building, ::project),
                    color = if (chosen) colors.primary else colors.outlineVariant,
                    style = Stroke(width = if (chosen) 2.5.dp.toPx() else 1.dp.toPx()),
                )
                val markerRadius = 12.dp.toPx()
                drawCircle(if (chosen) colors.primary else colors.primaryContainer,
                    radius = markerRadius, center = point)
                drawCircle(colors.outlineVariant, radius = markerRadius,
                    center = point, style = Stroke(width = 1.dp.toPx()))
                labelPaint.color = if (chosen) colors.onPrimary.toArgb()
                    else colors.onPrimaryContainer.toArgb()
                labelPaint.textSize = 12.sp.toPx()
                val metrics = labelPaint.fontMetrics
                drawContext.canvas.nativeCanvas.drawText(building.symbol,
                    point.x, point.y - (metrics.ascent + metrics.descent) / 2f,
                    labelPaint)
            }
        }

        FilledTonalButton(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            onClick = { showList = true },
        ) {
            Text("Корпуса")
        }

        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            color = colors.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 2.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { zoom = (zoom * 1.3f).coerceAtMost(5f) }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = { zoom = (zoom / 1.3f).coerceAtLeast(.75f) }) {
                    Text("−", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = { zoom = 1.18f; pan = Offset.Zero }) {
                    AppIcon(AppIcons.Refresh, contentDescription = "Вернуть обзор карты")
                }
            }
        }

        selected?.let { building ->
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerHigh,
                shadowElevation = 3.dp,
                onClick = { selected = null },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(building.name, style = MaterialTheme.typography.titleMedium)
                    Text(building.address, style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant)
                }
            }
        }
    }

    if (showList) {
        ModalBottomSheet(onDismissRequest = { showList = false }) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().testTag("campus-building-list"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(campusBuildings, key = { it.symbol }) { building ->
                    Surface(
                        onClick = {
                            selected = building
                            val p = metersAt(building.latitude, building.longitude)
                            zoom = 2.1f
                            pan = Offset(-p.x * mapScale(), -p.y * mapScale())
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
                            Text(building.symbol, style = MaterialTheme.typography.titleMedium,
                                color = colors.primary,
                                modifier = Modifier.widthIn(min = 24.dp))
                            Column {
                                Text(building.name, style = MaterialTheme.typography.bodyLarge)
                                Text(building.address, style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
