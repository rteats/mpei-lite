package com.rteats.mpeineo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import kotlinx.coroutines.launch

/** Search cached Inbox and Sent messages with Material's full-screen focused search. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MailSearchDialog(
    mail: MailUiState,
    onOpen: (SearchMailHit) -> Unit,
    onClose: () -> Unit,
) {
    val bar = rememberSearchBarState()
    val input = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    var hasExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bar.animateToExpanded() }
    LaunchedEffect(bar.currentValue) {
        if (bar.currentValue == SearchBarValue.Expanded) hasExpanded = true
        else if (hasExpanded) onClose()
    }
    val searchInput: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = input,
            searchBarState = bar,
            onSearch = { },
            placeholder = { Text("Поиск писем") },
            leadingIcon = {
                IconButton(onClick = onClose) {
                    AppIcon(AppIcons.Back, contentDescription = "Закрыть поиск")
                }
            },
            trailingIcon = {
                if (input.text.isNotEmpty()) {
                    IconButton(onClick = { input.setTextAndPlaceCursorAtEnd("") }) {
                        AppIcon(AppIcons.Close, contentDescription = "Очистить запрос")
                    }
                }
            },
            modifier = Modifier.testTag("mail-search-query"),
        )
    }
    SearchBar(
        state = bar,
        inputField = searchInput,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    ExpandedFullScreenSearchBar(state = bar, inputField = searchInput) {
        val query = input.text.toString().trim()
        val hits = GlobalSearchIndex.mailHits(query, mail)
        LazyColumn(
            modifier = Modifier.testTag("mail-search-results"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (query.isBlank()) {
                item {
                    Text(
                        "Недавние загруженные письма",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            items(
                items = hits,
                key = { "${it.folder.name}-${it.message.uidValidity}-${it.message.uid}" },
            ) { hit ->
                ListItem(
                    headlineContent = { Text(hit.message.subject.ifBlank { "Без темы" }) },
                    supportingContent = {
                        Text(
                            (if (hit.folder == com.rteats.mpeineo.data.OwaMailFolder.SENT)
                                "Отправленные · " else "Входящие · ") + hit.message.sender,
                        )
                    },
                    leadingContent = {
                        AppIcon(AppIcons.Mail, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(hit) }
                        .testTag("mail-search-result"),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            if (hits.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank()) "Загруженных писем пока нет."
                        else "Совпадений среди загруженных писем нет.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
