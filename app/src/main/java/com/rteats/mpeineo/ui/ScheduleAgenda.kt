package com.rteats.mpeineo.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.model.Lesson
import com.rteats.mpeineo.model.ScheduleWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ScheduleLessonFocus(
    val requestId: Int,
    val weekOffset: Int,
    val date: String,
    val name: String,
    val startTime: String,
    val place: String? = null,
)

internal data class TimetableSlot(val date: LocalDate, val lessons: List<Lesson>)

private val weekdayFormat = DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("ru"))
private val fullDateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))
private val timeFormat = DateTimeFormatter.ofPattern("H:mm")

internal enum class TimetableLessonType(@get:DrawableRes val icon: Int) {
    LECTURE(AppIcons.Education),
    PRACTICE(AppIcons.Edit),
    LAB(AppIcons.Science),
    OTHER(AppIcons.Quiz),
}

internal fun timetableLessonType(kind: String): TimetableLessonType {
    val label = kind.trim().trimEnd('.').lowercase(Locale.ROOT)
    return when {
        label == "лк" || label.startsWith("лек") -> TimetableLessonType.LECTURE
        label == "пр" || label == "пз" || label.startsWith("прак") || label.startsWith("сем") ->
            TimetableLessonType.PRACTICE
        label == "лб" || label.startsWith("лаб") -> TimetableLessonType.LAB
        else -> TimetableLessonType.OTHER
    }
}

internal fun TimetableLessonType.label(kind: String): String = when (this) {
    TimetableLessonType.LECTURE -> "Лекция"
    TimetableLessonType.PRACTICE ->
        if (kind.trim().startsWith("сем", ignoreCase = true)) "Семинар" else "Практика"
    TimetableLessonType.LAB -> "Лабораторная работа"
    TimetableLessonType.OTHER -> kind.ifBlank { "Другое занятие" }
}

