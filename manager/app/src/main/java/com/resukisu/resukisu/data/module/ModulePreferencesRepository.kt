package com.resukisu.resukisu.data.module

import com.resukisu.resukisu.data.AppSettingsRepository
import com.resukisu.resukisu.domain.model.ModulePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ModulePreferencesRepository(
    private val settings: AppSettingsRepository,
) {
    private val mutablePreferences = MutableStateFlow(readPreferences())
    val preferences: StateFlow<ModulePreferences> = mutablePreferences.asStateFlow()

    fun reload() {
        mutablePreferences.value = readPreferences()
    }

    fun setSort(enabledFirst: Boolean, actionFirst: Boolean) {
        mutablePreferences.update {
            it.copy(sortEnabledFirst = enabledFirst, sortActionFirst = actionFirst)
        }
        settings.putBoolean(PREF_SORT_ENABLED, enabledFirst)
        settings.putBoolean(PREF_SORT_ACTION, actionFirst)
    }

    fun setShowMoreInfo(enabled: Boolean) {
        mutablePreferences.update { it.copy(showMoreModuleInfo = enabled) }
        settings.putBoolean(PREF_SHOW_MORE_INFO, enabled)
    }

    private fun readPreferences() = ModulePreferences(
        sortEnabledFirst = settings.getBoolean(PREF_SORT_ENABLED, false),
        sortActionFirst = settings.getBoolean(PREF_SORT_ACTION, false),
        showMoreModuleInfo = settings.getBoolean(PREF_SHOW_MORE_INFO, false),
    )

    private companion object {
        const val PREF_SORT_ENABLED = "module_sort_enabled_first"
        const val PREF_SORT_ACTION = "module_sort_action_first"
        const val PREF_SHOW_MORE_INFO = "show_more_module_info"
    }
}
