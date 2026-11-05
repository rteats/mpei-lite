package com.rteats.mpeineo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.data.OwaMailFolder
import kotlinx.coroutines.launch

/**
 * Material 3 pinned search app bar with a single shared SearchBarState.
 * Its collapsed field is the Mail app bar; the expanded view searches both
 * cached folders. Account actions stay in the trailing overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MailSearchAppBar(
    mail: MailUiState,
    onOpen: (SearchMailHit) -> Unit,
    onOpenPortal: () -> Unit,
    onLogout: () -> Unit,
    onSettings: () -> Unit = {},
) {
    val bar = rememberSearchBarState()
    val input = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val expanded = bar.currentValue == SearchBarValue.Expanded

    val searchInput: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = input,
            searchBarState = bar,
            onSearch = { },
            placeholder = { Text("Поиск по почте") },
            leadingIcon = {
                if (expanded) {
                    IconButton(onClick = { scope.launch { bar.animateToCollapsed() } }) {
                        AppIcon(AppIcons.Back, contentDescription = "Закрыть поиск")
                    }
                } else {
                    AppIcon(AppIcons.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (expanded) {
                    if (input.text.isNotEmpty()) {
                        IconButton(onClick = { input.setTextAndPlaceCursorAtEnd("") }) {
                            AppIcon(AppIcons.Close, contentDescription = "Очистить запрос")
                        }
                    }
                } else {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("mail-actions-overflow"),
                        ) {
                            AppIcon(AppIcons.Overflow, contentDescription = "Действия почты")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Настройки") },
                                leadingIcon = { AppIcon(AppIcons.Settings, contentDescription = null) },
                                onClick = { menuExpanded = false; onSettings() },
                                modifier = Modifier.testTag("mail-open-settings"),
                            )
                            DropdownMenuItem(
                                text = { Text("Веб-версия") },
                                leadingIcon = {
                                    AppIcon(AppIcons.OpenPortal, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onOpenPortal()
                                },
                                modifier = Modifier.testTag("portal-header-ОСЭП"),
                            )
                            DropdownMenuItem(
                                text = { Text("Выйти из аккаунта") },
                                leadingIcon = {
                                    AppIcon(AppIcons.Logout, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    confirmLogout = true
                                },
                                modifier = Modifier.testTag("mail-logout-open"),
                            )
                        }
                    }
                }
            },
            modifier = Modifier.testTag("mail-search-open"),
        )
    }

    TopSearchBar(
        state = bar,
        inputField = searchInput,
        modifier = Modifier.fillMaxWidth().testTag("mail-search-app-bar"),
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
                val correspondent = if (hit.folder == OwaMailFolder.SENT) {
                    hit.message.recipients.orEmpty().joinToString(", ")
                        .ifBlank { hit.message.sender }
                } else hit.message.sender
                ListItem(
                    headlineContent = { Text(hit.message.subject.ifBlank { "Без темы" }) },
                    supportingContent = {
                        Text(
                            (if (hit.folder == OwaMailFolder.SENT)
                                "Отправленные · " else "Входящие · ") + correspondent,
                        )
                    },
                    leadingContent = { AppIcon(AppIcons.Mail, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            bar.animateToCollapsed()
                            onOpen(hit)
                        }
                    }.testTag("mail-search-result"),
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
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из ОСЭП?") },
            text = {
                Text("Сохранённые письма, сессия и данные для автоматического входа будут удалены.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    },
                    modifier = Modifier.testTag("mail-logout-confirm"),
                ) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Отмена") }
            },
        )
    }
}
