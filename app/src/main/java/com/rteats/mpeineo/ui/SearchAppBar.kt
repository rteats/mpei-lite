package com.rteats.mpeineo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/** One Material 3 search entry with shared edge spacing across all destinations. */
internal data class SearchAppBarContext(
    val scope: SearchScope,
    val placeholder: String,
)

internal fun searchAppBarContext(tab: AppTab?): SearchAppBarContext = when (tab) {
    null -> SearchAppBarContext(SearchScope.SCHEDULE, "Поиск расписания")
    AppTab.SCHEDULE -> SearchAppBarContext(
        SearchScope.SCHEDULE, "Группа, преподаватель, аудитория",
    )
    AppTab.BARS -> SearchAppBarContext(SearchScope.BARS, "Поиск дисциплины в БАРС")
    AppTab.MAIL -> SearchAppBarContext(SearchScope.MAIL, "Поиск по почте")
    AppTab.MAPS -> SearchAppBarContext(SearchScope.MAPS, "Корпус или аудитория")
    AppTab.SETTINGS -> SearchAppBarContext(SearchScope.SCHEDULE, "Поиск расписания")
}

internal enum class SearchAppBarAction { NONE, HOME, REFRESH_WEEK, COMPOSE_MAIL }

internal fun searchAppBarAction(tab: AppTab?, mailListReady: Boolean): SearchAppBarAction =
    when {
        tab == null -> SearchAppBarAction.HOME
        tab == AppTab.SCHEDULE -> SearchAppBarAction.REFRESH_WEEK
        tab == AppTab.MAIL && mailListReady -> SearchAppBarAction.COMPOSE_MAIL
        else -> SearchAppBarAction.NONE
    }

/**
 * Search field is always 16dp from the leading screen edge. Material internally
 * gives its field another 8dp and its action row 4dp, so the right-hand padding
 * must change when a trailing button is present. The icon touch targets end at
 * 16dp; an action-free search field also ends at 16dp.
 */
internal val SEARCH_APP_BAR_EDGE = 16.dp
internal val SEARCH_APP_BAR_START_PADDING = 8.dp
internal val SEARCH_APP_BAR_END_WITH_ACTION = 12.dp
internal val SEARCH_APP_BAR_END_WITHOUT_ACTION = 4.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContextSearchAppBar(
    context: SearchAppBarContext,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onScheduleRefresh: (() -> Unit)? = null,
    scheduleRefreshing: Boolean = false,
    onMailCompose: (() -> Unit)? = null,
    homeEditing: Boolean = false,
    onHomeEditToggle: (() -> Unit)? = null,
    onHomeSettings: (() -> Unit)? = null,
    onSearchBoundsChanged: (Rect) -> Unit = {},
) {
    val searchBarState = rememberSearchBarState()
    val action = when {
        onHomeEditToggle != null && onHomeSettings != null -> SearchAppBarAction.HOME
        onScheduleRefresh != null -> SearchAppBarAction.REFRESH_WEEK
        onMailCompose != null -> SearchAppBarAction.COMPOSE_MAIL
        else -> SearchAppBarAction.NONE
    }
    val endPadding = animateDpAsState(
        targetValue = if (action == SearchAppBarAction.NONE) {
            SEARCH_APP_BAR_END_WITHOUT_ACTION
        } else {
            SEARCH_APP_BAR_END_WITH_ACTION
        },
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "search-app-bar-edge-padding",
    )
    // Home has two 48dp buttons. One-button replacements must start past the
    // viewport even while the outgoing two-button cluster still has its width.
    val slideBeyondScreen = with(LocalDensity.current) {
        (SEARCH_APP_BAR_EDGE + 96.dp).roundToPx()
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
                modifier = Modifier.testTag("search-app-bar-entry")
                    .onGloballyPositioned { onSearchBoundsChanged(it.boundsInRoot()) },
            )
        },
        modifier = modifier.fillMaxWidth().testTag("search-app-bar"),
        contentPadding = PaddingValues(
            start = SEARCH_APP_BAR_START_PADDING,
            end = endPadding.value,
        ),
        actions = {
            // Animate the whole trailing cluster in screen coordinates, not
            // only within the rounded search field or from its right corner.
            // Keep outgoing content unclipped so it can leave past the edge.
            AnimatedContent(
                targetState = action,
                contentAlignment = Alignment.CenterEnd,
                transitionSpec = {
                    (
                        (slideInHorizontally(
                            initialOffsetX = { it + slideBeyondScreen },
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                        ) + fadeIn(tween(190))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { it + slideBeyondScreen },
                                animationSpec = tween(230, easing = FastOutSlowInEasing),
                            ) + fadeOut(tween(130)))
                    ).using(SizeTransform(clip = false))
                },
                label = "search-app-bar-action",
            ) { current ->
                when (current) {
                    SearchAppBarAction.HOME -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { onHomeEditToggle?.invoke() },
                            modifier = Modifier.testTag("superapp-edit"),
                        ) {
                            Crossfade(
                                targetState = homeEditing,
                                animationSpec = tween(160),
                                label = "home-edit-icon",
                            ) { editing ->
                                AppIcon(
                                    if (editing) AppIcons.Confirm else AppIcons.Edit,
                                    contentDescription =
                                        if (editing) "Готово" else "Настроить кнопки",
                                )
                            }
                        }
                        IconButton(
                            onClick = { onHomeSettings?.invoke() },
                            modifier = Modifier.testTag("superapp-settings"),
                        ) {
                            AppIcon(AppIcons.Settings, contentDescription = "Настройки")
                        }
                    }
                    SearchAppBarAction.REFRESH_WEEK -> IconButton(
                        onClick = { onScheduleRefresh?.invoke() },
                        enabled = !scheduleRefreshing,
                        modifier = Modifier.testTag("schedule-week-refresh"),
                    ) {
                        Crossfade(
                            targetState = scheduleRefreshing,
                            animationSpec = tween(160),
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
