package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.model.SulogEntry
import com.resukisu.resukisu.domain.model.SulogEventFilter
import com.resukisu.resukisu.domain.model.SulogFile
import com.resukisu.resukisu.domain.model.defaultSulogEventFilters
import com.resukisu.resukisu.domain.model.filterSulogEntries
import com.resukisu.resukisu.domain.usecase.CleanSulogUseCase
import com.resukisu.resukisu.domain.usecase.EnableSulogUseCase
import com.resukisu.resukisu.domain.usecase.GetStringSetPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.ObserveSulogStateUseCase
import com.resukisu.resukisu.domain.usecase.RefreshSulogUseCase
import com.resukisu.resukisu.domain.usecase.SetStringSetPreferenceUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SulogScreenState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sulogStatus: String = "",
    val isSulogEnabled: Boolean = false,
    val searchText: String = "",
    val selectedFilters: Set<SulogEventFilter> = emptySet(),
    val files: List<SulogFile> = emptyList(),
    val selectedFilePath: String? = null,
    val entries: List<SulogEntry> = emptyList(),
    val visibleEntries: List<SulogEntry> = emptyList(),
    val errorMessage: String? = null,
)

data class SulogActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onEnableSulog: () -> Unit,
    val onCleanFile: () -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onToggleFilter: (SulogEventFilter) -> Unit,
    val onSelectFile: (String) -> Unit,
)

data class SulogFileSelector(
    val items: List<String>,
    val selectedIndex: Int,
)

typealias SulogUiState = SulogScreenState

sealed interface SulogUiAction {
    data object Refresh : SulogUiAction
    data object RefreshLatest : SulogUiAction
    data object Enable : SulogUiAction
    data object CleanFile : SulogUiAction
    data class Search(val query: String) : SulogUiAction
    data class ToggleFilter(val filter: SulogEventFilter) : SulogUiAction
    data class SelectFile(val path: String) : SulogUiAction
}

sealed interface SulogUiEvent {
    data class Error(val message: String) : SulogUiEvent
}

class SulogViewModel(
    observeState: ObserveSulogStateUseCase,
    private val refreshSulog: RefreshSulogUseCase,
    private val setSulogEnabled: EnableSulogUseCase,
    private val cleanSulog: CleanSulogUseCase,
    getStringSetPreference: GetStringSetPreferenceUseCase,
    private val setStringSetPreference: SetStringSetPreferenceUseCase,
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val filters = MutableStateFlow(
        getStringSetPreference(PREF_SULOG_FILTERS)
            .mapNotNull { raw -> SulogEventFilter.entries.firstOrNull { it.name == raw } }
            .toSet()
            .ifEmpty(::defaultSulogEventFilters)
    )
    private var refreshJob: Job? = null
    private val mutableEvents = MutableSharedFlow<SulogUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SulogUiEvent> = mutableEvents.asSharedFlow()

    val state: StateFlow<SulogUiState> = combine(
        observeState(),
        search,
        filters,
    ) { source, query, selectedFilters ->
        SulogUiState(
            isLoading = source.isLoading,
            isRefreshing = source.isRefreshing,
            sulogStatus = source.status,
            isSulogEnabled = source.enabled,
            searchText = query,
            selectedFilters = selectedFilters,
            files = source.files,
            selectedFilePath = source.selectedFilePath,
            entries = source.entries,
            visibleEntries = filterSulogEntries(source.entries, query, selectedFilters),
            errorMessage = source.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SulogUiState(selectedFilters = filters.value),
    )
    val uiState: StateFlow<SulogUiState> = state

    fun dispatch(action: SulogUiAction) {
        when (action) {
            SulogUiAction.Refresh -> refresh(state.value.selectedFilePath)
            SulogUiAction.RefreshLatest -> refresh(null)
            SulogUiAction.Enable -> viewModelScope.launch {
                val result = setSulogEnabled(true)
                result.exceptionOrNull()?.let {
                    mutableEvents.emit(SulogUiEvent.Error(it.message.orEmpty()))
                } ?: refresh(state.value.selectedFilePath)
            }

            SulogUiAction.CleanFile -> state.value.selectedFilePath?.let { path ->
                viewModelScope.launch {
                    val result = cleanSulog(path)
                    result.exceptionOrNull()?.let {
                        mutableEvents.emit(SulogUiEvent.Error(it.message.orEmpty()))
                    } ?: refresh(path)
                }
            }

            is SulogUiAction.Search -> search.value = action.query
            is SulogUiAction.ToggleFilter -> {
                filters.value = filters.value.toMutableSet().apply {
                    if (!add(action.filter)) remove(action.filter)
                }
                setStringSetPreference(PREF_SULOG_FILTERS, filters.value.map { it.name }.toSet())
            }

            is SulogUiAction.SelectFile -> refresh(action.path)
        }
    }

    private fun refresh(preferredFilePath: String?) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            refreshSulog(preferredFilePath).exceptionOrNull()?.let {
                mutableEvents.emit(SulogUiEvent.Error(it.message.orEmpty()))
            }
        }
    }

    private companion object {
        const val PREF_SULOG_FILTERS = "sulog_filters"
    }
}
