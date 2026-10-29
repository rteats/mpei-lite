package com.rteats.mpeineo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.snap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/** One Material 3 search entry with shared edge spacing across all destinations. */
internal data class SearchAppBarContext(
    val scope: SearchScope,
    val placeholder: String,
)

/** Top-bar capability belongs to the destination, not to the root scaffold. */
internal enum class AppBarMode { SEARCH, TITLE }

internal fun appBarMode(tab: AppTab?): AppBarMode = when (tab) {
    AppTab.SETTINGS -> AppBarMode.TITLE
    else -> AppBarMode.SEARCH
}

internal fun searchAppBarContext(tab: AppTab?): SearchAppBarContext = when (tab) {
    null -> SearchAppBarContext(SearchScope.SCHEDULE, "Поиск расписания")
    AppTab.HOME -> SearchAppBarContext(SearchScope.SCHEDULE, "Поиск MPEI Lite")
    AppTab.SCHEDULE -> SearchAppBarContext(
        SearchScope.SCHEDULE, "Группа, преподаватель, аудитория",
    )
    AppTab.BARS -> SearchAppBarContext(SearchScope.BARS, "Поиск дисциплины в БАРС")
    AppTab.MAIL -> SearchAppBarContext(SearchScope.MAIL, "Поиск по почте")
    AppTab.MAPS -> SearchAppBarContext(SearchScope.MAPS, "Корпус или аудитория")
    AppTab.SETTINGS -> error("Settings has a title bar, not a timetable search bar")
}

internal enum class SearchAppBarAction { NONE, REFRESH_WEEK, COMPOSE_MAIL }

internal fun searchAppBarAction(tab: AppTab?, mailListReady: Boolean): SearchAppBarAction =
    when {
        tab == AppTab.SCHEDULE -> SearchAppBarAction.REFRESH_WEEK
        tab == AppTab.MAIL && mailListReady -> SearchAppBarAction.COMPOSE_MAIL
        else -> SearchAppBarAction.NONE
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContextSearchAppBar(
    context: SearchAppBarContext,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onScheduleRefresh: (() -> Unit)? = null,
    scheduleRefreshing: Boolean = false,
    onMailCompose: (() -> Unit)? = null,
    onSettings: () -> Unit = {},
) {
    val action = when {
        onScheduleRefresh != null -> SearchAppBarAction.REFRESH_WEEK
        onMailCompose != null -> SearchAppBarAction.COMPOSE_MAIL
        else -> SearchAppBarAction.NONE
    }
    // The app-wide spatial-only scheme makes long text moves too elastic.
    // Use the native Expressive *effects* spring specifically for this title.
    val titleEffects = remember { MotionScheme.expressive().fastEffectsSpec<Float>() }
    val actionSpring = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    TopAppBar(
        title = {
            AnimatedContent(
                targetState = context.scope,
                transitionSpec = {
                    // Remove the old glyphs immediately: no two service
                    // titles occupy the same position during a crossfade.
                    fadeIn(animationSpec = titleEffects) togetherWith
                        fadeOut(animationSpec = snap())
                },
                label = "service-app-bar-title",
            ) { scope ->
                Text(
                    when (scope) {
                        SearchScope.BARS -> "БАРС"
                        SearchScope.MAIL -> "Почта"
                        SearchScope.MAPS -> "Корпуса"
                        SearchScope.SCHEDULE -> "Пары"
                    },
                    maxLines = 1,
                )
            }
        },
        actions = {
            IconButton(
                onClick = onSearch,
                modifier = Modifier.testTag("compact-search-action"),
            ) {
                AppIcon(AppIcons.Search, contentDescription = "Поиск")
            }
            AnimatedVisibility(
                visible = action == SearchAppBarAction.REFRESH_WEEK,
                enter = expandHorizontally(
                    expandFrom = Alignment.End,
                    animationSpec = actionSpring,
                ),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.End,
                    animationSpec = actionSpring,
                ),
            ) {
                IconButton(
                    onClick = { onScheduleRefresh?.invoke() },
                    enabled = !scheduleRefreshing,
                    modifier = Modifier.testTag("schedule-week-refresh"),
                ) {
                    if (scheduleRefreshing) LoadingIndicator(modifier = Modifier.size(24.dp))
                    else AppIcon(AppIcons.Refresh, contentDescription = "Обновить неделю")
                }
            }
            AnimatedVisibility(
                visible = action == SearchAppBarAction.COMPOSE_MAIL,
                enter = expandHorizontally(
                    expandFrom = Alignment.End,
                    animationSpec = actionSpring,
                ),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.End,
                    animationSpec = actionSpring,
                ),
            ) {
                IconButton(
                    onClick = { onMailCompose?.invoke() },
                    modifier = Modifier.testTag("mail-compose-fab"),
                ) {
                    AppIcon(AppIcons.Edit, contentDescription = "Написать письмо")
                }
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier.testTag("service-settings"),
            ) {
                AppIcon(AppIcons.Settings, contentDescription = "Настройки")
            }
        },
        modifier = modifier.testTag("search-app-bar"),
    )
}
