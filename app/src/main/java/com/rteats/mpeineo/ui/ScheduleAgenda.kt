package com.rteats.mpeineo.ui

import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalContext
import com.rteats.mpeineo.MpeiNeoApplication
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.model.ScheduleDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Global search focus is a one-shot request anchored to a cached lesson. */
internal data class ScheduleLessonFocus(
    val requestId: Int,
    val weekOffset: Int,
    val date: String,
    val name: String,
    val startTime: String,
    val place: String? = null,
)

/**
 * The vertical agenda is an infinite sequence of bounded, seven-day pages.
 * Normal scrolling remains native LazyColumn scrolling. Only a deliberate
 * overscroll at Monday/Sunday changes week; no horizontal day pager exists.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScheduleAgenda(
    state: MainUiState,
    onEnsureWeek: (Int) -> Unit,
    onRefreshWeek: (Int) -> Unit,
    onRetryWeek: (Int) -> Unit,
    onViewedWeek: (Int) -> Unit,
    onScrollInProgressChanged: (Boolean) -> Unit,
    onLocateRoom: (String) -> Unit,
    todayJumpRequest: Int,
    focusLesson: ScheduleLessonFocus? = null,
    onFocusConsumed: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val clock = rememberMpeiNow()
    val today = remember { LocalDate.now(mpeiTimeZone) }
    val monday = remember(today) {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val currentDayIndex = today.dayOfWeek.value - 1
    val selectedId = state.selected?.id
    val selectedType = state.selected?.type
    var visibleWeek by rememberSaveable(selectedId, selectedType) { mutableIntStateOf(0) }
    var entryDay by rememberSaveable(selectedId, selectedType) {
        mutableIntStateOf(currentDayIndex)
    }
    val locale = remember { Locale.forLanguageTag("ru") }
    val weekDateFormat = remember { DateTimeFormatter.ofPattern("d MMM", locale) }
    val haptic = LocalHapticFeedback.current
    val profiler = (LocalContext.current.applicationContext as? MpeiNeoApplication)
        ?.container?.uiFrameProfiler
    val overscrollThreshold = with(LocalDensity.current) { 76.dp.toPx() }
    val overscrollMax = with(LocalDensity.current) { 180.dp.toPx() }

    LaunchedEffect(selectedId, selectedType, visibleWeek) {
        onViewedWeek(visibleWeek)
        onScrollInProgressChanged(false)
        onEnsureWeek(visibleWeek)
        // Preload adjacent weeks without displaying their days.
        onEnsureWeek(visibleWeek - 1)
        onEnsureWeek(visibleWeek + 1)
    }
    LaunchedEffect(todayJumpRequest) {
        visibleWeek = 0
        entryDay = currentDayIndex
    }

    LaunchedEffect(focusLesson?.requestId) {
        val request = focusLesson ?: return@LaunchedEffect
        visibleWeek = request.weekOffset
        entryDay = runCatching { LocalDate.parse(request.date).dayOfWeek.value - 1 }
            .getOrDefault(0)
    }

    // transitionSpec is not @Composable: resolve tokens while in composition.
    val weekEnterEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val weekExitEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    key(selectedId, selectedType) {
    AnimatedContent(
        targetState = visibleWeek,
        modifier = modifier.fillMaxSize(),
        label = "schedule-week",
        transitionSpec = {
            // Opacity is an effects role: never apply a bouncy spatial spring
            // to fades. Keep incoming content more gradual than outgoing.
            fadeIn(weekEnterEffects) togetherWith
                fadeOut(weekExitEffects)
        },
    ) { displayedWeek ->
        val (weekStart, weekEnd) = weekRange(monday, displayedWeek)
        // These strings are static for this week. Avoid date formatting on
        // every pointer-driven overscroll animation frame.
        val previousWeekLabel = remember(monday, displayedWeek, weekDateFormat) {
            weekRange(monday, displayedWeek - 1).let { (start, end) ->
                start.format(weekDateFormat) + " – " + end.format(weekDateFormat)
            }
        }
        val nextWeekLabel = remember(monday, displayedWeek, weekDateFormat) {
            weekRange(monday, displayedWeek + 1).let { (start, end) ->
                start.format(weekDateFormat) + " – " + end.format(weekDateFormat)
            }
        }
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = entryDay.coerceIn(0, 6),
        )
        LaunchedEffect(todayJumpRequest, displayedWeek) {
            if (displayedWeek == 0 && visibleWeek == 0) {
                listState.animateScrollToItem(currentDayIndex)
            }
        }
        // Publish focus only after the day itself has finished scrolling.
        // The lesson card then brings its own bounds into view.
        var readyFocusId by remember(displayedWeek) { mutableIntStateOf(-1) }
        LaunchedEffect(displayedWeek, visibleWeek, listState) {
            if (displayedWeek == visibleWeek) {
                snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                    onScrollInProgressChanged(scrolling)
                }
            }
        }
        LaunchedEffect(focusLesson?.requestId, displayedWeek, state.agendaWeeks[displayedWeek]) {
            readyFocusId = -1
            val request = focusLesson ?: return@LaunchedEffect
            if (request.weekOffset != displayedWeek ||
                state.agendaWeeks[displayedWeek] == null) return@LaunchedEffect
            val index = runCatching { LocalDate.parse(request.date).dayOfWeek.value - 1 }
                .getOrDefault(0).coerceIn(0, 6)
            listState.animateScrollToItem(index)
            readyFocusId = request.requestId
        }
        // Only this distance determines both the week switch and visual feedback.
        var pullDistance by remember(displayedWeek) { mutableFloatStateOf(0f) }
        // Fling callbacks can continue after finger-up and may report UserInput.
        // Keep week switching strictly tied to a live touch gesture.
        var fingerDown by remember(displayedWeek) {
            androidx.compose.runtime.mutableStateOf(false)
        }
        var thresholdTicked by remember(displayedWeek) {
            androidx.compose.runtime.mutableStateOf(false)
        }
        val visualPull by animateFloatAsState(
            targetValue = pullDistance,
            // Fast spatial spring retargets on finger reversals and release;
            // threshold detection still reads the raw gesture distance.
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>(),
            label = "schedule-week-pull-stretch",
        )

        val pullConnection = remember(
            displayedWeek, listState, overscrollMax, overscrollThreshold, haptic,
        ) {
            object : NestedScrollConnection {
                private fun pull(deltaY: Float): Offset {
                    if (!fingerDown || displayedWeek != visibleWeek || deltaY == 0f) {
                        return Offset.Zero
                    }
                    val step = advanceWeekPull(
                        distance = pullDistance,
                        deltaY = deltaY,
                        atStart = !listState.canScrollBackward,
                        atEnd = !listState.canScrollForward,
                        maxDistance = overscrollMax,
                    )
                    if (shouldTickWeekPull(
                            oldDistance = pullDistance,
                            newDistance = step.distance,
                            threshold = overscrollThreshold,
                            alreadyTicked = thresholdTicked,
                        )
                    ) {
                        thresholdTicked = true
                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                    pullDistance = step.distance
                    return Offset(0f, step.consumedY)
                }

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset = if (source == NestedScrollSource.UserInput) {
                    pull(available.y)
                } else Offset.Zero

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset = if (source == NestedScrollSource.UserInput) {
                    // Include the unconsumed portion of a drag that reaches the
                    // first/last day after starting inside the list.
                    pull(available.y)
                } else Offset.Zero
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .nestedScroll(pullConnection)
                    .pointerInput(displayedWeek, selectedId, selectedType) {
                        // Nested scroll is the sole pull accumulator. This
                        // observer only decides on finger-up and always clears
                        // the gesture (including canceled/interrupted input).
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            fingerDown = true
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                } while (event.changes.any { it.pressed })

                                if (displayedWeek == visibleWeek) {
                                    when (weekDeltaForPull(pullDistance, overscrollThreshold)) {
                                        -1 -> {
                                            profiler?.markInteraction("WEEK_CHANGE")
                                            entryDay = 6
                                            visibleWeek = displayedWeek - 1
                                        }
                                        1 -> {
                                            profiler?.markInteraction("WEEK_CHANGE")
                                            entryDay = 0
                                            visibleWeek = displayedWeek + 1
                                        }
                                    }
                                }
                            } finally {
                                // Clear before a fling can dispatch another nested-scroll event.
                                fingerDown = false
                                pullDistance = 0f
                                thresholdTicked = false
                            }
                        }
                    },
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { translationY = visualPull * 0.22f },
                    state = listState,
                    // The global top app bar and persistent root navigation own insets.
                    contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (dayIndex in 0..6) {
                        val date = weekStart.plusDays(dayIndex.toLong())
                        item(key = "day-$date", contentType = "day-card") {
                            val dayData: ScheduleDay? =
                                state.agendaWeeks[displayedWeek]?.days
                                    ?.firstOrNull { it.date == date.toString() }
                            AgendaDayCard(
                                date = date,
                                today = today,
                                clock = clock,
                                day = dayData,
                                loading = displayedWeek in state.agendaLoadingOffsets,
                                failed = displayedWeek in state.agendaFailedOffsets,
                                onRetry = { onRetryWeek(displayedWeek) },
                                onLocateRoom = onLocateRoom,
                                focusLesson = focusLesson?.takeIf {
                                    it.weekOffset == displayedWeek &&
                                        it.date == date.toString() &&
                                        it.requestId == readyFocusId
                                },
                                onFocusConsumed = onFocusConsumed,
                            )
                        }
                    }
                }
                // Both indicators stay composed while fading out. Neither
                // can stick after release, cancellation or a week transition.
                WeekPullIndicator(
                    previous = true,
                    destination = previousWeekLabel,
                    progress = (pullDistance / overscrollThreshold).coerceIn(0f, 1f),
                    armed = pullDistance >= overscrollThreshold,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
                WeekPullIndicator(
                    previous = false,
                    destination = nextWeekLabel,
                    progress = (-pullDistance / overscrollThreshold).coerceIn(0f, 1f),
                    armed = pullDistance <= -overscrollThreshold,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
                )
            }
        }
    }
    }
}

/**
 * A destination preview instead of a loading-like pull cue. Gesture progress
 * controls reveal; the target dates do not change while the finger moves.
 * At threshold the arrow crossfades to a check and a single haptic tick fires.
 */
