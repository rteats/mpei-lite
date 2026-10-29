package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Cached summaries only: Home never starts a second network request or WebView. */
@Composable
internal fun HomeDashboardScreen(
    schedule: MainUiState,
    bars: BarsUiState,
    mail: MailUiState,
    onSchedule: () -> Unit,
    onNextLesson: (HomeLessonHighlight) -> Unit,
    onBars: () -> Unit,
    onMail: () -> Unit,
    onMap: () -> Unit,
) {
    val nextLesson = nextHomeLesson(
        schedule.agendaWeeks.values,
        LocalDateTime.now(ZoneId.of("Europe/Moscow")),
    )
    val unread = mail.items.count { it.unread }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home-dashboard"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Сегодня", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            DashboardPanel(
                title = "Расписание",
                subtitle = if (nextLesson == null) {
                    if (schedule.selected == null) "Выберите расписание"
                    else "Ближайших пар в загруженных неделях нет"
                } else {
                    val time = nextLesson.startsAt.format(DateTimeFormatter.ofPattern("HH:mm"))
                    (if (nextLesson.isOngoing) "Сейчас" else time) +
                        " · " + nextLesson.lesson.name + " · " + nextLesson.lesson.place
                },
                icon = AppIcons.Schedule,
                onClick = {
                    if (nextLesson == null) onSchedule() else onNextLesson(nextLesson)
                },
            )
        }
        item {
            DashboardPanel(
                title = "БАРС",
                subtitle = when {
                    bars.loginRequired -> "Требуется вход"
                    bars.disciplines.isEmpty() -> "Откройте оценки"
                    else -> "Дисциплин: ${bars.disciplines.size}"
                },
                icon = AppIcons.Bars,
                onClick = onBars,
            )
        }
        item {
            DashboardPanel(
                title = "Почта",
                subtitle = when {
                    !mail.configured || mail.loginRequired -> "Настройте почту"
                    unread == 0 -> "Нет непрочитанных писем"
                    else -> "Непрочитанных писем: $unread"
                },
                icon = AppIcons.Mail,
                onClick = onMail,
            )
        }
        item {
            DashboardPanel(
                title = "Карта кампуса",
                subtitle = "Корпуса, аудитории и объекты МЭИ",
                icon = AppIcons.Location,
                onClick = onMap,
            )
        }
    }
}

@Composable
private fun DashboardPanel(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
