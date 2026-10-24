package com.rteats.mpeineo.ui

import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.model.Lesson
import com.rteats.mpeineo.MpeiNeoApplication
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt
import java.time.LocalTime
import java.util.Locale

@Composable
internal fun ScheduleScreen(
    state: MainUiState,
    onEnsureAgendaWeek: (Int) -> Unit,
    onRefreshAgendaWeek: (Int) -> Unit,
    onRetryAgendaWeek: (Int) -> Unit,
    onViewedAgendaWeek: (Int) -> Unit,
    onAgendaScrollingChanged: (Boolean) -> Unit,
    onLocateRoom: (String) -> Unit,
    todayJumpRequest: Int,
    onPreviewLessonClick: (HomeLessonHighlight) -> Unit = {},
    focusLesson: ScheduleLessonFocus? = null,
    onFocusConsumed: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (state.selected == null) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Выберите расписание",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Выберите избранное или найдите группу, преподавателя или аудиторию вверху.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            ScheduleNextLessonStrip(
                state = state,
                onClick = onPreviewLessonClick,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
            )
        ScheduleAgenda(
            state = state,
            onEnsureWeek = onEnsureAgendaWeek,
            onRefreshWeek = onRefreshAgendaWeek,
            onRetryWeek = onRetryAgendaWeek,
            onViewedWeek = onViewedAgendaWeek,
            onScrollInProgressChanged = onAgendaScrollingChanged,
            onLocateRoom = onLocateRoom,
            todayJumpRequest = todayJumpRequest,
            focusLesson = focusLesson,
            onFocusConsumed = onFocusConsumed,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        )
        }
    }
}

internal fun isOneHourBreak(previous: Lesson, next: Lesson): Boolean =
    runCatching {
        val end = LocalTime.parse(previous.endTime)
        val start = LocalTime.parse(next.startTime)
        Duration.between(end, start).toMinutes() == 60L
    }.getOrDefault(false)