@Composable
internal fun ScheduleAgenda(
    today: LocalDate,
    weeks: Map<Int, ScheduleWeek>,
    aliases: Map<String, String>,
    listState: LazyListState,
    onSlot: (TimetableSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = remember(weeks) {
        weeks.values.flatMap { it.days }.mapNotNull { day ->
            runCatching { LocalDate.parse(day.date) }.getOrNull()?.let {
                it to timetableLessonsByStart(day.lessons)
            }
        }.toMap()
    }
    val periods = remember(days) {
        val extraTimes = days.values.flatMap { it.keys }.toSet()
        val times = (timetablePeriodStarts + extraTimes.filterNotNull()).distinct().sorted()
        if (null in extraTimes) times + listOf(null) else times
    }
    val flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start)
    BoxWithConstraints(
        modifier = modifier.windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Horizontal)),
    ) {
        val density = LocalDensity.current
        val fontScale = density.fontScale.coerceAtLeast(1f)
        val gutter = (46.dp * fontScale).coerceAtMost(maxWidth * .32f)
        val columnArea = (maxWidth - gutter).coerceAtLeast(1.dp)
        val minColumn = 70.dp * (1f + (fontScale - 1f) * .8f)
        val columnCount = (columnArea / minColumn).toInt().coerceIn(1, 7)
        val columnWidth = columnArea / columnCount
        val dayHeight = 56.dp * fontScale
        val cardHeight = with(density) {
            val available = maxHeight.roundToPx() - dayHeight.roundToPx() - AppSpacing.sm.roundToPx()
            (available / periods.size).toDp().coerceAtLeast(60.dp * fontScale)
        }
        val scroll = rememberScrollState()
        val pinnedHeader = Modifier.zIndex(1f)
            .offset { IntOffset(0, scroll.value) }
            .fillMaxWidth().height(dayHeight)
            .background(MaterialTheme.colorScheme.background)
        Row(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(bottom = AppSpacing.sm),
        ) {
            Column(Modifier.width(gutter)) {
                Spacer(pinnedHeader)
                periods.forEach { period ->
                    Box(
                        Modifier.fillMaxWidth().height(cardHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            period?.format(timeFormat) ?: "—",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
            LazyRow(
                modifier = Modifier.weight(1f).testTag("schedule-timetable-grid"),
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(end = columnArea - columnWidth),
            ) {
                items(timetableDayCount, key = { it }) { index ->
                    val date = remember(index) { timetableDayAt(index) }
                    TimetableDayColumn(
                        date, today, days[date].orEmpty(), aliases, periods,
                        columnWidth, dayHeight, cardHeight, pinnedHeader, onSlot,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimetableDayColumn(
    date: LocalDate,
    today: LocalDate,
    lessons: Map<LocalTime?, List<Lesson>>,
    aliases: Map<String, String>,
    periods: List<LocalTime?>,
    width: Dp,
    dayHeight: Dp,
    cardHeight: Dp,
    pinnedHeader: Modifier,
    onSlot: (TimetableSlot) -> Unit,
) {
    val boundary = date.dayOfWeek == DayOfWeek.MONDAY
    val colors = MaterialTheme.colorScheme
    val (dayColor, dayText) = when {
        date == today -> colors.primary to colors.onPrimary
        boundary -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.surfaceContainer to colors.onSurface
    }
    val slots = remember(date, periods, lessons) {
        periods.map { period -> lessons[period]?.takeIf { it.isNotEmpty() }?.let { TimetableSlot(date, it) } }
    }
    val cellShape = MaterialTheme.shapes.medium
    val dividerWidth = if (boundary) 2.dp else 0.dp
    val dayInset = if (boundary) dividerWidth + 3.dp else 0.dp
    val cellPadding = 3.dp
    Layout(
        modifier = Modifier.width(width).testTag("schedule-day-$date").drawWithCache {
            val rtl = layoutDirection == LayoutDirection.Rtl
            val inset = dayInset.roundToPx()
            val padding = cellPadding.roundToPx()
            val rowHeight = cardHeight.roundToPx()
            val startX = if (rtl) padding else inset + padding
            val outline = cellShape.createOutline(
                Size((size.width - inset - padding * 2).coerceAtLeast(0f),
                    (rowHeight - padding * 2).coerceAtLeast(0).toFloat()),
                layoutDirection, this,
            )
            val firstRowY = dayHeight.roundToPx() + padding
            onDrawBehind {
                if (boundary) {
                    val lineWidth = dividerWidth.roundToPx().toFloat()
                    drawRect(colors.outlineVariant, Offset(if (rtl) size.width - lineWidth else 0f, 0f),
                        Size(lineWidth, size.height))
                }
                slots.forEachIndexed { index, slot ->
                    if (slot == null) {
                        translate(startX.toFloat(), (firstRowY + index * rowHeight).toFloat()) {
                            drawOutline(outline, colors.surfaceContainerLow)
                        }
                    }
                }
            }
        },
        content = {
            Surface(
                modifier = pinnedHeader.padding(3.dp).testTag("schedule-day-header-$date")
                    .semantics(mergeDescendants = true) {
                        heading()
                        contentDescription = date.format(fullDateFormat)
                    },
                shape = MaterialTheme.shapes.medium,
                color = dayColor,
                contentColor = dayText,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(date.format(weekdayFormat), style = MaterialTheme.typography.labelMedium)
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
            slots.forEach { slot ->
                if (slot != null) TimetableCell(Modifier.padding(cellPadding), slot, aliases, onSlot)
            }
        },
    ) { measurables, constraints ->
        val inset = dayInset.roundToPx()
        val columnWidth = constraints.maxWidth
        val contentWidth = (columnWidth - inset).coerceAtLeast(0)
        val headerHeight = dayHeight.roundToPx()
        val rowHeight = cardHeight.roundToPx()
        val header = measurables.first().measure(Constraints.fixed(contentWidth, headerHeight))
        val cells = measurables.drop(1).map { it.measure(Constraints.fixed(contentWidth, rowHeight)) }
        layout(columnWidth, headerHeight + rowHeight * slots.size) {
            header.placeRelative(inset, 0)
            var cell = 0
            slots.forEachIndexed { index, slot ->
                if (slot != null) cells[cell++].placeRelative(inset, headerHeight + index * rowHeight)
            }
        }
    }
}

@Composable
private fun TimetableCell(
    modifier: Modifier,
    slot: TimetableSlot,
    aliases: Map<String, String>,
    onSlot: (TimetableSlot) -> Unit,
) {
    val lesson = slot.lessons.first()
    val additionalLessons = slot.lessons.size - 1
    val colors = MaterialTheme.colorScheme
    val type = timetableLessonType(lesson.kind)
    val (container, foreground) = when (type) {
        TimetableLessonType.LECTURE -> colors.primaryContainer to colors.onPrimaryContainer
        TimetableLessonType.PRACTICE -> colors.secondaryContainer to colors.onSecondaryContainer
        TimetableLessonType.LAB -> colors.tertiaryContainer to colors.onTertiaryContainer
        TimetableLessonType.OTHER -> colors.surfaceContainerHigh to colors.onSurface
    }
    val alias = remember(lesson.name, aliases) { timetableLessonLabel(lesson.name, aliases) }
    val description = remember(slot, type) {
        listOfNotNull(
            lesson.name, type.label(lesson.kind), slot.date.format(fullDateFormat),
            lesson.startTime.takeIf(String::isNotBlank),
            "Всего занятий — ${slot.lessons.size}".takeIf { additionalLessons > 0 },
        ).joinToString(", ")
    }
    Surface(
        onClick = { onSlot(slot) },
        modifier = modifier.testTag("schedule-lesson").semantics { contentDescription = description },
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = foreground,
    ) {
        Column(
            Modifier.padding(horizontal = AppSpacing.xs, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(type.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                if (additionalLessons > 0) {
                    Text("+$additionalLessons", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
            Text(
                alias,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
