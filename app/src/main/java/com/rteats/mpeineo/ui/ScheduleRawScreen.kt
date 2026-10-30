package com.rteats.mpeineo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.gson.GsonBuilder
import com.rteats.mpeineo.model.ScheduleTarget

/** A raw JSON reader with a small data target selector. */
@Composable
internal fun ScheduleRawScreen(
    state: MainUiState,
    onQueryChange: (String) -> Unit,
    onSelectTarget: (ScheduleTarget) -> Unit,
    onEnsureWeek: (Int) -> Unit,
    onRefreshWeek: (Int) -> Unit,
    onRetryWeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val gson = remember { GsonBuilder().setPrettyPrinting().create() }
    LaunchedEffect(state.selected?.id, state.selected?.type) { offset = 0 }
    LaunchedEffect(state.selected?.id, offset) { onEnsureWeek(offset) }
    val week = state.agendaWeeks[offset]
    val loading = offset in state.agendaLoadingOffsets
    val failed = offset in state.agendaFailedOffsets
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("schedule-raw"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onQueryChange,
            label = { Text("Группа / преподаватель / аудитория") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("schedule-target-query"),
        )
        if (state.searchQuery.trim().length >= 2) {
            if (state.isSearching) Text("Поиск...")
            state.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.searchResults.take(8).forEach { target ->
                Text(
                    target.type.displayName + ": " + target.name,
                    modifier = Modifier.fillMaxWidth().clickable {
                        onSelectTarget(target)
                        onQueryChange("")
                    }.padding(vertical = 8.dp).testTag("schedule-target-result"),
                )
            }
        }
        if (state.favorites.isNotEmpty()) {
            Text("Сохранённые расписания", style = MaterialTheme.typography.labelMedium)
            state.favorites.forEach { target ->
                Text(
                    target.type.displayName + ": " + target.name,
                    modifier = Modifier.fillMaxWidth().clickable { onSelectTarget(target) }
                        .padding(vertical = 6.dp),
                )
            }
        }
        HorizontalDivider()
        Text(
            state.selected?.let { it.type.displayName + ": " + it.name }
                ?: "Расписание не выбрано",
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.selected != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { offset-- }) { Text("← Неделя") }
                TextButton(onClick = { offset = 0 }) { Text("Текущая") }
                TextButton(onClick = { offset++ }) { Text("Неделя →") }
                TextButton(onClick = { onRefreshWeek(offset) }) { Text("Обновить") }
            }
            Text("Смещение недели: " + offset, style = MaterialTheme.typography.labelSmall)
            if (loading) Text("Загрузка данных...")
            if (failed) TextButton(onClick = { onRetryWeek(offset) }) {
                Text("Ошибка загрузки. Повторить")
            }
            SelectionContainer {
                Text(
                    text = week?.let(gson::toJson) ?: if (loading) "..." else "null",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().testTag("schedule-raw-json"),
                )
            }
        } else Text("Введите название, выберите результат и получите JSON.")
    }
}
