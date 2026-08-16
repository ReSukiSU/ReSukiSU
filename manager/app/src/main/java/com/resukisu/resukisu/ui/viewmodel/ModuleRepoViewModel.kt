package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.model.CatalogModule
import com.resukisu.resukisu.domain.model.ModuleCatalogFailure
import com.resukisu.resukisu.domain.model.ModuleCatalogResult
import com.resukisu.resukisu.domain.usecase.GetBooleanPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.ObserveCatalogModulesUseCase
import com.resukisu.resukisu.domain.usecase.ObserveModuleCatalogOfflineUseCase
import com.resukisu.resukisu.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import com.resukisu.resukisu.domain.usecase.RefreshModuleCatalogUseCase
import com.resukisu.resukisu.domain.usecase.SetBooleanPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.TransliterateTextUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModuleRepoUiState(
    val modules: List<CatalogModule> = emptyList(),
    val sortStargazerCountFirst: Boolean = false,
    val isRefreshing: Boolean = false,
    val offline: Boolean = false,
    val search: String = "",
)

sealed interface ModuleRepoUiAction {
    data object Refresh : ModuleRepoUiAction
    data class Search(val query: String) : ModuleRepoUiAction
    data class SetStarsFirst(val enabled: Boolean) : ModuleRepoUiAction
}

sealed interface ModuleRepoUiEvent {
    data object Offline : ModuleRepoUiEvent
    data class Error(val message: String) : ModuleRepoUiEvent
}

class ModuleRepoViewModel(
    observeModules: ObserveCatalogModulesUseCase,
    observeRefreshing: ObserveModuleCatalogRefreshingUseCase,
    observeOffline: ObserveModuleCatalogOfflineUseCase,
    private val refreshCatalog: RefreshModuleCatalogUseCase,
    getBooleanPreference: GetBooleanPreferenceUseCase,
    private val setBooleanPreference: SetBooleanPreferenceUseCase,
    private val transliterateText: TransliterateTextUseCase,
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val sortStarsFirst = MutableStateFlow(
        getBooleanPreference("module_repo_sort_star_first", false)
    )
    private val mutableEvents = MutableSharedFlow<ModuleRepoUiEvent>(extraBufferCapacity = 1)

    val events: SharedFlow<ModuleRepoUiEvent> = mutableEvents.asSharedFlow()
    val state: StateFlow<ModuleRepoUiState> = combine(
        observeModules(),
        observeRefreshing(),
        observeOffline(),
        search,
        sortStarsFirst,
    ) { modules, refreshing, offline, query, starsFirst ->
        ModuleRepoUiState(
            modules = modules.filter { module ->
                module.moduleId.contains(query, true) ||
                        module.moduleName.contains(query, true) ||
                        transliterateText(module.moduleName).contains(query, true)
            }.sortedWith(
                compareByDescending<CatalogModule> { it.installed }
                    .thenByDescending { if (starsFirst) it.stargazerCount else 0 }
            ),
            sortStargazerCountFirst = starsFirst,
            isRefreshing = refreshing,
            offline = offline,
            search = query,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ModuleRepoUiState(),
    )
    val uiState: StateFlow<ModuleRepoUiState> = state

    fun updateSearch(value: String) {
        search.value = value
    }

    fun setSortStargazerCountFirst(enabled: Boolean) {
        setBooleanPreference("module_repo_sort_star_first", enabled)
        sortStarsFirst.value = enabled
    }

    fun refresh(onFailure: (() -> Unit)? = null) {
        viewModelScope.launch {
            when (val result = refreshCatalog()) {
                is ModuleCatalogResult.Success -> Unit
                is ModuleCatalogResult.Failure -> {
                    onFailure?.invoke()
                    when (val reason = result.reason) {
                        ModuleCatalogFailure.Offline -> mutableEvents.emit(ModuleRepoUiEvent.Offline)
                        ModuleCatalogFailure.NotFound -> mutableEvents.emit(
                            ModuleRepoUiEvent.Error("Module not found")
                        )

                        is ModuleCatalogFailure.Network -> mutableEvents.emit(
                            ModuleRepoUiEvent.Error(reason.message)
                        )
                    }
                }
            }
        }
    }

    fun dispatch(action: ModuleRepoUiAction) {
        when (action) {
            ModuleRepoUiAction.Refresh -> refresh()
            is ModuleRepoUiAction.Search -> updateSearch(action.query)
            is ModuleRepoUiAction.SetStarsFirst -> setSortStargazerCountFirst(action.enabled)
        }
    }
}
