package com.rteats.mpeineo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType

/**
 * Search is a destination inside the schedule service, not an overlay WebView.
 * The expensive agenda is not composed while search results are visible.
 */
@Composable
internal fun ScheduleExplorerScreen(
    state: MainUiState,
    onQueryChange: (String) -> Unit,
    onSearchTypeChange: (ScheduleTargetType?) -> Unit,
    onSelectTarget: (ScheduleTarget) -> Unit,
    onToggleFavorite: (ScheduleTarget) -> Unit,
    onEnsureWeek: (Int) -> Unit,
    onRefreshWeek: (Int) -> Unit,
    onRetryWeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    val query = state.searchQuery.trim()
    val favorites = remember(state.favorites) { state.favorites.map { it.type to it.id }.toSet() }

    fun openTarget(target: ScheduleTarget) {
        focusManager.clearFocus()
        keyboard?.hide()
        onSelectTarget(target)
        onQueryChange("")
        searching = false
    }

    BackHandler(searching) {
        focusManager.clearFocus()
        keyboard?.hide()
        searching = false
    }

    Column(
        modifier = modifier.fillMaxSize().testTag("schedule-explorer"),
    ) {
        if (searching) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focus).testTag("schedule-target-query"),
                leadingIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        searching = false
                    }) { AppIcon(AppIcons.Back, contentDescription = "Закрыть поиск") }
                },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            AppIcon(AppIcons.ClearLog, contentDescription = "Очистить запрос")
                        }
                    }
                },
                placeholder = { Text("Группа, преподаватель, аудитория…") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )
            LaunchedEffect(searching) { focus.requestFocus() }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scheduleSearchFilters.forEach { (label, type) ->
                    FilterChip(
                        selected = state.searchType == type,
                        onClick = { onSearchTypeChange(type) },
                        label = { Text(label) },
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            val listState = rememberLazyListState()
            LaunchedEffect(state.searchQuery, state.searchType) {
                listState.scrollToItem(0)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .testTag("schedule-search-results"),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (query.isEmpty()) {
                    val favoriteTargets = state.favorites.filter {
                        state.searchType == null || it.type == state.searchType
                    }
                    val recentTargets = state.recentlyViewed.filter { target ->
                        (state.searchType == null || target.type == state.searchType) &&
                            favoriteTargets.none { it.type == target.type && it.id == target.id }
                    }
                    if (favoriteTargets.isNotEmpty()) {
                        item(key = "favorites-header") {
                            SearchSectionHeading("Избранное", favoriteTargets.size)
                        }
                        items(favoriteTargets, key = { "favorite_${it.type}_${it.id}" }) { target ->
                            SearchTargetRow(
                                target = target, favorite = true,
                                onOpen = { openTarget(target) },
                                onToggleFavorite = { onToggleFavorite(target) },
                            )
                        }
                    }
                    if (recentTargets.isNotEmpty()) {
                        item(key = "recent-header") {
                            SearchSectionHeading("Недавние", recentTargets.size)
                        }
                        items(recentTargets, key = { "recent_${it.type}_${it.id}" }) { target ->
                            SearchTargetRow(
                                target = target, favorite = false,
                                onOpen = { openTarget(target) },
                                onToggleFavorite = { onToggleFavorite(target) },
                            )
                        }
                    }
                    if (favoriteTargets.isEmpty() && recentTargets.isEmpty()) {
                        item(key = "empty-history") {
                            SearchHint(
                                title = "Найдите своё расписание",
                                detail = "Начните вводить группу, фамилию или номер аудитории. " +
                                    "Сохранённые расписания появятся здесь.",
                            )
                        }
                    }
                } else {
                    item(key = "results-header") {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Результаты",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (state.isSearching) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                                    Text("Обновляем", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                Text(
                                    "${state.searchResults.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(
                        items = state.searchResults,
                        key = { "result_${it.type}_${it.id}" },
                    ) { target ->
                        SearchTargetRow(
                            target = target,
                            favorite = (target.type to target.id) in favorites,
                            onOpen = { openTarget(target) },
                            onToggleFavorite = { onToggleFavorite(target) },
                        )
                    }
                    if (!state.isSearching && state.searchResults.isEmpty()) {
                        item(key = "search-empty") {
                            val detail = when {
                                state.searchError != null -> state.searchError
                                query.length == 1 && state.searchType == null ->
                                    "Укажите категорию для поиска по одной букве " +
                                        "или введите ещё символ."
                                else -> "Попробуйте короткую часть названия, другую категорию " +
                                    "или проверьте раскладку клавиатуры."
                            }
                            SearchHint(title = "Совпадений нет", detail = detail.orEmpty())
                        }
                    }
                }
                item(key = "end-space") { Spacer(Modifier.height(24.dp)) }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Расписание",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.selected != null) {
                    TextButton(onClick = { searching = true }) {
                        Text("Сменить")
                    }
                }
            }
            Surface(
                onClick = { searching = true },
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .testTag("schedule-search-open"),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppIcon(AppIcons.Search, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.selected?.name ?: "Найти расписание",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (state.selected == null) FontWeight.Normal
                                else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.selected?.type?.displayName?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    AppIcon(AppIcons.Forward, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.selected == null && (state.favorites.isNotEmpty() ||
                    state.recentlyViewed.isNotEmpty())) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (state.favorites + state.recentlyViewed)
                        .distinctBy { it.type to it.id }.take(8).forEach { target ->
                            FilterChip(
                                selected = false,
                                onClick = { openTarget(target) },
                                label = { Text(target.name, maxLines = 1) },
                            )
                        }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ScheduleScreen(
                    state = state,
                    onEnsureAgendaWeek = onEnsureWeek,
                    onRefreshAgendaWeek = onRefreshWeek,
                    onRetryAgendaWeek = onRetryWeek,
                    onViewedAgendaWeek = {},
                    onAgendaScrollingChanged = {},
                    onLocateRoom = {},
                    todayJumpRequest = 0,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private val scheduleSearchFilters: List<Pair<String, ScheduleTargetType?>> = listOf(
    "Все" to null,
    "Группы" to ScheduleTargetType.GROUP,
    "Преподаватели" to ScheduleTargetType.LECTURER,
    "Аудитории" to ScheduleTargetType.ROOM,
    "Люди" to ScheduleTargetType.PERSON,
)

@Composable
private fun SearchSectionHeading(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text(count.toString(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchHint(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchTargetRow(
    target: ScheduleTarget,
    favorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val icon = when (target.type) {
        ScheduleTargetType.GROUP -> AppIcons.GroupList
        ScheduleTargetType.LECTURER, ScheduleTargetType.PERSON -> AppIcons.Person
        ScheduleTargetType.ROOM -> AppIcons.Location
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 18.dp, end = 9.dp, top = 7.dp, bottom = 7.dp)
            .testTag("schedule-target-result"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                AppIcon(icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Column(modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                target.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                (target.type.displayName +
                    if (target.description.isBlank()) "" else " · " + target.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleFavorite,
            modifier = Modifier.testTag("schedule-favorite-toggle")) {
            AppIcon(
                AppIcons.Favorite,
                contentDescription = if (favorite) "Удалить из избранного" else "В избранное",
                tint = if (favorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
            )
        }
    }
}
