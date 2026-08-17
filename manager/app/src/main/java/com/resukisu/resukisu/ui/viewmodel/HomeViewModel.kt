package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.data.system.HomeStateRepository
import com.resukisu.resukisu.domain.model.HomeDashboardState
import com.resukisu.resukisu.domain.model.HomeSystemInfo
import com.resukisu.resukisu.domain.model.ManagerUpdateChannel
import com.resukisu.resukisu.domain.usecase.CheckManagerUpdateUseCase
import com.resukisu.resukisu.domain.usecase.GetBooleanPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.GetHomeBasicInfoUseCase
import com.resukisu.resukisu.domain.usecase.GetHomeModuleOverviewUseCase
import com.resukisu.resukisu.domain.usecase.GetHomeSuperuserCountUseCase
import com.resukisu.resukisu.domain.usecase.GetKernelStatusUseCase
import com.resukisu.resukisu.domain.usecase.GetManagerRuntimeInfoUseCase
import com.resukisu.resukisu.domain.usecase.GetSuSFSStatusUseCase
import com.resukisu.resukisu.domain.usecase.IsNetworkAvailableUseCase
import com.resukisu.resukisu.domain.usecase.RebootUseCase
import com.resukisu.resukisu.domain.usecase.SetBooleanPreferenceUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias HomeUiState = HomeDashboardState

sealed interface HomeUiAction {
    data object AwaitInitialData : HomeUiAction
    data class Refresh(val showIndicator: Boolean = true) : HomeUiAction
    data class SetSimpleMode(val enabled: Boolean) : HomeUiAction
    data class Reboot(val reason: String) : HomeUiAction
}

sealed interface HomeUiEvent {
    data class Error(val message: String) : HomeUiEvent
}

