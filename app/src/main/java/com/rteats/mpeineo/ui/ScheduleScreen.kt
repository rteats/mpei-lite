package com.rteats.mpeineo.ui

import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.model.Lesson
import java.time.Duration
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
    focusLesson: ScheduleLessonFocus? = null,
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
                        "Выберите избранное или найдите группу, преподавателя или аудиторию внизу.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    } else {
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
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        )
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
internal fun LessonCard(lesson: Lesson, onLocateRoom: (String) -> Unit = {}, focused: Boolean = false) {
    var expanded by rememberSaveable(
        lesson.startTime, lesson.name, lesson.place, lesson.lecturer, lesson.groups,
    ) { mutableStateOf(false) }

    val motion = MaterialTheme.motionScheme
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 20.dp else 28.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "lesson-corner",
    )
    val container by animateColorAsState(
        targetValue = lerp(
            lessonContainerColor(lesson.kind),
            MaterialTheme.colorScheme.primaryContainer,
            if (focused) 0.40f else if (expanded) 0.12f else 0f,
        ),
        animationSpec = motion.defaultEffectsSpec(),
        label = "lesson-container",
    )
    val verticalPadding by animateDpAsState(
        targetValue = if (expanded) 13.dp else 10.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "lesson-padding",
    )

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = verticalPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LessonNumber(lesson.startTime)
                Text(
                    lesson.name,
                    modifier = Modifier.weight(1f).animateContentSize(
                        animationSpec = motion.defaultSpatialSpec(),
                    ),
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
                    animationSpec = motion.defaultSpatialSpec(),
                ) + fadeIn(animationSpec = motion.defaultEffectsSpec()),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = motion.defaultSpatialSpec(),
                ) + fadeOut(animationSpec = motion.defaultEffectsSpec()),
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
private fun lessonContainerColor(kind: String): Color {
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
