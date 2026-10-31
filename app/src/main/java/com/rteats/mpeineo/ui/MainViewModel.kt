package com.rteats.mpeineo.ui

import androidx.lifecycle.ViewModel
import com.rteats.mpeineo.widget.NextLessonWidgetProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rteats.mpeineo.AppContainer
import com.rteats.mpeineo.data.rankScheduleTargets
import com.rteats.mpeineo.data.shouldQueryScheduleServer
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import com.rteats.mpeineo.model.ScheduleWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val selected: ScheduleTarget? = null,
    val favorites: List<ScheduleTarget> = emptyList(),
    val recentlyViewed: List<ScheduleTarget> = emptyList(),
    val favoriteNames: Map<String, String> = emptyMap(),
    val agendaWeeks: Map<Int, ScheduleWeek> = emptyMap(),
    val agendaLoadingOffsets: Set<Int> = emptySet(),
    val agendaFailedOffsets: Set<Int> = emptySet(),
    val searchQuery: String = "",
    val searchType: ScheduleTargetType? = null,
    val searchResults: List<ScheduleTarget> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
)

/**
 * Saved agenda weeks and one loading/error set are the sole schedule data state.
 * The active week number remains local to ScheduleAgenda because it is gesture
 * and saveable UI state, not an independent network selection.
 */
