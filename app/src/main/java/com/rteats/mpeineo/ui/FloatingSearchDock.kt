package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/** Which search is initially selected, and the contextual hint shown in the dock. */
internal data class FloatingSearchContext(
    val scope: SearchScope,
    val placeholder: String,
)

internal fun floatingSearchContext(tab: AppTab?): FloatingSearchContext = when (tab) {
    null -> FloatingSearchContext(SearchScope.ALL, "Поиск по МЭИ")
    AppTab.SCHEDULE -> FloatingSearchContext(
        SearchScope.SCHEDULE, "Группа, преподаватель, аудитория",
    )
    AppTab.BARS -> FloatingSearchContext(SearchScope.BARS, "Поиск дисциплины в БАРС")
    AppTab.MAIL -> FloatingSearchContext(SearchScope.MAIL, "Поиск по почте")
    AppTab.MAPS -> FloatingSearchContext(SearchScope.MAPS, "Корпус или аудитория")
    AppTab.SETTINGS -> FloatingSearchContext(SearchScope.ALL, "Поиск по МЭИ")
}

/**
 * Single stationary search affordance at the root of the application.
 * It does not move with service transitions or intercept swipes elsewhere.
 */
@Composable
internal fun FloatingSearchDock(
    context: FloatingSearchContext,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onScheduleRefresh: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("floating-global-search"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UnifiedSearchEntry(
            placeholder = context.placeholder,
            onClick = onSearch,
            modifier = Modifier.weight(1f).shadow(
                elevation = 6.dp,
                shape = MaterialTheme.shapes.extraLarge,
            ),
            tag = "floating-search-entry",
        )
        if (onScheduleRefresh != null) {
            FilledTonalIconButton(
                onClick = onScheduleRefresh,
                modifier = Modifier.size(56.dp)
                    .testTag("schedule-week-refresh"),
            ) {
                AppIcon(AppIcons.Refresh, contentDescription = "Обновить неделю")
            }
        }
    }
}
