package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.data.MailSummary
import com.rteats.mpeineo.model.Lesson
import com.rteats.mpeineo.model.ScheduleWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val moscowZone = ZoneId.of("Europe/Moscow")
private val lessonClock = DateTimeFormatter.ofPattern("H:mm")
private val shortRussianDate = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))

/** Use the published local timetable (Moscow time), not the phone's time zone. */
internal data class HomeLessonHighlight(
    val lesson: Lesson,
    val date: LocalDate,
    val startsAt: LocalDateTime,
    val isOngoing: Boolean,
    val minutesUntil: Long,
)

/**
 * Read only weeks already loaded by the schedule screen. A visit to Home must
 * never cause a new timetable request, and expired lessons must not be offered.
 */
internal fun nextHomeLesson(
    weeks: Collection<ScheduleWeek>,
    now: LocalDateTime,
): HomeLessonHighlight? = weeks.asSequence()
    .flatMap { it.days.asSequence() }
    .flatMap { day ->
        val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
            ?: return@flatMap emptySequence()
        day.lessons.asSequence().mapNotNull { lesson ->
            val start = runCatching { LocalTime.parse(lesson.startTime.trim(), lessonClock) }
                .getOrNull() ?: return@mapNotNull null
            val end = runCatching { LocalTime.parse(lesson.endTime.trim(), lessonClock) }
                .getOrNull() ?: start.plusMinutes(95)
            val startsAt = date.atTime(start)
            val endsAt = date.atTime(end)
            if (!endsAt.isAfter(now)) return@mapNotNull null
            HomeLessonHighlight(
                lesson = lesson,
                date = date,
                startsAt = startsAt,
                isOngoing = !startsAt.isAfter(now),
                minutesUntil = Duration.between(now, startsAt).toMinutes().coerceAtLeast(0),
            )
        }
    }
    .minByOrNull { it.startsAt }

@Composable
internal fun HomeNextLessonCard(
    state: MainUiState,
    onClick: () -> Unit,
    barsDisciplines: List<BarsDiscipline> = emptyList(),
    onOpenBarsDiscipline: (String) -> Unit = {},
) {
    var now by remember { mutableStateOf(LocalDateTime.now(moscowZone)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now(moscowZone)
            delay(60_000L)
        }
    }
    val upcoming = remember(state.agendaWeeks, now) {
        nextHomeLesson(listOfNotNull(state.agendaWeeks[0], state.agendaWeeks[1]), now)
    }
    val matchedSubject = remember(upcoming?.lesson?.name, barsDisciplines) {
        upcoming?.let { findBarsSubject(it.lesson.name, barsDisciplines) }
    }
    val heading = when {
        upcoming?.isOngoing == true -> "СЕЙЧАС ИДЁТ"
        upcoming != null -> "СЛЕДУЮЩАЯ ПАРА"
        else -> "РАСПИСАНИЕ"
    }
    val title = when {
        upcoming != null -> upcoming.lesson.name.ifBlank { "Пара" }
        state.selected == null -> "Выберите расписание"
        0 in state.agendaLoadingOffsets -> "Загружаем расписание"
        0 in state.agendaFailedOffsets -> "Расписание недоступно"
        state.agendaWeeks[0] == null -> "Нет данных о расписании"
        else -> "На этой неделе пары закончились"
    }
    val description = when {
        upcoming != null -> upcoming.lesson.place.ifBlank { "Аудитория не указана" }
        state.selected == null -> "Нажмите, чтобы выбрать группу"
        0 in state.agendaFailedOffsets -> "Откройте пары, чтобы повторить"
        else -> "Открыть расписание"
    }
    val whenLabel = when {
        upcoming == null -> null
        upcoming.isOngoing -> "Идёт сейчас"
        upcoming.date == now.toLocalDate() && upcoming.minutesUntil < 2 -> "Скоро"
        upcoming.date == now.toLocalDate() && upcoming.minutesUntil < 60 ->
            "Через " + upcoming.minutesUntil + " мин"
        upcoming.date == now.toLocalDate() ->
            "Через " + upcoming.minutesUntil / 60 + " ч " +
                upcoming.minutesUntil % 60 + " мин"
        upcoming.date == now.toLocalDate().plusDays(1) -> "Завтра"
        else -> upcoming.date.format(shortRussianDate)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("home-next-lesson"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    heading,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                AppIcon(AppIcons.Schedule, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (upcoming?.lesson?.kind?.isNotBlank() == true) {
                Surface(
                    modifier = Modifier.testTag("home-next-lesson-kind"),
                    shape = MaterialTheme.shapes.medium,
                    color = lessonContainerColor(upcoming.lesson.kind),
                ) {
                    Text(
                        upcoming.lesson.kind,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (upcoming != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        upcoming.lesson.startTime + "–" + upcoming.lesson.endTime,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (whenLabel != null) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                whenLabel,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (matchedSubject != null) {
                TextButton(
                    onClick = { onOpenBarsDiscipline(matchedSubject.disciplineName) },
                    modifier = Modifier.testTag("home-lesson-bars-link"),
                ) {
                    AppIcon(AppIcons.Bars, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Text(
                        "Оценки в БАРС",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeBarsPreview(
    state: BarsUiState,
    onOpen: () -> Unit,
    onOpenDiscipline: (String) -> Unit,
) {
    val lastChange = state.gradeChanges.values.flatMap { it }.lastOrNull()
    val changesCount = state.gradeChanges.values.sumOf { it.size }
    val title = when {
        changesCount > 0 -> if (changesCount == 1) "Изменилась оценка" else "Изменений: " + changesCount
        state.disciplines.isNotEmpty() -> "Оценки"
        state.loginRequired -> "Требуется вход"
        else -> "БАРС"
    }
    val detail = when {
        lastChange != null -> lastChange.disciplineName
        state.disciplines.isNotEmpty() -> state.disciplines.size.toString() + " дисциплин"
        else -> "Нет сохранённых оценок"
    }
    HomeCompactPreview(
        icon = AppIcons.Bars,
        label = "БАРС",
        title = title,
        detail = detail,
        tag = "home-bars-preview",
        onClick = { if (lastChange != null) onOpenDiscipline(lastChange.disciplineName) else onOpen() },
    )
}

@Composable
internal fun HomeMailPreview(
    state: MailUiState,
    onOpen: () -> Unit,
    onOpenMessage: (MailSummary) -> Unit,
) {
    val unread = state.items.filter { it.unread }
    val newestUnread = unread.maxByOrNull { it.sentAt }
    val title = when {
        unread.isNotEmpty() -> "Загружено непрочитанных: " + unread.size
        state.loginRequired -> "Требуется вход"
        state.configured -> "Всё прочитано"
        else -> "Почта"
    }
    val detail = when {
        newestUnread != null -> newestUnread.subject.ifBlank { "Без темы" }
        state.configured -> "Входящие"
        else -> "Нет сохранённых писем"
    }
    HomeCompactPreview(
        icon = AppIcons.Mail,
        label = "ПОЧТА",
        title = title,
        detail = detail,
        tag = "home-mail-preview",
        onClick = { if (newestUnread != null) onOpenMessage(newestUnread) else onOpen() },
    )
}

@Composable
private fun HomeCompactPreview(
    icon: Int,
    label: String,
    title: String,
    detail: String,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(136.dp).testTag(tag),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                AppIcon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
