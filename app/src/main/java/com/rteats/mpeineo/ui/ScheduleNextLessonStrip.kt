package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Compact task-first summary above the agenda. A click focuses the exact
 * cached lesson; it does not launch another timetable request or duplicate
 * a full lesson card.
 */
@Composable
internal fun ScheduleNextLessonStrip(
    state: MainUiState,
    onClick: (HomeLessonHighlight) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by rememberMpeiNow()
    val upcoming = remember(state.agendaWeeks, now) {
        nextHomeLesson(
            listOfNotNull(state.agendaWeeks[0], state.agendaWeeks[1]), now,
        )
    } ?: return
    Surface(
        onClick = { onClick(upcoming) },
        modifier = modifier.fillMaxWidth().testTag("schedule-next-lesson"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val heading = if (upcoming.isOngoing) "Идёт сейчас" else "Следующая пара"
            Text(heading, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    upcoming.lesson.startTime,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    upcoming.lesson.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val detail = listOfNotNull(
                upcoming.lesson.kind.takeIf { it.isNotBlank() },
                upcoming.lesson.place.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
