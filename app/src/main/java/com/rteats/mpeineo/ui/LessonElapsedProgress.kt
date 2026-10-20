package com.rteats.mpeineo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** MPEI publishes lesson dates and times in Moscow time, not device-local time. */
internal val mpeiTimeZone: ZoneId = ZoneId.of("Europe/Moscow")
private val lessonTimeFormat = DateTimeFormatter.ofPattern("H:mm")

/**
 * Elapsed time in [0,1), only for a lesson currently in progress.
 * Never show a progress fill for a past/future lesson or a malformed interval.
 */
internal fun lessonElapsedFraction(
    date: LocalDate,
    startTime: String,
    endTime: String,
    now: LocalDateTime,
): Float? {
    val start = runCatching { LocalTime.parse(startTime.trim(), lessonTimeFormat) }
        .getOrNull() ?: return null
    val end = runCatching { LocalTime.parse(endTime.trim(), lessonTimeFormat) }
        .getOrNull() ?: return null
    if (!end.isAfter(start)) return null
    val startsAt = date.atTime(start)
    val endsAt = date.atTime(end)
    if (now.isBefore(startsAt) || !now.isBefore(endsAt)) return null

    val totalMillis = Duration.between(startsAt, endsAt).toMillis()
    if (totalMillis <= 0) return null
    val elapsedMillis = Duration.between(startsAt, now).toMillis()
    return (elapsedMillis.toDouble() / totalMillis).toFloat().coerceIn(0f, 1f)
}

/**
 * One lifecycle-bound timer per mounted view (rather than per lesson row).
 * A 15-second interval keeps the background fill current without an
 * infinite Compose frame animation or needless per-frame invalidation.
 */
@Composable
internal fun rememberMpeiNow(): State<LocalDateTime> = produceState(
    initialValue = LocalDateTime.now(mpeiTimeZone),
) {
    while (true) {
        value = LocalDateTime.now(mpeiTimeZone)
        delay(15_000L)
    }
}

/**
 * Draw on the existing card's base, under ALL its text and touch handling.
 * Parent Surface clips the rectangular fill to the card's animated corners.
 */
@Composable
internal fun LessonElapsedBackdrop(
    progress: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            color = fillColor,
            size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
        )
    }
}