internal fun MainUiState.shouldLoadAgendaWeek(offset: Int): Boolean =
    selected != null &&
        offset !in agendaWeeks &&
        offset !in agendaLoadingOffsets &&
        offset !in agendaFailedOffsets

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private val agendaJobs = mutableMapOf<Int, Job>()
    private var selectionVersion = 0L

    init {
        viewModelScope.launch {
            container.preferences.favorites.collect { favorites ->
                _state.update { it.copy(favorites = favorites) }
            }
        }
        viewModelScope.launch {
            container.preferences.favoriteNames.collect { names ->
                _state.update { it.copy(favoriteNames = names) }
            }
        }
        viewModelScope.launch {
            container.preferences.recentlyViewed.collect { recent ->
                _state.update { it.copy(recentlyViewed = recent) }
            }
        }
        viewModelScope.launch {
            val selected = container.preferences.selected.first()
            // Preferences loading may finish after the user has selected another
            // schedule; do not restore an obsolete selection in that case.
            if (selected != null && selectionVersion == 0L && state.value.selected == null) {
                _state.update { it.copy(selected = selected) }
                // Preserve the prior startup behavior: cached days immediately,
                // followed by one forced network refresh for the current week.
                refreshAgendaWeek(0)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _state.update {
            it.copy(
                searchQuery = query,
                searchError = null,
            )
        }

        if (query.trim().isEmpty()) {
            searchJob?.cancel()
            _state.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                )
            }
            return
        }

        scheduleSearch(delayMillis = 170)
    }

    fun setSearchType(type: ScheduleTargetType?) {
        _state.update { it.copy(searchType = type, searchError = null) }
        if (state.value.searchQuery.trim().isNotEmpty()) {
            scheduleSearch(delayMillis = 0)
        }
    }

    fun search() {
        val query = state.value.searchQuery.trim()
        if (query.isEmpty()) {
            searchJob?.cancel()
            _state.update {
                it.copy(
                    isSearching = false,
                    searchError = "Введите поисковый запрос",
                )
            }
            return
        }
        scheduleSearch(delayMillis = 0)
    }

    fun selectTarget(target: ScheduleTarget) {
        // Cancel in-flight agenda pages when selecting a different group/person/room.
        selectionVersion++
        agendaJobs.values.forEach { it.cancel() }
        agendaJobs.clear()
        _state.update {
            it.copy(
                selected = target,
                agendaWeeks = emptyMap(),
                agendaLoadingOffsets = emptySet(),
                agendaFailedOffsets = emptySet(),
            )
        }
        viewModelScope.launch {
            container.preferences.setSelected(target)
            NextLessonWidgetProvider.requestRefresh(container.appContext)
        }
        ensureAgendaWeek(0)
    }

    /**
     * Lazy agenda week request. Reuses saved weeks first and downloads only
     * weeks that actually enter the visible agenda. Failures require explicit
     * retry instead of creating infinite automatic retry loops during scrolling.
     */
    fun ensureAgendaWeek(offset: Int) {
        if (!state.value.shouldLoadAgendaWeek(offset)) return
        requestAgendaWeek(offset, forceNetwork = false)
    }

    fun retryAgendaWeek(offset: Int) {
        _state.update { it.copy(agendaFailedOffsets = it.agendaFailedOffsets - offset) }
        ensureAgendaWeek(offset)
    }

    fun refreshAgendaWeek(offset: Int) = requestAgendaWeek(offset, forceNetwork = true)

    /**
     * Single owner of all agenda loading, including the startup refresh.
     * An obsolete request cannot publish a week or clear its replacement's
     * loading state, even when a repository call ignores cancellation.
     */
    private fun requestAgendaWeek(offset: Int, forceNetwork: Boolean) {
        val target = state.value.selected ?: return
        if (!forceNetwork && !state.value.shouldLoadAgendaWeek(offset)) return
        val version = selectionVersion
        agendaJobs[offset]?.cancel()
        val weekStart = mondayFor(LocalDate.now()).plusWeeks(offset.toLong())
        _state.update {
            it.copy(
                agendaLoadingOffsets = it.agendaLoadingOffsets + offset,
                agendaFailedOffsets = it.agendaFailedOffsets - offset,
            )
        }
        container.diagnostics.log("SCHEDULE", "week request offset=$offset force=$forceNetwork")
        // Register before dispatch so even a synchronously completed cache hit
        // observes its own request identity.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val thisJob = currentCoroutineContext()[Job]
            fun isCurrentRequest(): Boolean {
                val selected = state.value.selected ?: return false
                return selectionVersion == version &&
                    agendaJobs[offset] === thisJob &&
                    selected.id == target.id && selected.type == target.type
            }

            try {
                if (forceNetwork) {
                    // A new app launch must display saved lessons immediately
                    // instead of waiting for a slow remote response.
                    val cached = container.repository.cachedWeek(target, weekStart)
                    if (cached != null && isCurrentRequest()) {
                        _state.update {
                            it.copy(agendaWeeks = it.agendaWeeks + (offset to cached.week))
                        }
                        if (offset in 0..1) {
                            NextLessonWidgetProvider.requestRefresh(container.appContext)
                        }
                    }
                }
                val result = container.repository.loadWeek(
                    target = target,
                    weekStart = weekStart,
                    forceNetwork = forceNetwork,
                )
                if (isCurrentRequest()) {
                    container.diagnostics.log("SCHEDULE",
                        "week received offset=$offset source=${result.source} days=${result.week.days.size}")
                    _state.update {
                        it.copy(
                            agendaWeeks = it.agendaWeeks + (offset to result.week),
                            agendaFailedOffsets = it.agendaFailedOffsets - offset,
                        )
                    }
                    if (offset in 0..1) {
                        NextLessonWidgetProvider.requestRefresh(container.appContext)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isCurrentRequest()) {
                    container.diagnostics.log("SCHEDULE",
                        "week error offset=$offset type=${error.javaClass.simpleName} " +
                            "detail=${error.message.orEmpty().take(160)}")
                    _state.update {
                        it.copy(agendaFailedOffsets = it.agendaFailedOffsets + offset)
                    }
                }
            } finally {
                if (isCurrentRequest()) {
                    agendaJobs.remove(offset)
                    _state.update {
                        it.copy(agendaLoadingOffsets = it.agendaLoadingOffsets - offset)
                    }
                }
            }
        }
        agendaJobs[offset] = job
        job.start()
    }

    fun toggleFavorite(target: ScheduleTarget) {
        viewModelScope.launch {
            container.preferences.toggleFavorite(target)
        }
    }

    fun renameFavorite(target: ScheduleTarget, name: String) {
        viewModelScope.launch {
            container.preferences.renameFavorite(target, name)
        }
    }

    /**
     * Cache-first search: local results appear before the remote debounce.
     * One owner job cancels the previous request and ignores obsolete responses.
     */
    private fun scheduleSearch(delayMillis: Long) {
        searchJob?.cancel()
        val query = state.value.searchQuery.trim()
        val type = state.value.searchType
        if (query.isEmpty()) return
        searchJob = viewModelScope.launch {
            val remoteAllowed = shouldQueryScheduleServer(query, type)
            fun current() = state.value.searchQuery.trim() == query &&
                state.value.searchType == type

            val local = try {
                container.repository.cachedSearch(query, type)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            if (!current()) return@launch
            val shortcuts = state.value.favorites + state.value.recentlyViewed
            val offline = rankScheduleTargets(
                local + shortcuts, query, type,
                favorites = state.value.favorites,
                recent = state.value.recentlyViewed,
            )
            _state.update {
                it.copy(searchResults = offline, isSearching = remoteAllowed, searchError = null)
            }
            if (!remoteAllowed) return@launch

            if (delayMillis > 0) delay(delayMillis)
            if (!current()) return@launch
            container.diagnostics.log(
                "SCHEDULE",
                "lookup type=${type?.apiName ?: "all"} length=${query.length} cached=${offline.size}",
            )
            try {
                val results = container.repository.search(query, type)
                if (current()) {
                    val ranked = rankScheduleTargets(
                        results + offline, query, type,
                        favorites = state.value.favorites,
                        recent = state.value.recentlyViewed,
                        includeUnmatched = true,
                    )
                    container.diagnostics.log("SCHEDULE",
                        "lookup remote=${results.size} visible=${ranked.size}")
                    _state.update {
                        it.copy(searchResults = ranked, isSearching = false, searchError = null)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (current()) {
                    container.diagnostics.log(
                        "SCHEDULE",
                        "lookup failed type=${error.javaClass.simpleName}",
                    )
                    _state.update {
                        it.copy(
                            searchResults = offline,
                            isSearching = false,
                            searchError = if (offline.isEmpty()) {
                                "Не удалось получить результаты с сервера."
                            } else null,
                        )
                    }
                }
            }
        }
    }

    private fun mondayFor(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(container) as T
                }
            }
    }
}
