package com.rteats.mpeineo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/**
 * One compact Material 3 search app bar, not a floating search pill.
 * The actual input and cached/remote results live in GlobalSearchScreen.
 * A tap on the collapsed native search field opens that full-screen view.
 */
internal data class SearchAppBarContext(
    val scope: SearchScope,
    val placeholder: String,
)

internal fun searchAppBarContext(tab: AppTab?): SearchAppBarContext = when (tab) {
    null -> SearchAppBarContext(SearchScope.ALL, "Поиск по МЭИ")
    AppTab.SCHEDULE -> SearchAppBarContext(
        SearchScope.SCHEDULE, "Группа, преподаватель, аудитория",
    )
    AppTab.BARS -> SearchAppBarContext(SearchScope.BARS, "Поиск дисциплины в БАРС")
    AppTab.MAIL -> SearchAppBarContext(SearchScope.MAIL, "Поиск по почте")
    AppTab.MAPS -> SearchAppBarContext(SearchScope.MAPS, "Корпус или аудитория")
    AppTab.SETTINGS -> SearchAppBarContext(SearchScope.ALL, "Поиск по МЭИ")
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
) {
    // The collapsed field invokes the shared search screen; it deliberately
    // does not create a second IME or a second search-query state.
    val searchBarState = rememberSearchBarState()
    val action = when {
        onScheduleRefresh != null -> SearchAppBarAction.REFRESH_WEEK
        onMailCompose != null -> SearchAppBarAction.COMPOSE_MAIL
        else -> SearchAppBarAction.NONE
    }

    AppBarWithSearch(
        state = searchBarState,
        inputField = {
            SearchBarDefaults.InputField(
                query = "",
                onQueryChange = { onSearch() },
                onSearch = { onSearch() },
                expanded = false,
                onExpandedChange = { if (it) onSearch() },
                placeholder = { Text(context.placeholder, maxLines = 1) },
                leadingIcon = {
                    AppIcon(AppIcons.Search, contentDescription = null)
                },
                modifier = Modifier.testTag("search-app-bar-entry"),
            )
        },
        modifier = modifier.fillMaxWidth().testTag("search-app-bar"),
        actions = {
            AnimatedContent(
                targetState = action,
                transitionSpec = {
                    fadeIn(tween(140)) togetherWith fadeOut(tween(90))
                },
                label = "search-app-bar-action",
            ) { current ->
                when (current) {
                    SearchAppBarAction.REFRESH_WEEK -> IconButton(
                        onClick = { onScheduleRefresh?.invoke() },
                        enabled = !scheduleRefreshing,
                        modifier = Modifier.testTag("schedule-week-refresh"),
                    ) {
                        Crossfade(
                            targetState = scheduleRefreshing,
                            animationSpec = tween(150),
                            label = "search-app-bar-week-refresh",
                        ) { loading ->
                            if (loading) {
                                LoadingIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                AppIcon(
                                    AppIcons.Refresh,
                                    contentDescription = "Обновить неделю",
                                )
                            }
                        }
                    }
                    SearchAppBarAction.COMPOSE_MAIL -> IconButton(
                        onClick = { onMailCompose?.invoke() },
                        modifier = Modifier.testTag("mail-compose-fab"),
                    ) {
                        AppIcon(AppIcons.Edit, contentDescription = "Написать письмо")
                    }
                    SearchAppBarAction.NONE -> Unit
                }
            }
        },
    )
}
