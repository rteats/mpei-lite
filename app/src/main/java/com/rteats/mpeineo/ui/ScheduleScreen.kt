package com.rteats.mpeineo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rteats.mpeineo.AppIcon
import com.rteats.mpeineo.AppIcons
import com.rteats.mpeineo.MpeiNeoApplication
import com.rteats.mpeineo.model.Lesson
import com.rteats.mpeineo.model.ScheduleTarget
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val timetableWeekFormat = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
private val timetableLessonDateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))
private val firstTimetableWeek = timetableWeekStart(timetableFirstDay)
private val timetableWeekCount = timetableWeekOffset(firstTimetableWeek, timetableLastDay) + 1

private val timetableSlotSaver = listSaver<TimetableSlot?, String>(
    save = { slot ->
        slot?.let { (date, lessons) ->
            listOf(date.toString()) + lessons.flatMap { lesson ->
                listOf(lesson.name, lesson.kind, lesson.startTime, lesson.endTime,
                    lesson.place, lesson.lecturer, lesson.groups)
            }
        }.orEmpty()
    },
    restore = { parts ->
        parts.takeIf { it.size > 1 && (it.size - 1) % 7 == 0 }?.let {
            TimetableSlot(LocalDate.parse(it.first()), it.drop(1).chunked(7).map { lesson ->
                Lesson(lesson[0], lesson[1], lesson[2], lesson[3], lesson[4], lesson[5], lesson[6])
            })
        }
    },
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleScreen(
    state: MainUiState,
    onEnsureAgendaWeek: (Int) -> Unit,
    onRefreshAgendaWeek: (Int) -> Unit,
    onRetryAgendaWeek: (Int) -> Unit,
    onAliasChanged: (String, String) -> Unit,
    todayJumpRequest: Int,
    active: Boolean = true,
    startDate: LocalDate? = null,
    onFindTeachers: (suspend (String) -> List<ScheduleTarget>)? = null,
    onOpenTeacher: (ScheduleTarget, LocalDate) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (state.selected == null) {
        Box(modifier.fillMaxSize().padding(AppSpacing.md), contentAlignment = Alignment.Center) {
            Card {
                Column(
                    Modifier.padding(AppSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                ) {
                    Text("Выберите расписание", style = MaterialTheme.typography.titleLarge)
                    Text("Выберите избранное или найдите группу, преподавателя или аудиторию.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        return
    }

    val clock = rememberMpeiNow()
    val today by remember(clock) { derivedStateOf { clock.value.toLocalDate() } }
    val initialIndex = timetableIndexOf(startDate ?: today).coerceIn(0, timetableDayCount - 1)
    val dayCache = remember { LazyLayoutCacheWindow(aheadFraction = .75f, behindFraction = .5f) }
    val list = rememberLazyListState(cacheWindow = dayCache, initialFirstVisibleItemIndex = initialIndex)
    val anchor by remember(list) {
        derivedStateOf {
            list.layoutInfo.visibleItemsInfo.minByOrNull { abs(it.offset) }?.index
                ?: list.firstVisibleItemIndex
        }
    }
    val reference = state.agendaReferenceMonday
    val week by remember(list) {
        derivedStateOf(structuralEqualityPolicy()) { timetableWeekStart(timetableDayAt(anchor)) }
    }
    val weekOffset = timetableWeekOffset(reference, week)
    val refreshing = weekOffset in state.agendaLoadingOffsets
    val refreshWeek = { onRefreshAgendaWeek(weekOffset) }
    val profiler = (LocalContext.current.applicationContext as? MpeiNeoApplication)
        ?.container?.uiFrameProfiler
    var dayPicker by rememberSaveable { mutableStateOf(false) }
    var selectedSlot by rememberSaveable(stateSaver = timetableSlotSaver) {
        mutableStateOf<TimetableSlot?>(null)
    }
    var lessonIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var aliasDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var lastJumpRequest by remember { mutableStateOf(todayJumpRequest) }

    fun jumpTo(date: LocalDate) {
        list.requestScrollToItem(timetableIndexOf(date).coerceIn(0, timetableDayCount - 1))
    }

    LaunchedEffect(active, reference, state.selected) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            val items = list.layoutInfo.visibleItemsInfo
            val first = items.firstOrNull()?.index ?: list.firstVisibleItemIndex
            val last = items.lastOrNull()?.index ?: first
            timetableWeekOffset(reference, timetableDayAt(first))..
                timetableWeekOffset(reference, timetableDayAt(last))
        }.collect { offsets ->
            for (offset in (offsets.first - 1)..(offsets.last + 1)) onEnsureAgendaWeek(offset)
        }
    }
    LaunchedEffect(active, weekOffset) {
        if (active) profiler?.markInteraction("WEEK_CHANGE")
    }
    LaunchedEffect(active, todayJumpRequest) {
        if (active && todayJumpRequest != lastJumpRequest) {
            selectedSlot = null
            lessonIndex = null
            aliasDraft = null
            dayPicker = false
            jumpTo(today)
        }
        lastJumpRequest = todayJumpRequest
    }

    Column(modifier.fillMaxSize().testTag("schedule-timetable")) {
        TimetableWeekBar(
            week = week,
            onMove = { date ->
                list.scrollToWeek(timetableIndexOf(date).coerceIn(0, timetableDayCount - 1))
            },
            onPickDay = { dayPicker = true },
            onRefresh = refreshWeek.takeIf { active && !refreshing },
            active = active,
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = refreshWeek,
            enabled = active,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            ScheduleAgenda(
                today = today,
                weeks = state.agendaWeeks,
                aliases = state.lessonAliases,
                listState = list,
                onSlot = { slot ->
                    if (active) {
                        lessonIndex = 0.takeIf { slot.lessons.size == 1 }
                        selectedSlot = slot
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            val failed = weekOffset in state.agendaFailedOffsets
            val status = when {
                failed -> "Не удалось загрузить неделю"
                !refreshing && state.agendaWeeks[weekOffset]?.days?.all { it.lessons.isEmpty() } == true ->
                    "На этой неделе нет занятий"
                else -> null
            }
            if (status != null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(AppSpacing.sm),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(status, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)
                        if (failed) {
                            TextButton(onClick = { onRetryAgendaWeek(weekOffset) }) { Text("Повторить") }
                        }
                    }
                }
            }
        }
    }

    if (dayPicker && active) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = timetableDayAt(anchor).atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli(),
            yearRange = timetableYears,
        )
        DatePickerDialog(
            onDismissRequest = { dayPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        jumpTo(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    dayPicker = false
                }) { Text("Перейти") }
            },
            dismissButton = {
                TextButton(onClick = { jumpTo(today); dayPicker = false }) { Text("Сегодня") }
            },
        ) { DatePicker(state = picker) }
    }

    selectedSlot?.takeIf { active }?.let { slot ->
        TimetableLessonSheet(
            slot = slot,
            lessonIndex = lessonIndex,
            onSelectLesson = { lessonIndex = it },
            aliases = state.lessonAliases,
            aliasDraft = aliasDraft,
            onEditAlias = { aliasDraft = it },
            onAliasChanged = onAliasChanged,
            onFindTeachers = onFindTeachers,
            onTeacher = { onOpenTeacher(it, slot.date) },
            onDismiss = {
                selectedSlot = null
                lessonIndex = null
                aliasDraft = null
            },
        )
    }
}

private suspend fun LazyListState.scrollToWeek(dayIndex: Int) {
    val dayWidth = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: run {
        requestScrollToItem(dayIndex)
        return
    }
    scroll {
        var remaining = (dayIndex - firstVisibleItemIndex) * dayWidth - firstVisibleItemScrollOffset
        // A late frame should never make the grid compose several new days to catch up.
        while (remaining != 0) {
            withFrameNanos { }
            val moved = scrollBy(remaining.coerceIn(-dayWidth, dayWidth).toFloat()).roundToInt()
            if (moved == 0) break
            remaining -= moved
        }
    }
}

@Composable
private fun TimetableWeekBar(
    week: LocalDate,
    onMove: suspend (LocalDate) -> Unit,
    onPickDay: () -> Unit,
    onRefresh: (() -> Unit)?,
    active: Boolean,
) {
    val weekPage = timetableWeekOffset(firstTimetableWeek, week)
    // The day list owns restoration. Keep its heading pager alive across week changes.
    val pager = remember { PagerState(weekPage, pageCount = { timetableWeekCount }) }
    val gridPage by rememberUpdatedState(weekPage)
    val moveToWeek by rememberUpdatedState(onMove)
    val scope = rememberCoroutineScope()
    var movingGrid by remember { mutableStateOf(false) }
    LaunchedEffect(weekPage, movingGrid) {
        if (!movingGrid && pager.settledPage != weekPage) pager.requestScrollToPage(weekPage)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collectLatest { page ->
            if (page == gridPage) return@collectLatest
            movingGrid = true
            try {
                moveToWeek(firstTimetableWeek.plusWeeks(page.toLong()))
            } finally {
                movingGrid = false
            }
        }
    }

    HorizontalPager(
        state = pager,
        beyondViewportPageCount = 1,
        userScrollEnabled = active,
        modifier = Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Horizontal))
            .testTag("schedule-week-bar"),
    ) {
        val page = it
        val monday = firstTimetableWeek.plusWeeks(page.toLong())
        val isSettled = page == pager.settledPage
        TextButton(
            onClick = onPickDay,
            enabled = active,
            modifier = Modifier.fillMaxWidth().then(if (isSettled) {
                Modifier.testTag("schedule-jump-date").semantics {
                    customActions = buildList {
                        if (page > 0) add(CustomAccessibilityAction("Предыдущая неделя") {
                            scope.launch { pager.animateScrollToPage(page - 1) }
                            true
                        })
                        if (page < timetableWeekCount - 1) add(CustomAccessibilityAction("Следующая неделя") {
                            scope.launch { pager.animateScrollToPage(page + 1) }
                            true
                        })
                        if (onRefresh != null) add(CustomAccessibilityAction("Обновить неделю") {
                            onRefresh()
                            true
                        })
                    }
                }
            } else Modifier),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${monday.format(timetableWeekFormat)} — ${monday.plusDays(6).format(timetableWeekFormat)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("${studyWeekNumber(monday)}-я учебная неделя", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimetableLessonSheet(
    slot: TimetableSlot,
    lessonIndex: Int?,
    onSelectLesson: (Int?) -> Unit,
    aliases: Map<String, String>,
    aliasDraft: String?,
    onEditAlias: (String?) -> Unit,
    onAliasChanged: (String, String) -> Unit,
    onFindTeachers: (suspend (String) -> List<ScheduleTarget>)?,
    onTeacher: (ScheduleTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()

    fun openTeacher(target: ScheduleTarget) {
        scope.launch {
            sheet.hide()
            if (!sheet.isVisible) {
                onDismiss()
                onTeacher(target)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        val lesson = lessonIndex?.let(slot.lessons::getOrNull)
        val slotScroll = rememberScrollState()
        val detailsScroll = key(lessonIndex) { rememberScrollState() }
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(if (lesson == null) slotScroll else detailsScroll)
                .padding(start = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.lg)
                .testTag(if (lesson == null) "schedule-slot-lessons" else "schedule-lesson-details"),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            if (lesson == null) {
                Text("Занятия в это время", style = MaterialTheme.typography.headlineSmall)
                Text(slot.date.format(timetableLessonDateFormat), style = MaterialTheme.typography.titleSmall)
                slot.lessons.forEachIndexed { index, candidate ->
                    val type = timetableLessonType(candidate.kind)
                    ListItem(
                        onClick = { onSelectLesson(index) },
                        supportingContent = {
                            Text(listOf(type.label(candidate.kind), candidate.place, candidate.lecturer, candidate.groups)
                                .filter(String::isNotBlank).joinToString(" · "))
                        },
                        leadingContent = { AppIcon(type.icon, contentDescription = null) },
                    ) { Text(candidate.name) }
                }
                return@Column
            }
            key(lessonIndex) {
                TimetableLessonDetails(
                    lesson = lesson,
                    date = slot.date,
                    alias = aliases[timetableAliasKey(lesson.name)].orEmpty(),
                    aliasDraft = aliasDraft,
                    onEditAlias = onEditAlias,
                    onAliasChanged = { onAliasChanged(lesson.name, it) },
                    onFindTeachers = onFindTeachers,
                    onTeacher = ::openTeacher,
                    onReturnToList = if (slot.lessons.size > 1) ({ onSelectLesson(null) }) else null,
                )
            }
        }
    }
}

@Composable
private fun TimetableLessonDetails(
    lesson: Lesson,
    date: LocalDate,
    alias: String,
    aliasDraft: String?,
    onEditAlias: (String?) -> Unit,
    onAliasChanged: (String) -> Unit,
    onFindTeachers: (suspend (String) -> List<ScheduleTarget>)?,
    onTeacher: (ScheduleTarget) -> Unit,
    onReturnToList: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var searching by remember { mutableStateOf(false) }
    var teacherCandidates by remember { mutableStateOf<List<ScheduleTarget>?>(null) }
    if (onReturnToList != null) {
        TextButton(onClick = {
            focus.clearFocus()
            keyboard?.hide()
            onEditAlias(null)
            onReturnToList()
        }) { Text("К списку занятий") }
    }
    Text(lesson.name, style = MaterialTheme.typography.headlineSmall)
    Text(date.format(timetableLessonDateFormat), style = MaterialTheme.typography.titleSmall)
    val details = listOf(
        "Время" to listOf(lesson.startTime, lesson.endTime).filter(String::isNotBlank).joinToString("–"),
        "Тип" to lesson.kind,
        "Аудитория" to lesson.place,
        "Преподаватель" to lesson.lecturer,
        "Группы" to lesson.groups,
    )
    details.filter { (_, value) -> value.isNotBlank() }.forEach { (label, value) ->
        Text("$label · $value", style = MaterialTheme.typography.bodyLarge)
    }
    if (aliasDraft == null) {
        TextButton(
            onClick = {
                onEditAlias(alias.ifBlank { timetableLessonLabel(lesson.name, emptyMap()) })
            },
            modifier = Modifier.testTag("schedule-edit-alias"),
        ) { Text("Изменить сокращение") }
    } else {
        OutlinedTextField(
            value = aliasDraft,
            onValueChange = { onEditAlias(it.take(32)) },
            singleLine = true,
            label = { Text("Сокращение") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("schedule-alias-input"),
        )
        Row {
            TextButton(onClick = {
                onAliasChanged(aliasDraft)
                focus.clearFocus()
                keyboard?.hide()
                onEditAlias(null)
            }) { Text("Сохранить") }
            TextButton(onClick = {
                onAliasChanged("")
                focus.clearFocus()
                keyboard?.hide()
                onEditAlias(null)
            }) { Text("Сбросить") }
        }
    }
    if (onFindTeachers != null && lesson.lecturer.isNotBlank()) {
        TextButton(
            enabled = !searching,
            onClick = {
                searching = true
                teacherCandidates = null
                scope.launch {
                    try {
                        val results = onFindTeachers(lesson.lecturer)
                        val only = results.singleOrNull()
                        if (only != null) onTeacher(only) else teacherCandidates = results
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        teacherCandidates = emptyList()
                    } finally {
                        searching = false
                    }
                }
            },
            modifier = Modifier.testTag("schedule-open-teacher"),
        ) { Text("Расписание преподавателя") }
        if (searching) CircularProgressIndicator()
        teacherCandidates?.let { candidates ->
            if (candidates.isEmpty()) {
                Text("Расписание преподавателя не найдено", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Выберите преподавателя", style = MaterialTheme.typography.titleSmall)
                candidates.forEach { target ->
                    TextButton(onClick = { onTeacher(target) }) {
                        Text(listOf(target.name, target.description).filter(String::isNotBlank)
                            .joinToString(" · "))
                    }
                }
            }
        }
    }
}
