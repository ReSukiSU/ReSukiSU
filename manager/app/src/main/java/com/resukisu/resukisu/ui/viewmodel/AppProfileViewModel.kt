package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.model.AppControlAction
import com.resukisu.resukisu.domain.model.AppProfile
import com.resukisu.resukisu.domain.model.InstalledAppGroup
import com.resukisu.resukisu.domain.usecase.ControlAppUseCase
import com.resukisu.resukisu.domain.usecase.GetAppProfileUseCase
import com.resukisu.resukisu.domain.usecase.GetAppSepolicyUseCase
import com.resukisu.resukisu.domain.usecase.GetDefaultUmountModulesUseCase
import com.resukisu.resukisu.domain.usecase.GetSuperUserAppGroupUseCase
import com.resukisu.resukisu.domain.usecase.SetAppProfileUseCase
import com.resukisu.resukisu.domain.usecase.SetAppSepolicyUseCase
import com.resukisu.resukisu.domain.usecase.ValidateSepolicyUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AppProfileUiState(
    val appGroup: InstalledAppGroup? = null,
    val profile: AppProfile? = null,
    val defaultUmountModules: Boolean = true,
    val isLoading: Boolean = true,
    val sepolicyValid: Boolean = true,
)

sealed interface AppProfileUiAction {
    data object Load : AppProfileUiAction
    data class Save(val profile: AppProfile) : AppProfileUiAction
    data class ControlApp(val action: AppControlAction) : AppProfileUiAction
    data class ValidateSepolicy(val rules: String) : AppProfileUiAction
}

sealed interface AppProfileUiEvent {
    data object Saved : AppProfileUiEvent
    data class Error(val cause: Throwable? = null) : AppProfileUiEvent
    data object SepolicyUpdateFailed : AppProfileUiEvent
}

class AppProfileViewModel(
    private val uid: Int,
    private val packageName: String,
    private val getAppGroup: GetSuperUserAppGroupUseCase,
    private val getProfile: GetAppProfileUseCase,
    private val getDefaultUmountModules: GetDefaultUmountModulesUseCase,
    private val setProfile: SetAppProfileUseCase,
    private val getSepolicy: GetAppSepolicyUseCase,
    private val setSepolicy: SetAppSepolicyUseCase,
    private val controlApp: ControlAppUseCase,
    private val validateSepolicy: ValidateSepolicyUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppProfileUiState())
    val state: StateFlow<AppProfileUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<AppProfileUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AppProfileUiEvent> = mutableEvents.asSharedFlow()
    private var validationJob: Job? = null
    private val saveMutex = Mutex()

    init {
        dispatch(AppProfileUiAction.Load)
    }

    fun dispatch(action: AppProfileUiAction) {
        when (action) {
            AppProfileUiAction.Load -> viewModelScope.launch {
                mutableState.update { it.copy(isLoading = true) }
                runCatching {
                    val profile = getProfile(packageName, uid)
                    val loadedProfile = if (profile.allowSu) {
                        profile.copy(
                            rules = runCatching { getSepolicy(packageName) }
                                .getOrDefault(profile.rules)
                        )
                    } else {
                        profile
                    }
                    Triple(
                        getAppGroup(uid, packageName),
                        loadedProfile,
                        runCatching { getDefaultUmountModules() }
                            .getOrDefault(profile.umountModules),
                    )
                }.onSuccess { (group, profile, defaultUmountModules) ->
                    mutableState.value = AppProfileUiState(
                        appGroup = group,
                        profile = profile,
                        defaultUmountModules = defaultUmountModules,
                        isLoading = false,
                    )
                }.onFailure { error ->
                    mutableState.update { it.copy(isLoading = false) }
                    mutableEvents.tryEmit(AppProfileUiEvent.Error(error))
                }
            }

            is AppProfileUiAction.Save -> {
                val previous = mutableState.value.profile
                mutableState.update { it.copy(profile = action.profile) }
                viewModelScope.launch {
                    saveMutex.withLock {
                        val sepolicyKey = action.profile.rootTemplate ?: action.profile.name
                        if (action.profile.allowSu && !action.profile.rootUseDefault &&
                            action.profile.rules.isNotEmpty() &&
                            !setSepolicy(sepolicyKey, action.profile.rules)
                        ) {
                            rollbackIfCurrent(action.profile, previous)
                            mutableEvents.emit(AppProfileUiEvent.SepolicyUpdateFailed)
                            return@withLock
                        }
                        runCatching { setProfile(action.profile) }
                            .onSuccess { saved ->
                                if (saved) {
                                    mutableEvents.tryEmit(AppProfileUiEvent.Saved)
                                } else {
                                    rollbackIfCurrent(action.profile, previous)
                                    mutableEvents.tryEmit(AppProfileUiEvent.Error())
                                }
                            }
                            .onFailure {
                                rollbackIfCurrent(action.profile, previous)
                                mutableEvents.tryEmit(AppProfileUiEvent.Error(it))
                            }
                    }
                }
            }

            is AppProfileUiAction.ControlApp -> viewModelScope.launch {
                controlApp(packageName, action.action)
                    .onFailure { mutableEvents.tryEmit(AppProfileUiEvent.Error(it)) }
            }

            is AppProfileUiAction.ValidateSepolicy -> {
                validationJob?.cancel()
                mutableState.update { it.copy(sepolicyValid = false) }
                validationJob = viewModelScope.launch {
                    val valid = runCatching { validateSepolicy(action.rules) }.getOrDefault(false)
                    mutableState.update { it.copy(sepolicyValid = valid) }
                }
            }
        }
    }

    private fun rollbackIfCurrent(failed: AppProfile, previous: AppProfile?) {
        mutableState.update { current ->
            if (current.profile == failed) current.copy(profile = previous) else current
        }
    }
}