class HomeViewModel(
    private val homeStateRepository: HomeStateRepository,
    private val checkManagerUpdate: CheckManagerUpdateUseCase,
    private val getKernelStatus: GetKernelStatusUseCase,
    private val getManagerRuntimeInfo: GetManagerRuntimeInfoUseCase,
    private val getSuSFSStatus: GetSuSFSStatusUseCase,
    private val getBasicInfo: GetHomeBasicInfoUseCase,
    private val getModuleOverview: GetHomeModuleOverviewUseCase,
    private val getSuperuserCount: GetHomeSuperuserCountUseCase,
    private val isNetworkAvailable: IsNetworkAvailableUseCase,
    private val getBooleanPreference: GetBooleanPreferenceUseCase,
    private val setBooleanPreference: SetBooleanPreferenceUseCase,
    private val reboot: RebootUseCase,
) : ViewModel() {
    val state = homeStateRepository.state
    val uiState = state
    private val mutableEvents = MutableSharedFlow<HomeUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HomeUiEvent> = mutableEvents.asSharedFlow()

    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null
    private var updateJob: Job? = null

    init {
        // Every navigation-scoped instance publishes persisted toggles to the shared state source.
        applyUserSettings()
    }

    suspend fun awaitInitialData() {
        refreshData(refreshUI = false).join()
    }

    fun refreshData(refreshUI: Boolean = false): Job {
        if (!refreshUI) {
            refreshJob?.takeIf(Job::isActive)?.let { return it }
            if (state.value.isInitialDataLoaded) return completedJob()
        }
        refreshManagerUpdates(force = refreshUI)
        return viewModelScope.launch {
            refreshMutex.withLock {
                homeStateRepository.update { it.copy(isRefreshing = refreshUI) }
                try {
                    applyUserSettings()
                    val kernelStatus = runCatching { getKernelStatus() }
                        .getOrElse { state.value.systemStatus }
                    homeStateRepository.update {
                        it.copy(systemStatus = kernelStatus, isCoreDataLoaded = true)
                    }

                    val basic = async { getBasicInfo(kernelStatus.managerUAPIVersion) }
                    val module = async { getModuleOverview() }
                    val superusers = async { getSuperuserCount() }
                    val managers = async { getManagerRuntimeInfo() }
                    val susfs = async { getSuSFSStatus() }
                    val basicInfo = basic.await()
                    val moduleInfo = module.await()
                    val superuserCount = superusers.await()
                    val managerInfo = managers.await()
                    val susfsInfo = susfs.await()
                    homeStateRepository.update { current ->
                        current.copy(
                            systemInfo = HomeSystemInfo(
                                kernelRelease = basicInfo.kernelRelease,
                                androidVersion = basicInfo.androidVersion,
                                deviceModel = basicInfo.deviceModel,
                                managerVersion = basicInfo.managerVersion,
                                selinuxStatus = basicInfo.selinuxStatus,
                                susfsEnabled = susfsInfo.enabled,
                                susfsVersionSupported = susfsInfo.enabled,
                                susfsVersion = susfsInfo.version,
                                susfsFeatures = susfsInfo.enabledFeatures,
                                superuserCount = superuserCount,
                                moduleCount = moduleInfo.count,
                                managersList = managerInfo,
                                isDynamicSignEnabled = managerInfo.dynamicSignatureEnabled,
                                zygiskImplement = moduleInfo.zygiskImplementation,
                                metaModuleImplement = moduleInfo.metaModuleImplementation,
                                seccompStatus = basicInfo.seccompStatus,
                            ),
                            isInitialDataLoaded = true,
                            isExtendedDataLoaded = true,
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    mutableEvents.emit(HomeUiEvent.Error(error.message.orEmpty()))
                } finally {
                    homeStateRepository.update {
                        it.copy(isInitialDataLoaded = true, isRefreshing = false)
                    }
                }
            }
        }.also { refreshJob = it }
    }
    fun handleSimpleModeChange(enabled: Boolean) =
        updatePreference(PREF_SIMPLE_MODE, enabled) { it.copy(isSimpleMode = enabled) }

    fun dispatch(action: HomeUiAction) {
        when (action) {
            HomeUiAction.AwaitInitialData -> viewModelScope.launch { awaitInitialData() }
            is HomeUiAction.Refresh -> refreshData(action.showIndicator)
            is HomeUiAction.SetSimpleMode -> handleSimpleModeChange(action.enabled)
            is HomeUiAction.Reboot -> viewModelScope.launch {
                reboot(action.reason).onFailure {
                    mutableEvents.tryEmit(HomeUiEvent.Error(it.message.orEmpty()))
                }
            }
        }
    }

    private fun refreshManagerUpdates(force: Boolean) {
        val stableEnabled = getBooleanPreference(PREF_CHECK_UPDATE, true)
        val betaEnabled = getBooleanPreference(PREF_CHECK_BETA_UPDATE, true)
        if (!stableEnabled && !betaEnabled) {
            homeStateRepository.update {
                it.copy(
                    stableManagerUpdate = null,
                    betaManagerUpdate = null,
                    isBetaManagerUpdateCheckFailed = false,
                )
            }
            return
        }
        if (!isNetworkAvailable()) return
        if (!force && updateJob?.isActive == true) return
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            if (stableEnabled) launch {
                val update =
                    runCatching { checkManagerUpdate(ManagerUpdateChannel.STABLE) }.getOrNull()
                homeStateRepository.update { it.copy(stableManagerUpdate = update) }
            }
            if (betaEnabled) launch {
                val result = runCatching { checkManagerUpdate(ManagerUpdateChannel.BETA) }
                homeStateRepository.update {
                    it.copy(
                        betaManagerUpdate = result.getOrNull(),
                        isBetaManagerUpdateCheckFailed = result.isFailure,
                    )
                }
            }
        }
    }

    private fun applyUserSettings() {
        homeStateRepository.update {
            it.copy(
                isSimpleMode = getBooleanPreference(PREF_SIMPLE_MODE),
            )
        }
    }

    private fun updatePreference(
        key: String,
        value: Boolean,
        reducer: (HomeUiState) -> HomeUiState,
    ) {
        setBooleanPreference(key, value)
        homeStateRepository.update(reducer)
    }

    private fun completedJob(): Job = Job().apply { complete() }

    private companion object {
        const val PREF_CHECK_UPDATE = "check_update"
        const val PREF_CHECK_BETA_UPDATE = "check_beta_update"
        const val PREF_SIMPLE_MODE = "is_simple_mode"
    }
}