/**
 * An anchored, expandable lesson. Number/title stay in place, while the
 * compact room yields its width immediately to the expanded title, while
 * the full location row appears with the rest of the details below.
 * The app's expressive MotionScheme coordinates layout, color and shape.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LessonCard(
    lesson: Lesson,
    onLocateRoom: (String) -> Unit = {},
    focusRequestId: Int? = null,
    onFocusConsumed: (Int) -> Unit = {},
    date: LocalDate? = null,
    now: LocalDateTime? = null,
) {
    var expanded by rememberSaveable(
        lesson.startTime, lesson.name, lesson.place, lesson.lecturer, lesson.groups,
    ) { mutableStateOf(false) }

    val elapsedFraction = if (date != null && now != null) {
        lessonElapsedFraction(date, lesson.startTime, lesson.endTime, now)
    } else null
    val progressColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val bringIntoView = remember { BringIntoViewRequester() }
    var visibleFocusId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(focusRequestId) {
        visibleFocusId = null
        if (focusRequestId != null) {
            // The parent finished scrolling to the correct day. Wait for the
            // card to be measured, bring its real bounds into view, then pulse.
            withFrameNanos { }
            bringIntoView.bringIntoView()
            visibleFocusId = focusRequestId
            // The navigation cue is one-shot, but must remain active until
            // its complete 180 + 850 + 650 ms animation has played.
            kotlinx.coroutines.delay(1_750L)
            onFocusConsumed(focusRequestId)
        }
    }
    val focusPulse = rememberDestinationFocusPulse(visibleFocusId)
    val profiler = (LocalContext.current.applicationContext as? MpeiNeoApplication)
        ?.container?.uiFrameProfiler
    // Keep the linked-destination pulse, but don't independently animate shape,
    // padding, colour and text measurement for every lesson card.
    val cornerRadius = 28.dp
    val targetContainer = lerp(
        lessonContainerColor(lesson.kind),
        MaterialTheme.colorScheme.primaryContainer,
        if (focusPulse > 0f) 0.12f + focusPulse * 0.35f
        else if (expanded) 0.12f else 0f,
    )
    val container by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "lesson-card-color",
    )
    val verticalPadding by animateDpAsState(
        targetValue = if (expanded) 13.dp else 10.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "lesson-card-padding",
    )

    Surface(
        onClick = {
            profiler?.markInteraction("LESSON_TOGGLE")
            expanded = !expanded
        },
        modifier = Modifier.fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .border(
                2.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = focusPulse),
                RoundedCornerShape(cornerRadius),
            )
            .testTag(if (focusRequestId != null) "schedule-lesson-focus" else "schedule-lesson"),
        shape = RoundedCornerShape(cornerRadius),
        color = container,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (elapsedFraction != null) {
                LessonElapsedBackdrop(
                    progress = elapsedFraction,
                    fillColor = progressColor,
                    modifier = Modifier.matchParentSize()
                        .testTag("schedule-lesson-elapsed-background"),
                )
            }
            Column(
                modifier = Modifier
                    .then(if (elapsedFraction != null) Modifier.semantics {
                        stateDescription = "Прошло " +
                            (elapsedFraction * 100).roundToInt() +
                            "% времени пары"
                    } else Modifier)
                    .padding(horizontal = 14.dp, vertical = verticalPadding),
            ) {
                Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LessonNumber(lesson.startTime)
                Text(
                    lesson.name,
                    modifier = Modifier.weight(1f),
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                // Do not animate the room's *width* here. A shrinking
                // trailing slot continuously remeasures the title, making
                // a long name wrap into three lines mid-transition and
                // settle into two. Release the full width on the first
                // expansion frame; animate the vertical reveal below.
                if (!expanded && lesson.place.isNotBlank()) {
                    Text(
                        lesson.place,
                        modifier = Modifier.widthIn(max = 100.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    // Local expansion is a small spatial change, not an opacity effect.
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                ),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            lesson.startTime + "–" + lesson.endTime,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (lesson.kind.isNotBlank()) LessonTypeChip(lesson.kind)
                    }
                    if (lesson.place.isNotBlank()) {
                        val building = campusBuildingForRoom(lesson.place)
                        LessonDetail(
                            icon = {
                                AppIcon(
                                    AppIcons.Location, contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            text = lesson.place,
                            onClick = if (building != null) {
                                { onLocateRoom(lesson.place) }
                            } else null,
                        )
                    }
                    if (lesson.lecturer.isNotBlank()) {
                        LessonDetail(
                            icon = {
                                AppIcon(
                                    AppIcons.Person, contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            text = lesson.lecturer,
                        )
                    }
                    if (lesson.groups.isNotBlank()) {
                        LessonDetail(
                            icon = {
                                AppIcon(
                                    AppIcons.GroupList, contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            text = lesson.groups,
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun LessonNumber(startTime: String) {
    lessonPeriodNumber(startTime)?.let { period ->
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        ) {
            Text(
                period.toString(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LessonDetail(
    icon: @Composable () -> Unit,
    text: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().then(
            if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodySmall) {
            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            AppIcon(
                AppIcons.Forward,
                contentDescription = "Показать корпус на карте",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LessonTypeChip(kind: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Text(
            kind,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
internal fun lessonContainerColor(kind: String): Color {
    val scheme = MaterialTheme.colorScheme
    val normalized = kind.lowercase(Locale.forLanguageTag("ru"))
    val semantic = when {
        "лаб" in normalized || "lab" in normalized -> Color(0xFFEF5350)
        "лек" in normalized || "lecture" in normalized -> Color(0xFF66BB6A)
        "сем" in normalized || "прак" in normalized ||
            "seminar" in normalized || "practice" in normalized -> Color(0xFFFFCA28)
        "конс" in normalized || "экзам" in normalized ||
            "зач" in normalized || "exam" in normalized -> scheme.onSurfaceVariant
        else -> return scheme.surfaceContainer
    }
    val monetAdjusted = if (
        "конс" in normalized || "экзам" in normalized ||
        "зач" in normalized || "exam" in normalized
    ) semantic else lerp(semantic, scheme.primary, 0.12f)

    return lerp(
        scheme.surfaceContainer,
        monetAdjusted,
        if (monetAdjusted == scheme.onSurfaceVariant) 0.08f else 0.13f,
    )
}
