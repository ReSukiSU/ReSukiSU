package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.data.module.ModulePreferencesRepository
import com.resukisu.resukisu.domain.model.InstalledModule
import com.resukisu.resukisu.domain.model.MetaModuleStatus
import com.resukisu.resukisu.domain.usecase.CalculateInstalledModuleSizeUseCase
import com.resukisu.resukisu.domain.usecase.GetBooleanPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.ObserveInstalledModulesUseCase
import com.resukisu.resukisu.domain.usecase.RebootUseCase
import com.resukisu.resukisu.domain.usecase.RefreshInstalledModulesUseCase
import com.resukisu.resukisu.domain.usecase.SetModuleEnabledUseCase
import com.resukisu.resukisu.domain.usecase.SetModuleRemovedUseCase
import com.resukisu.resukisu.domain.usecase.TransliterateTextUseCase
import com.resukisu.resukisu.domain.usecase.UpdateCachedModuleEnabledUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

data class ModuleUiState(
    val moduleList: List<InstalledModule> = emptyList(),
    val moduleSizes: Map<String, String> = emptyMap(),
    val isRefreshing: Boolean = false,
    val search: String = "",
    val sortEnabledFirst: Boolean = false,
    val sortActionFirst: Boolean = false,
    val hasModuleRequireMount: Boolean = false,
    val hasMagisk: Boolean = false,
    val metaModuleStatus: MetaModuleStatus = MetaModuleStatus.MISSING,
    val isNeedRefresh: Boolean = false,
    val showMoreModuleInfo: Boolean = false,
)

sealed interface ModuleUiAction {
    data class Refresh(val manual: Boolean = false) : ModuleUiAction
    data object ReloadSettings : ModuleUiAction
    data class Search(val query: String) : ModuleUiAction
    data class Sort(val enabledFirst: Boolean, val actionFirst: Boolean) : ModuleUiAction
    data class SetShowMoreInfo(val enabled: Boolean) : ModuleUiAction
    data class LoadSize(val moduleId: String) : ModuleUiAction
    data object MarkNeedRefresh : ModuleUiAction
    data class UpdateCachedEnabled(val moduleId: String, val enabled: Boolean) : ModuleUiAction
    data class SetEnabled(val moduleId: String, val enabled: Boolean) : ModuleUiAction
    data class SetRemoved(val moduleId: String, val removed: Boolean) : ModuleUiAction
    data object Reboot : ModuleUiAction
}

sealed interface ModuleUiEvent {
    data class Error(val message: String) : ModuleUiEvent
    data object RefreshCompleted : ModuleUiEvent
    data class EnabledChanged(
        val moduleId: String,
        val enabled: Boolean,
        val successful: Boolean,
    ) : ModuleUiEvent

    data class RemovedChanged(
        val moduleId: String,
        val removed: Boolean,
        val successful: Boolean,
    ) : ModuleUiEvent
}

private data class ModuleControls(
    val search: String = "",
    val isNeedRefresh: Boolean = false,
    val moduleSizes: Map<String, String> = emptyMap(),
)