@Composable
private fun WeekPullIndicator(
    previous: Boolean,
    destination: String,
    progress: Float,
    armed: Boolean,
    modifier: Modifier = Modifier,
) {
    // Gesture feedback is driven directly by the finger, not by competing
    // duration-based animations that can outlive a released pull.
    val reveal = progress.coerceIn(0f, 1f)
    val color = if (armed) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.graphicsLayer {
            alpha = reveal.coerceIn(0f, 1f)
            translationY = (if (previous) -1f else 1f) *
                (1f - reveal) * 12.dp.toPx()
            scaleX = 0.96f + 0.04f * reveal
            scaleY = 0.96f + 0.04f * reveal
        },
        shape = RoundedCornerShape(24.dp),
        color = color,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(
                icon = when {
                    armed -> AppIcons.Confirm
                    previous -> AppIcons.ExpandMore
                    else -> AppIcons.ExpandLess
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    if (previous) "Предыдущая неделя" else "Следующая неделя",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.78f),
                )
                Text(
                    destination,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AgendaDaySection(
    date: LocalDate,
    clock: State<LocalDateTime>,
    day: ScheduleDay?,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onLocateRoom: (String) -> Unit,
    focusLesson: ScheduleLessonFocus?,
    onFocusConsumed: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            day != null -> {
                if (day.lessons.isEmpty()) {
                    Text(
                        "Пар нет",
                        modifier = Modifier.padding(start = 14.dp, bottom = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val oneHourBreaks = remember(day.lessons) {
                    day.lessons.indices.filter { index ->
                        index > 0 && isOneHourBreak(day.lessons[index - 1], day.lessons[index])
                    }.toSet()
                }
                day.lessons.forEachIndexed { index, lesson ->
                    if (index in oneHourBreaks) {
                        Spacer(Modifier.height(18.dp))
                    }
                    LessonCard(
                        lesson = lesson,
                        onLocateRoom = onLocateRoom,
                        focusRequestId = focusLesson?.takeIf {
                            it.name == lesson.name &&
                                it.startTime == lesson.startTime &&
                                (it.place == null || it.place == lesson.place)
                        }?.requestId,
                        onFocusConsumed = onFocusConsumed,
                        date = date,
                        now = clock.value,
                    )
                }
            }
            failed -> {
                Text(
                    "Не удалось загрузить эту неделю",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onRetry) { Text("Повторить") }
            }
            loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) { ContainedLoadingIndicator() }
            }
            else -> {
                Text(
                    "Загружаем расписание…",
                    modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Muted Monet-derived tonality for today, without an accent outline.
 * Keep lesson type backgrounds unchanged and all text onSurface for contrast.
 */
@Composable
private fun AgendaDayCard(
    date: LocalDate,
    today: LocalDate,
    clock: State<LocalDateTime>,
    day: ScheduleDay?,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onLocateRoom: (String) -> Unit,
    focusLesson: ScheduleLessonFocus?,
    onFocusConsumed: (Int) -> Unit,
) {
    val isToday = date == today
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (isToday) lerp(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.primaryContainer,
            0.18f,
        ) else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AgendaDayHeader(date, today)
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                AgendaDaySection(
                    date = date,
                    clock = clock,
                    day = day,
                    loading = loading,
                    failed = failed,
                    onRetry = onRetry,
                    onLocateRoom = onLocateRoom,
                    focusLesson = focusLesson,
                    onFocusConsumed = onFocusConsumed,
                )
            }
        }
    }
}

@Composable
private fun AgendaDayHeader(
    date: LocalDate,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val language = remember { Locale.forLanguageTag("ru") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", language) }
    val isToday = date == today
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isToday) lerp(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.primaryContainer,
            0.27f,
        ) else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            date.format(dateFormat).replaceFirstChar { it.titlecase(language) } +
                if (isToday) " • Сегодня" else "",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
