package com.rteats.mpeineo.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons

/** Material's contained full-screen search. No custom search geometry or overlay. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaterialSearchAppBar(
    context: SearchAppBarContext,
    state: SearchBarState,
    textFieldState: TextFieldState,
    onCloseSearch: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    onCompose: (() -> Unit)? = null,
    results: @Composable () -> Unit,
) {
    val colors = SearchBarDefaults.appBarWithSearchColors(
        searchBarColors = SearchBarDefaults.containedColors(state = state),
    )
    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = state,
            colors = colors.searchBarColors.inputFieldColors,
            onSearch = {},
            placeholder = { Text(context.placeholder) },
            leadingIcon = {
                if (state.currentValue == SearchBarValue.Expanded) {
                    IconButton(onClick = onCloseSearch) {
                        AppIcon(AppIcons.Back, contentDescription = "Закрыть поиск")
                    }
                } else AppIcon(AppIcons.Search, contentDescription = null)
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = {
                        textFieldState.setTextAndPlaceCursorAtEnd("")
                    }) {
                        AppIcon(AppIcons.ClearLog, contentDescription = "Очистить запрос")
                    }
                }
            },
            modifier = Modifier.testTag("global-search-input"),
        )
    }
    AppBarWithSearch(
        state = state,
        inputField = inputField,
        colors = colors,
        navigationIcon = {
            IconButton(onClick = onSettings, modifier = Modifier.testTag("service-settings")) {
                AppIcon(AppIcons.Settings, contentDescription = "Настройки")
            }
        },
        actions = {
            if (onRefresh != null) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !refreshing,
                    modifier = Modifier.testTag("schedule-week-refresh"),
                ) {
                    if (refreshing) LoadingIndicator(Modifier.size(24.dp))
                    else AppIcon(AppIcons.Refresh, contentDescription = "Обновить неделю")
                }
            }
            if (onCompose != null) {
                IconButton(onClick = onCompose, modifier = Modifier.testTag("mail-compose-fab")) {
                    AppIcon(AppIcons.Edit, contentDescription = "Написать письмо")
                }
            }
        },
        modifier = Modifier.testTag("search-app-bar"),
    )
    ExpandedFullScreenContainedSearchBar(
        state = state,
        inputField = inputField,
        colors = colors.searchBarColors,
    ) {
        results()
    }
}
