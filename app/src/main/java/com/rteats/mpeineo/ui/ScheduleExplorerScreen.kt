package com.rteats.mpeineo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import kotlinx.coroutines.launch

/**
 * The schedule uses the same Material 3 search expansion as mail. The agenda
 * stays mounted behind the full-screen results so it is ready on collapse.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onSettings: () -> Unit = {},
    reselectRequest: Int = 0,
    modifier: Modifier = Modifier,
) {
    val searchBar = rememberSearchBarState()
    val searchText = rememberTextFieldState(initialText = state.searchQuery)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val agendaStateHolder = rememberSaveableStateHolder()
    val query = state.searchQuery.trim()
    val favorites = remember(state.favorites) { state.favorites.map { it.type to it.id }.toSet() }
    val expanded = searchBar.currentValue == SearchBarValue.Expanded

    LaunchedEffect(searchText.text) {
        val text = searchText.text.toString()
        if (text != state.searchQuery) onQueryChange(text)
    }

    fun closeSearch() {
        focusManager.clearFocus()
        keyboard?.hide()
        scope.launch { searchBar.animateToCollapsed() }
    }

    fun openTarget(target: ScheduleTarget) {
        focusManager.clearFocus()
        keyboard?.hide()
        scope.launch {
            searchBar.animateToCollapsed()
            onSelectTarget(target)
            searchText.setTextAndPlaceCursorAtEnd("")
            onQueryChange("")
        }
    }

    var agendaJumpRequest by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var lastReselect by remember { androidx.compose.runtime.mutableIntStateOf(reselectRequest) }
    LaunchedEffect(reselectRequest) {
        if (reselectRequest != lastReselect) {
            agendaJumpRequest++
            closeSearch()
        }
        lastReselect = reselectRequest
    }

    val searchInput: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = searchText,
            searchBarState = searchBar,
            onSearch = {
                focusManager.clearFocus()
                keyboard?.hide()
            },
            placeholder = {
                Text(
                    if (expanded) "Группа, преподаватель или аудитория"
                    else state.selected?.let { "${it.name} · ${it.type.displayName}" }
                        ?: "Найти расписание",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                if (expanded) {
                    IconButton(onClick = ::closeSearch) {
                        AppIcon(AppIcons.Back, contentDescription = "Закрыть поиск")
                    }
                } else AppIcon(AppIcons.Search, contentDescription = null)
            },
            trailingIcon = {
                if (expanded) {
                    if (searchText.text.isNotEmpty()) {
                        IconButton(onClick = {
                            searchText.setTextAndPlaceCursorAtEnd("")
                        }) { AppIcon(AppIcons.Close, contentDescription = "Очистить запрос") }
                    }
                } else {
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.testTag("schedule-open-settings"),
                    ) { AppIcon(AppIcons.Settings, contentDescription = "Настройки") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Keep the last query for reopening search, while the collapsed
            // field continues to identify the timetable currently on screen.
            outputTransformation = if (!expanded && state.selected != null &&
                searchText.text.isNotEmpty()) {
                val label = "${state.selected.name} · ${state.selected.type.displayName}"
                OutputTransformation { replace(0, length, label) }
            } else null,
            modifier = Modifier.testTag("schedule-target-query"),
        )
    }

    Box(
        modifier = modifier.fillMaxSize().testTag("schedule-explorer"),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopSearchBar(
                state = searchBar,
                inputField = searchInput,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.fillMaxWidth().testTag("schedule-search-open"),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                agendaStateHolder.SaveableStateProvider("schedule-agenda") {
                    ScheduleScreen(
                        state = state,
                        onEnsureAgendaWeek = onEnsureWeek,
                        onRefreshAgendaWeek = onRefreshWeek,
                        onRetryAgendaWeek = onRetryWeek,
                        onViewedAgendaWeek = {},
                        onAgendaScrollingChanged = {},
                        onLocateRoom = {},
                        todayJumpRequest = agendaJumpRequest,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        ExpandedFullScreenSearchBar(state = searchBar, inputField = searchInput) {
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
        }
    }
}

private val scheduleSearchFilters: List<Pair<String, ScheduleTargetType?>> = listOf(
    "Все" to null,
    "Группы" to ScheduleTargetType.GROUP,
    "Преподаватели" to ScheduleTargetType.LECTURER,
    "Аудитории" to ScheduleTargetType.ROOM,
    "Личное" to ScheduleTargetType.PERSON,
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
                if (favorite) AppIcons.FavoriteFilled else AppIcons.Favorite,
                contentDescription = if (favorite) "Удалить из избранного" else "В избранное",
                tint = if (favorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
            )
        }
    }
}