class ModuleViewModel(
    observeInstalledModules: ObserveInstalledModulesUseCase,
    private val modulePreferences: ModulePreferencesRepository,
    private val refreshInstalledModules: RefreshInstalledModulesUseCase,
    private val calculateModuleSize: CalculateInstalledModuleSizeUseCase,
    private val updateCachedModuleEnabledUseCase: UpdateCachedModuleEnabledUseCase,
    private val getBooleanPreference: GetBooleanPreferenceUseCase,
    private val transliterateText: TransliterateTextUseCase,
    private val setModuleEnabled: SetModuleEnabledUseCase,
    private val setModuleRemoved: SetModuleRemovedUseCase,
    private val reboot: RebootUseCase,
) : ViewModel() {
    private val controls = MutableStateFlow(ModuleControls())
    private val mutableEvents = MutableSharedFlow<ModuleUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ModuleUiEvent> = mutableEvents.asSharedFlow()

    val state: StateFlow<ModuleUiState> = combine(
        observeInstalledModules(),
        controls,
        modulePreferences.preferences,
    ) { source, local, preferences ->
        ModuleUiState(
            moduleList = buildModuleList(
                modules = source.modules,
                search = local.search,
                sortEnabledFirst = preferences.sortEnabledFirst,
                sortActionFirst = preferences.sortActionFirst,
            ),
            moduleSizes = local.moduleSizes,
            isRefreshing = source.refreshing,
            search = local.search,
            sortEnabledFirst = preferences.sortEnabledFirst,
            sortActionFirst = preferences.sortActionFirst,
            hasModuleRequireMount = source.hasModuleRequireMount,
            hasMagisk = source.hasMagisk,
            metaModuleStatus = source.metaModuleStatus,
            isNeedRefresh = local.isNeedRefresh,
            showMoreModuleInfo = preferences.showMoreModuleInfo,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ModuleUiState())
    val uiState: StateFlow<ModuleUiState> = state

    fun dispatch(action: ModuleUiAction) {
        when (action) {
            is ModuleUiAction.Refresh -> refresh(action.manual)
            ModuleUiAction.ReloadSettings -> modulePreferences.reload()
            is ModuleUiAction.Search -> controls.update { it.copy(search = action.query) }
            is ModuleUiAction.Sort -> {
                modulePreferences.setSort(action.enabledFirst, action.actionFirst)
            }

            is ModuleUiAction.SetShowMoreInfo -> {
                modulePreferences.setShowMoreInfo(action.enabled)
            }

            is ModuleUiAction.LoadSize -> viewModelScope.launch {
                val size = formatFileSize(calculateModuleSize(action.moduleId))
                controls.update { current ->
                    current.copy(moduleSizes = current.moduleSizes + (action.moduleId to size))
                }
            }

            ModuleUiAction.MarkNeedRefresh -> controls.update { it.copy(isNeedRefresh = true) }
            is ModuleUiAction.UpdateCachedEnabled ->
                updateCachedModuleEnabledUseCase(action.moduleId, action.enabled)

            is ModuleUiAction.SetEnabled -> viewModelScope.launch {
                val successful = setModuleEnabled(action.moduleId, action.enabled).isSuccess
                mutableEvents.emit(
                    ModuleUiEvent.EnabledChanged(action.moduleId, action.enabled, successful)
                )
            }

            is ModuleUiAction.SetRemoved -> viewModelScope.launch {
                val successful = setModuleRemoved(action.moduleId, action.removed).isSuccess
                mutableEvents.emit(
                    ModuleUiEvent.RemovedChanged(action.moduleId, action.removed, successful)
                )
            }

            ModuleUiAction.Reboot -> viewModelScope.launch {
                reboot().onFailure { mutableEvents.tryEmit(ModuleUiEvent.Error(it.message.orEmpty())) }
            }
        }
    }
    private fun refresh(manual: Boolean) {
        viewModelScope.launch { refreshNow(manual) }
    }

    private suspend fun refreshNow(manual: Boolean) {
        val checkUpdates = getBooleanPreference(
            PREF_CHECK_MODULE_UPDATE,
            getBooleanPreference(PREF_CHECK_UPDATE, true),
        )
        refreshInstalledModules(manual, checkUpdates)
            .onSuccess {
                controls.update { it.copy(isNeedRefresh = false) }
                mutableEvents.tryEmit(ModuleUiEvent.RefreshCompleted)
            }
            .onFailure { error ->
                mutableEvents.tryEmit(ModuleUiEvent.Error(error.message.orEmpty()))
            }
    }

    private fun buildModuleList(
        modules: List<InstalledModule>,
        search: String,
        sortEnabledFirst: Boolean,
        sortActionFirst: Boolean,
    ): List<InstalledModule> {
        val comparator = compareBy<InstalledModule>(
            {
                val executable = it.hasWebUi || it.hasActionScript
                when {
                    it.metamodule && it.enabled -> 0
                    sortEnabledFirst && sortActionFirst -> when {
                        it.enabled && executable -> 1
                        it.enabled -> 2
                        executable -> 3
                        else -> 4
                    }

                    sortEnabledFirst -> if (it.enabled) 1 else 2
                    sortActionFirst -> if (executable) 1 else 2
                    else -> 1
                }
            },
            { if (sortEnabledFirst) !it.enabled else false },
            { if (sortActionFirst) !(it.hasWebUi || it.hasActionScript) else false },
        ).thenBy(Collator.getInstance(Locale.getDefault()), InstalledModule::id)

        return modules.filter { module ->
            module.id.contains(search, ignoreCase = true) ||
                    module.name.contains(search, ignoreCase = true) ||
                    transliterateText(module.name).contains(search, ignoreCase = true)
        }.sortedWith(comparator)
    }

    private companion object {
        const val PREF_CHECK_MODULE_UPDATE = "check_module_update"
        const val PREF_CHECK_UPDATE = "check_update"
    }
}

fun formatFileSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val tb = gb * 1024
    return when {
        bytes >= tb -> "%.2f TB".format(bytes / tb)
        bytes >= gb -> "%.2f GB".format(bytes / gb)
        bytes >= mb -> "%.2f MB".format(bytes / mb)
        bytes >= kb -> "%.2f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}
