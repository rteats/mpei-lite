package com.rteats.mpeineo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rteats.mpeineo.AppContainer
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

        if (query.trim().length < 2) {
            searchJob?.cancel()
            _state.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                )
            }
            return
        }

        scheduleSearch(delayMillis = 200)
    }

    fun setSearchType(type: ScheduleTargetType?) {
        _state.update { it.copy(searchType = type, searchError = null) }
        if (state.value.searchQuery.trim().length >= 2) {
            scheduleSearch(delayMillis = 0)
        }
    }

    fun search() {
        val query = state.value.searchQuery.trim()
        if (query.length < 2) {
            searchJob?.cancel()
            _state.update {
                it.copy(
                    isSearching = false,
                    searchError = "Введите минимум 2 символа",
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
                    }
                }
                val result = container.repository.loadWeek(
                    target = target,
                    weekStart = weekStart,
                    forceNetwork = forceNetwork,
                )
                if (isCurrentRequest()) {
                    _state.update {
                        it.copy(
                            agendaWeeks = it.agendaWeeks + (offset to result.week),
                            agendaFailedOffsets = it.agendaFailedOffsets - offset,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (isCurrentRequest()) {
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

    private fun scheduleSearch(delayMillis: Long) {
        searchJob?.cancel()
        val query = state.value.searchQuery.trim()
        val type = state.value.searchType

        searchJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            if (state.value.searchQuery.trim() != query || state.value.searchType != type) return@launch

            _state.update { it.copy(isSearching = true, searchError = null) }

            // Show previously discovered groups/teachers/rooms immediately,
            // even with no network. Remote results subsequently take priority.
            val offline = try {
                container.repository.cachedSearch(query, type)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            fun stillCurrent() = state.value.searchQuery.trim() == query &&
                state.value.searchType == type
            if (stillCurrent()) {
                _state.update { it.copy(searchResults = offline, searchError = null) }
            }
            try {
                val results = container.repository.search(query, type)
                if (stillCurrent()) {
                    val merged = (results + offline).distinctBy { it.type to it.id }
                    _state.update {
                        it.copy(
                            isSearching = false,
                            searchResults = merged,
                            searchError = null,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (stillCurrent()) {
                    _state.update {
                        it.copy(
                            isSearching = false,
                            searchResults = offline,
                            searchError = if (offline.isEmpty()) {
                                error.message ?: "Не удалось выполнить поиск"
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
