package com.rteats.mpeineo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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

internal enum class FloatingDockAction { NONE, REFRESH_WEEK, COMPOSE_MAIL }

/** The dock owns its trailing action: Mail never draws a second floating button. */
internal fun floatingDockAction(tab: AppTab?, mailListReady: Boolean): FloatingDockAction =
    when {
        tab == AppTab.SCHEDULE -> FloatingDockAction.REFRESH_WEEK
        tab == AppTab.MAIL && mailListReady -> FloatingDockAction.COMPOSE_MAIL
        else -> FloatingDockAction.NONE
    }

/**
 * One stationary search affordance and one contextual trailing action.
 * The dock lives above route animations and cannot intercept map/scroll gestures elsewhere.
 */
@Composable
internal fun FloatingSearchDock(
    context: FloatingSearchContext,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onScheduleRefresh: (() -> Unit)? = null,
    scheduleRefreshing: Boolean = false,
    onMailCompose: (() -> Unit)? = null,
) {
    val action = when {
        onScheduleRefresh != null -> FloatingDockAction.REFRESH_WEEK
        onMailCompose != null -> FloatingDockAction.COMPOSE_MAIL
        else -> FloatingDockAction.NONE
    }
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
        AnimatedContent(
            targetState = action,
            transitionSpec = {
                (fadeIn(tween(170)) + scaleIn(tween(230), initialScale = 0.82f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(tween(180), targetScale = 0.88f))
            },
            label = "floating-dock-action",
        ) { visibleAction ->
            when (visibleAction) {
                FloatingDockAction.REFRESH_WEEK -> FilledTonalIconButton(
                    onClick = { onScheduleRefresh?.invoke() },
                    enabled = !scheduleRefreshing,
                    modifier = Modifier.size(56.dp)
                        .shadow(6.dp, CircleShape)
                        .testTag("schedule-week-refresh"),
                ) {
                    Crossfade(
                        targetState = scheduleRefreshing,
                        animationSpec = tween(180),
                        label = "floating-week-refresh-progress",
                    ) { refreshing ->
                        if (refreshing) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            AppIcon(AppIcons.Refresh, contentDescription = "Обновить неделю")
                        }
                    }
                }
                FloatingDockAction.COMPOSE_MAIL -> FilledTonalIconButton(
                    onClick = { onMailCompose?.invoke() },
                    modifier = Modifier.size(56.dp)
                        .shadow(6.dp, CircleShape)
                        .testTag("mail-compose-fab"),
                ) {
                    AppIcon(AppIcons.Edit, contentDescription = "Написать письмо")
                }
                FloatingDockAction.NONE -> Unit
            }
        }
    }
}
