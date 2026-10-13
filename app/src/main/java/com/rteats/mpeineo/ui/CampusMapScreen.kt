package com.rteats.mpeineo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.LayoutInflater
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonParser
import com.rteats.mpeineo.BuildConfig
import com.rteats.mpeineo.MpeiNeoApplication
import com.rteats.mpeineo.R
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Verified MPEI address-level positions. Grouped A-D and Ж-Л are each one
 * building/address, not fictitious positions for individual wings.
 *
 * MPEI directory: https://www.mpei.ru/AboutUniverse/Contact/Pages/Sheme.aspx
 * Yandex Maps address entries provide the corresponding lat/lon.
 */
internal data class CampusBuilding(
    val symbol: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

internal val campusBuildings = listOf(
    CampusBuilding("А–Д", "Главный учебный корпус · А, Б, В, Г, Д",
        "Красноказарменная ул., 17", 55.754877, 37.708185),
    CampusBuilding("Ж–Л", "Административный корпус · Ж, З, И, К, Л",
        "Красноказарменная ул., 14", 55.753788, 37.708257),
    CampusBuilding("Е", "Корпус Е",
        "Красноказарменная ул., 13, стр. 1", 55.756726, 37.703173),
    CampusBuilding("М", "Корпус М · библиотека",
        "Красноказарменная ул., 13, стр. 3", 55.756057, 37.704448),
    CampusBuilding("Н", "Корпус Н",
        "Красноказарменная ул., 13, стр. 6", 55.757142, 37.703972),
)

private enum class MapAccess { IDLE, CHECKING, ALLOWED, DISABLED, NO_KEY }

/**
 * Account-wide quota cannot be observed by the mobile SDK. The repository's
 * public config is an operator kill switch, NOT a fake local request counter.
 * Reject on any unavailable/bad response, before initializing MapKit.
 */
private suspend fun mapAccess(): MapAccess = withContext(Dispatchers.IO) {
    if (BuildConfig.YANDEX_MAPKIT_API_KEY.isBlank()) return@withContext MapAccess.NO_KEY
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/rteats/mpei-neo/main/config/mapkit.json")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext MapAccess.DISABLED
            val json = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            if (json.get("enabled")?.asBoolean == true) MapAccess.ALLOWED
            else MapAccess.DISABLED
        }
    } catch (_: Exception) {
        MapAccess.DISABLED
    }
}

@Composable
internal fun CampusMapScreen(active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val diagnostics = (context.applicationContext as MpeiNeoApplication).container.diagnostics
    var access by remember { mutableStateOf(MapAccess.IDLE) }

    LaunchedEffect(active) {
        if (!active) {
            access = MapAccess.IDLE
        } else {
            access = MapAccess.CHECKING
            access = mapAccess()
            diagnostics.log("MAP", "admission=" + access.name)
        }
    }

    Box(modifier.testTag("campus-map-screen")) {
        if (active && access == MapAccess.ALLOWED) {
            LiveCampusMap(
                modifier = Modifier.fillMaxSize(),
                onFailure = { diagnostics.log("MAP", "MapKit error=" + it) },
            )
        } else {
            OfflineCampusBuildings(
                message = when (access) {
                    MapAccess.IDLE, MapAccess.CHECKING -> "Проверяем доступность карты…"
                    MapAccess.NO_KEY -> "Карта пока не настроена. Адреса корпусов доступны."
                    MapAccess.DISABLED -> "Онлайн-карта временно недоступна. Адреса корпусов доступны."
                    MapAccess.ALLOWED -> ""
                },
                loading = access == MapAccess.CHECKING,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun OfflineCampusBuildings(
    message: String,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("campus-map-fallback"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(campusBuildings, key = { it.symbol }) { building ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(building.name, style = MaterialTheme.typography.titleMedium)
                    Text(building.address, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** MapKit listeners are weakly held by the SDK; retain them in the session. */
private class CampusMapSession(
    val view: MapView,
    val tapListeners: List<MapObjectTapListener>,
)

/** No SDK startup at Application launch or during off-screen pager precomposition. */
private object CampusMapKit {
    private var initialized = false

    @Synchronized fun initialize(context: android.content.Context) {
        if (initialized) return
        MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY)
        MapKitFactory.initialize(context.applicationContext)
        initialized = true
    }
}

@Composable
private fun LiveCampusMap(
    modifier: Modifier = Modifier,
    onFailure: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimary = MaterialTheme.colorScheme.onPrimary.toArgb()
    var selected by remember { mutableStateOf<CampusBuilding?>(null) }

    val result = remember(context, primary, onPrimary) {
        runCatching {
            CampusMapKit.initialize(context)
            // TextureView mode avoids a SurfaceView covering Compose navigation.
            val view = LayoutInflater.from(context)
                .inflate(R.layout.mapkit_view, null, false) as MapView
            val map = view.mapWindow.map
            map.move(CameraPosition(Point(55.7553, 37.7060), 15.8f, 0f, 0f))
            val listeners = campusBuildings.map { building ->
                val listener = MapObjectTapListener { _, _ ->
                    selected = building
                    true
                }
                map.mapObjects.addPlacemark().apply {
                    geometry = Point(building.latitude, building.longitude)
                    setIcon(ImageProvider.fromBitmap(
                        mapBadge(building.symbol, primary, onPrimary)))
                    addTapListener(listener)
                }
                listener
            }
            CampusMapSession(view, listeners)
        }
    }
    val session = result.getOrNull()
    if (session == null) {
        LaunchedEffect(result.isFailure) {
            onFailure(result.exceptionOrNull()?.javaClass?.simpleName ?: "Unknown")
        }
        OfflineCampusBuildings(
            message = "Не удалось открыть карту. Адреса корпусов доступны.",
            loading = false,
            modifier = modifier,
        )
        return
    }

    DisposableEffect(session, lifecycleOwner) {
        var running = false
        fun start() {
            if (!running) {
                MapKitFactory.getInstance().onStart()
                session.view.onStart()
                running = true
            }
        }
        fun stop() {
            if (running) {
                session.view.onStop()
                MapKitFactory.getInstance().onStop()
                running = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
            session.view.destroy()
        }
    }

    Box(modifier) {
        AndroidView(factory = { session.view }, modifier = Modifier.fillMaxSize())
        selected?.let { building ->
            // Keep SDK attribution and "Open in Maps" controls uncovered below.
            Surface(
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .clickable { selected = null },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(building.name, style = MaterialTheme.typography.titleMedium)
                    Text(building.address, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun mapBadge(text: String, background: Int, foreground: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = background
    canvas.drawRoundRect(RectF(6f, 6f, 114f, 114f), 38f, 38f, paint)
    paint.color = foreground
    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true
    paint.textSize = if (text.length > 1) 35f else 53f
    val metrics = paint.fontMetrics
    canvas.drawText(text, 60f, 60f - (metrics.ascent + metrics.descent) / 2f, paint)
    return bitmap
}
