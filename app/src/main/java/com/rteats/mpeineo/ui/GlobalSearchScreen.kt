package com.rteats.mpeineo.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.data.MailSummary
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.Lesson
import com.rteats.mpeineo.model.displayName
import com.rteats.mpeineo.model.favoriteKey
import org.json.JSONArray

/** The actual lesson and its week/day, rather than only its parent schedule. */
internal data class ScheduleLessonHit(val weekOffset: Int, val date: String, val lesson: Lesson)

/** Results are computed from cached mail and grades and existing Schedule target search. */
internal object GlobalSearchIndex {
    fun mail(query: String, items: List<MailSummary>): List<MailSummary> =
        items.filter { it.sender.contains(query, true) || it.subject.contains(query, true) }.take(20)

    fun disciplines(query: String, items: List<BarsDiscipline>): List<BarsDiscipline> =
        items.filter {
            it.disciplineName.contains(query, true) || it.personName.contains(query, true)
        }.take(20)

    fun lessons(query: String, state: MainUiState): List<ScheduleLessonHit> =
        state.agendaWeeks.entries.asSequence().flatMap { (week, agenda) ->
            agenda.days.asSequence().flatMap { day ->
                day.lessons.asSequence().filter { lesson ->
                    lesson.name.contains(query, true) || lesson.place.contains(query, true) ||
                        lesson.lecturer.contains(query, true)
                }.map { ScheduleLessonHit(week, day.date, it) }
            }
        }.distinctBy { Triple(it.date, it.lesson.name, it.lesson.startTime) }.take(8).toList()

    fun targets(query: String, state: MainUiState): List<ScheduleTarget> =
        (state.favorites + state.searchResults + listOfNotNull(state.selected))
            .distinctBy { it.favoriteKey() }
            .filter {
                it.name.contains(query, true) || it.description.contains(query, true) ||
                    it.displayName(state.favoriteNames).contains(query, true)
            }.take(20)
}

/** Clearable local query history. Does not index or persist mail bodies. */
internal class GlobalSearchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("superapp_search_v1", Context.MODE_PRIVATE)

    fun load(): List<String> = runCatching {
        val json = JSONArray(prefs.getString("queries", "[]") ?: "[]")
        (0 until json.length()).mapNotNull {
            json.optString(it).takeIf(String::isNotBlank)
        }.distinct().take(12)
    }.getOrDefault(emptyList())

    fun record(query: String): List<String> {
        val text = query.trim().take(100)
        if (text.isEmpty()) return load()
        return save(listOf(text) + load().filterNot { it.equals(text, true) })
    }
    fun remove(query: String): List<String> = save(load().filterNot { it == query })
    fun clear(): List<String> = save(emptyList())
    private fun save(values: List<String>): List<String> {
        val next = values.take(12)
        val json = JSONArray()
        next.forEach { json.put(it) }
        prefs.edit().putString("queries", json.toString()).apply()
        return next
    }
}

@Composable
internal fun GlobalSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    history: List<String>,
    onHistorySelected: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    schedule: MainUiState,
    mail: MailUiState,
    bars: BarsUiState,
    onSchedule: (ScheduleTarget) -> Unit,
    onLesson: (ScheduleLessonHit) -> Unit,
    onMail: (MailSummary) -> Unit,
    onDiscipline: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val trimmed = query.trim()
    val searching = trimmed.isNotBlank()
    val targets = if (searching) GlobalSearchIndex.targets(trimmed, schedule)
        else schedule.favorites.take(5)
    val disciplines = if (searching) GlobalSearchIndex.disciplines(trimmed, bars.disciplines)
        else bars.disciplines.take(4)
    val messages = if (searching) GlobalSearchIndex.mail(trimmed, mail.items)
        else mail.items.take(4)
    val lessons = if (searching && schedule.selected != null)
        GlobalSearchIndex.lessons(trimmed, schedule) else emptyList()

    Surface(
        modifier = Modifier.fillMaxSize().testTag("global-search-screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    AppIcon(AppIcons.Back, contentDescription = "Закрыть поиск")
                }
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(focus)
                        .testTag("global-search-input"),
                    placeholder = { Text("Поиск по МЭИ") },
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSubmit()
                        keyboard?.hide()
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    trailingIcon = if (query.isNotBlank()) {{
                        IconButton(onClick = { onQueryChange("") }) {
                            AppIcon(AppIcons.ClearLog, contentDescription = "Очистить запрос")
                        }
                    }} else null,
                )
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (!searching && history.isNotEmpty()) {
                    item { SearchSection("Недавние запросы", "Очистить", onClearHistory) }
                    items(history, key = { "history-" + it }) { entry ->
                        ListItem(
                            modifier = Modifier.clickable { onHistorySelected(entry) },
                            leadingContent = { AppIcon(AppIcons.Refresh, null) },
                            headlineContent = { Text(entry, maxLines = 1) },
                            trailingContent = {
                                IconButton(onClick = { onRemoveHistory(entry) }) {
                                    AppIcon(AppIcons.ClearLog, "Удалить запрос")
                                }
                            },
                        )
                    }
                }
                if (targets.isNotEmpty()) {
                    item { SearchSection(if (searching) "Расписание" else "Избранные расписания") }
                    items(targets, key = { "schedule-" + it.favoriteKey() }) { target ->
                        SearchEntry(AppIcons.Schedule,
                            target.displayName(schedule.favoriteNames),
                            target.type.displayName + " · " + target.description,
                        ) { onSchedule(target) }
                    }
                }
                if (lessons.isNotEmpty()) {
                    item { SearchSection("Пары в загруженном расписании") }
                    items(lessons, key = { "lesson-" + it.date + it.lesson.name + it.lesson.startTime }) { hit ->
                        SearchEntry(AppIcons.Schedule, hit.lesson.name,
                            hit.date + " · " + hit.lesson.startTime + " · " + hit.lesson.place) {
                            onLesson(hit)
                        }
                    }
                }
                if (disciplines.isNotEmpty()) {
                    item { SearchSection("Дисциплины БАРС") }
                    items(disciplines, key = { "bars-" + it.disciplineName }) { discipline ->
                        SearchEntry(AppIcons.Bars, discipline.disciplineName,
                            discipline.personName.ifBlank { "Оценки БАРС" }) {
                            onDiscipline(discipline.disciplineName)
                        }
                    }
                }
                if (messages.isNotEmpty()) {
                    item { SearchSection(if (searching) "Почта · загруженные письма" else "Последние письма") }
                    items(messages, key = { "mail-" + it.uidValidity + "-" + it.uid }) { item ->
                        SearchEntry(AppIcons.Mail, item.subject.ifBlank { "Без темы" },
                            item.sender) { onMail(item) }
                    }
                }
                if (searching && schedule.isSearching) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                        }
                    }
                }
                if (searching && targets.isEmpty() && lessons.isEmpty() &&
                    disciplines.isEmpty() && messages.isEmpty() && !schedule.isSearching) {
                    item {
                        Text(
                            "Совпадений нет. Почта и БАРС ищутся среди сохранённых данных.",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!searching && targets.isEmpty() && disciplines.isEmpty() && history.isEmpty()) {
                    item {
                        Text("Найдите группу, преподавателя, аудиторию, дисциплину или письмо.",
                            Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSection(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun SearchEntry(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = { AppIcon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}
