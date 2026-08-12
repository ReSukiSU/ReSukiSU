package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.AllowlistOperationResult
import com.resukisu.resukisu.domain.model.InstalledAppGroup
import com.resukisu.resukisu.domain.usecase.BackupAllowlistUseCase
import com.resukisu.resukisu.domain.usecase.GetBooleanPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.GetStringPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.ImportAllowlistUseCase
import com.resukisu.resukisu.domain.usecase.ObserveSuperUserStateUseCase
import com.resukisu.resukisu.domain.usecase.RefreshSuperUsersUseCase
import com.resukisu.resukisu.domain.usecase.SetBooleanPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.SetStringPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.TransliterateTextUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortType(val displayNameRes: Int, val persistKey: String) {
    NAME_ASC(R.string.sort_name_asc, "NAME_ASC"),
    NAME_DESC(R.string.sort_name_desc, "NAME_DESC"),
    INSTALL_TIME_NEW(R.string.sort_install_time_new, "INSTALL_TIME_NEW"),
    INSTALL_TIME_OLD(R.string.sort_install_time_old, "INSTALL_TIME_OLD"),
    SIZE_DESC(R.string.sort_size_desc, "SIZE_DESC"),
    SIZE_ASC(R.string.sort_size_asc, "SIZE_ASC"),
    USAGE_FREQ(R.string.sort_usage_freq, "USAGE_FREQ");

    companion object {
        fun fromPersistKey(key: String): SortType = entries.find { it.persistKey == key } ?: NAME_ASC
    }
}

data class SuperUserUiState(
    val appGroupList: List<InstalledAppGroup> = emptyList(),
    val search: String = "",
    val showSystemApps: Boolean = false,
    val currentSortType: SortType = SortType.NAME_ASC,
    val isRefreshing: Boolean = false,
)

sealed interface SuperUserUiAction {
    data object Refresh : SuperUserUiAction
    data class BackupAllowlist(val uri: String) : SuperUserUiAction
    data class RestoreAllowlist(val uri: String) : SuperUserUiAction
    data class Search(val query: String) : SuperUserUiAction
    data class SetShowSystemApps(val enabled: Boolean) : SuperUserUiAction
    data class SetSort(val sortType: SortType) : SuperUserUiAction
    data object StatusChanged : SuperUserUiAction
}

sealed interface SuperUserUiEvent {
    data class Error(val message: String) : SuperUserUiEvent
    data class AllowlistOperationFinished(
        val result: AllowlistOperationResult,
        val restore: Boolean,
    ) : SuperUserUiEvent
}

private data class SuperUserControls(
    val search: String = "",
    val showSystemApps: Boolean = false,
    val sortType: SortType = SortType.NAME_ASC,
)

class SuperUserViewModel(
    observeSuperUserState: ObserveSuperUserStateUseCase,
    private val refreshSuperUsers: RefreshSuperUsersUseCase,
    private val backupAllowlistUseCase: BackupAllowlistUseCase,
    private val importAllowlistUseCase: ImportAllowlistUseCase,
    getBooleanPreference: GetBooleanPreferenceUseCase,
    getStringPreference: GetStringPreferenceUseCase,
    private val setBooleanPreference: SetBooleanPreferenceUseCase,
    private val setStringPreference: SetStringPreferenceUseCase,
    private val transliterateText: TransliterateTextUseCase,
) : ViewModel() {
    private val sourceState = observeSuperUserState()
    private val controls = MutableStateFlow(
        SuperUserControls(
            showSystemApps = getBooleanPreference(KEY_SHOW_SYSTEM_APPS, false),
            sortType = SortType.fromPersistKey(
                getStringPreference(KEY_CURRENT_SORT_TYPE, SortType.NAME_ASC.persistKey)
                    ?: SortType.NAME_ASC.persistKey
            ),
        )
    )
    private val mutableEvents = MutableSharedFlow<SuperUserUiEvent>(extraBufferCapacity = 1)
    private var refreshJob: Job? = null
    val events: SharedFlow<SuperUserUiEvent> = mutableEvents.asSharedFlow()

    val state: StateFlow<SuperUserUiState> = combine(sourceState, controls) { source, local ->
        SuperUserUiState(
            appGroupList = buildAppGroupList(
                groups = source.groups,
                search = local.search,
                showSystemApps = local.showSystemApps,
                currentSortType = local.sortType,
            ),
            search = local.search,
            showSystemApps = local.showSystemApps,
            currentSortType = local.sortType,
            isRefreshing = source.refreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SuperUserUiState())
    val uiState: StateFlow<SuperUserUiState> = state

    fun dispatch(action: SuperUserUiAction) {
        when (action) {
            SuperUserUiAction.Refresh -> refresh()
            is SuperUserUiAction.BackupAllowlist -> viewModelScope.launch {
                mutableEvents.emit(
                    SuperUserUiEvent.AllowlistOperationFinished(
                        result = backupAllowlistUseCase(action.uri),
                        restore = false,
                    )
                )
            }

            is SuperUserUiAction.RestoreAllowlist -> viewModelScope.launch {
                val result = importAllowlistUseCase(action.uri)
                if (result == AllowlistOperationResult.Success) {
                    notifySuperuserStatusChanged()
                }
                mutableEvents.emit(
                    SuperUserUiEvent.AllowlistOperationFinished(
                        result = result,
                        restore = true,
                    )
                )
            }

            is SuperUserUiAction.Search -> controls.value =
                controls.value.copy(search = action.query)

            is SuperUserUiAction.SetShowSystemApps -> {
                setBooleanPreference(KEY_SHOW_SYSTEM_APPS, action.enabled)
                controls.value = controls.value.copy(showSystemApps = action.enabled)
            }

            is SuperUserUiAction.SetSort -> {
                setStringPreference(KEY_CURRENT_SORT_TYPE, action.sortType.persistKey)
                controls.value = controls.value.copy(sortType = action.sortType)
            }

            SuperUserUiAction.StatusChanged -> notifySuperuserStatusChanged()
        }
    }

    suspend fun fetchAppList() {
        refreshSuperUsers()
            .onFailure { mutableEvents.tryEmit(SuperUserUiEvent.Error(it.message.orEmpty())) }
    }

    private fun notifySuperuserStatusChanged() {
        viewModelScope.launch {
            sourceState.first { !it.refreshing }
            refresh()
        }
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { fetchAppList() }
    }

    private fun buildAppGroupList(
        groups: List<InstalledAppGroup>,
        search: String,
        showSystemApps: Boolean,
        currentSortType: SortType,
    ): List<InstalledAppGroup> = groups
        .filter { group ->
            group.apps.any { app ->
                app.label.contains(search, true) ||
                        app.packageName.contains(search, true) ||
                        transliterateText(app.label).contains(search, true)
            }
        }
        .filter { group ->
            group.uid == 2000 || showSystemApps || group.apps.any { !it.isSystem }
        }
        .sortedWith { first, second ->
            val priority = groupPriority(first).compareTo(groupPriority(second))
            if (priority != 0) {
                priority
            } else {
                when (currentSortType) {
                    SortType.NAME_ASC -> first.mainApp.label.compareTo(second.mainApp.label, true)
                    SortType.NAME_DESC -> second.mainApp.label.compareTo(first.mainApp.label, true)
                    SortType.INSTALL_TIME_NEW ->
                        second.mainApp.firstInstallTime.compareTo(first.mainApp.firstInstallTime)

                    SortType.INSTALL_TIME_OLD ->
                        first.mainApp.firstInstallTime.compareTo(second.mainApp.firstInstallTime)

                    else -> first.mainApp.label.compareTo(second.mainApp.label, true)
                }
            }
        }

    private fun groupPriority(group: InstalledAppGroup): Int = when {
        group.allowSu -> 0
        group.isRecentlyInstalled -> 1
        group.hasCustomProfile -> 2
        else -> 3
    }

    private companion object {
        const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        const val KEY_CURRENT_SORT_TYPE = "current_sort_type"
    }
}
