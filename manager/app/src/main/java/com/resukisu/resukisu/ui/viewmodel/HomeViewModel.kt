package com.resukisu.resukisu.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.system.Os
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.KernelVersion
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.data.appPreferences
import com.resukisu.resukisu.getKernelVersion
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.susfs.util.SuSFSManager
import com.resukisu.resukisu.ui.util.downloader.checkNewVersion
import com.resukisu.resukisu.ui.util.getMetaModuleImplement
import com.resukisu.resukisu.ui.util.getModuleCount
import com.resukisu.resukisu.ui.util.getSELinuxStatus
import com.resukisu.resukisu.ui.util.getSuSFSFeatures
import com.resukisu.resukisu.ui.util.getSuSFSStatus
import com.resukisu.resukisu.ui.util.getSuSFSVersion
import com.resukisu.resukisu.ui.util.getSuperuserCount
import com.resukisu.resukisu.ui.util.getZygiskImplement
import com.resukisu.resukisu.ui.util.isOfficialSignature
import com.resukisu.resukisu.ui.util.isSELinuxPermissive
import com.resukisu.resukisu.ui.util.module.LatestVersionInfo
import com.resukisu.resukisu.ui.util.rootAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val systemStatus: HomeViewModel.SystemStatus = HomeViewModel.SystemStatus(),
    val systemInfo: HomeViewModel.SystemInfo = HomeViewModel.SystemInfo(),
    val latestVersionInfo: LatestVersionInfo = LatestVersionInfo(),
    val isSimpleMode: Boolean = false,
    val isHideVersion: Boolean = false,
    val isHideOtherInfo: Boolean = false,
    val isHideSusfsStatus: Boolean = false,
    val isHideZygiskImplement: Boolean = false,
    val isHideMetaModuleImplement: Boolean = false,
    val isHideLinkCard: Boolean = false,
    val isInitialDataLoaded: Boolean = false,
    val isCoreDataLoaded: Boolean = false,
    val isExtendedDataLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
)

class HomeViewModel : ViewModel() {

    data class SystemStatus(
        val isManager: Boolean = false,
        val ksuVersion: Int? = null,
        val managerUAPIVersion: Int = 1,
        val kernelUAPIVersion: Int? = 1,
        val ksuFullVersion: String? = null,
        val lkmMode: Boolean? = null,
        val kernelVersion: KernelVersion = getKernelVersion(),
        val isRootAvailable: Boolean = false,
        val requireNewKernel: Boolean = false,
        val uapiMismatch: Boolean = false,
        val isSELinuxPermissive: Boolean = false,
        val isOfficialSignature: Boolean = true,
        val kernelPatchImplement: Natives.KernelPatchImplement = Natives.KernelPatchImplement.NO_KERNEL_PATCH_SUPPORT,
    )

    data class SystemInfo(
        val kernelRelease: String = "",
        val androidVersion: String = "",
        val deviceModel: String = "",
        val managerVersion: Triple<String, Int, Int> = Triple("", 0, 0),
        val selinuxStatus: String = "",
        val susfsEnabled: Boolean = false,
        val susfsVersionSupported: Boolean = false,
        val susfsVersion: String = "",
        val susfsFeatures: String = "",
        val superuserCount: Int = 0,
        val moduleCount: Int = 0,
        val managersList: Natives.ManagersList? = null,
        val isDynamicSignEnabled: Boolean = false,
        val zygiskImplement: String = "",
        val metaModuleImplement: String = "",
        val seccompStatus: Int = -1,
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val loadingJobs = mutableListOf<Job>()
    private var dataLoadJob: Job? = null

    private fun completedJob(): Job = Job().apply { complete() }

    suspend fun awaitInitialData(context: Context) {
        refreshData(context, refreshUI = false).join()
    }

    private fun loadCoreData(force: Boolean = false): Job? {
        if (!force && _uiState.value.isCoreDataLoaded) return null

        val job = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val kernelVersion = getKernelVersion()
                val isManager = runCatching { Natives.isManager }.getOrDefault(false)
                val ksuVersion = if (isManager) Natives.version else null
                val kernelUAPIVersion = if (isManager) Natives.kernelUAPIVersion else null
                val managerUAPIVersion = Natives.managerUAPIVersion
                val fullVersion = runCatching { Natives.getFullVersion() }.getOrDefault("Unknown")
                val lkmMode = ksuVersion?.let {
                    if (kernelVersion.isGKI()) Natives.isLkmMode else null
                }
                val status = SystemStatus(
                    isManager = isManager,
                    ksuVersion = ksuVersion,
                    ksuFullVersion = "$fullVersion (${Natives.version}/${kernelUAPIVersion})",
                    lkmMode = lkmMode,
                    kernelVersion = kernelVersion,
                    isRootAvailable = runCatching { rootAvailable() }.getOrDefault(false),
                    requireNewKernel = runCatching {
                        isManager && Natives.requireNewKernel()
                    }.getOrDefault(false),
                    uapiMismatch = runCatching {
                        isManager && Natives.checkUAPIMismatch()
                    }.getOrDefault(false),
                    kernelUAPIVersion = kernelUAPIVersion,
                    managerUAPIVersion = managerUAPIVersion,
                    isSELinuxPermissive = runCatching { isSELinuxPermissive() }.getOrDefault(false),
                    isOfficialSignature = runCatching { isOfficialSignature() }.getOrDefault(false),
                    kernelPatchImplement = Natives.getKernelPatchImplement(),
                )
                _uiState.update {
                    it.copy(
                        systemStatus = status,
                        isCoreDataLoaded = true,
                    )
                }
            }
        }
        loadingJobs.add(job)
        return job
    }

    private fun loadExtendedData(context: Context, force: Boolean = false): Job? {
        if (!force && _uiState.value.isExtendedDataLoaded) return null

        val job = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val (kernelRelease, androidVersion, deviceModel, managerVersion, selinuxStatus, seccompStatus) =
                    loadBasicSystemInfo(context)
                _uiState.update {
                    it.copy(
                        systemInfo = it.systemInfo.copy(
                            kernelRelease = kernelRelease,
                            androidVersion = androidVersion,
                            deviceModel = deviceModel,
                            managerVersion = managerVersion,
                            selinuxStatus = selinuxStatus,
                            seccompStatus = seccompStatus,
                        )
                    )
                }

                if (!_uiState.value.isSimpleMode) {
                    val moduleInfo = loadModuleInfo()
                    _uiState.update {
                        it.copy(
                            systemInfo = it.systemInfo.copy(
                                superuserCount = moduleInfo.first,
                                moduleCount = moduleInfo.second,
                                zygiskImplement = moduleInfo.third,
                                metaModuleImplement = moduleInfo.fourth,
                            )
                        )
                    }
                }

                if (!_uiState.value.isHideSusfsStatus) {
                    val susfsInfo = loadSuSFSInfo()
                    _uiState.update {
                        it.copy(
                            systemInfo = it.systemInfo.copy(
                                susfsEnabled = susfsInfo.first,
                                susfsVersionSupported = susfsInfo.first && SuSFSManager.isBinaryAvailable(
                                    context
                                ),
                                susfsVersion = susfsInfo.second,
                                susfsFeatures = susfsInfo.third,
                            )
                        )
                    }
                }

                val managerInfo = loadManagerInfo()
                _uiState.update {
                    it.copy(
                        systemInfo = it.systemInfo.copy(
                            managersList = managerInfo.first,
                            isDynamicSignEnabled = managerInfo.second,
                        ),
                        isExtendedDataLoaded = true,
                    )
                }
            }
        }
        loadingJobs.add(job)
        return job
    }

    private fun updateBooleanPref(
        context: Context,
        key: String,
        value: Boolean,
        reducer: (HomeUiState) -> HomeUiState,
    ) {
        context.appPreferences.putBoolean(key, value)
        _uiState.update(reducer)
    }

    fun handleHideSusfsStatusChange(newValue: Boolean) {
        handleHideSusfsStatusChange(ksuApp, newValue)
    }

    fun handleHideZygiskImplementChange(newValue: Boolean) {
        handleHideZygiskImplementChange(ksuApp, newValue)
    }

    fun handleHideMetaModuleImplementChange(newValue: Boolean) {
        handleHideMetaModuleImplementChange(ksuApp, newValue)
    }

    fun handleHideLinkCardChange(newValue: Boolean) {
        handleHideLinkCardChange(ksuApp, newValue)
    }

    fun handleHideLinkCardChange(context: Context, newValue: Boolean) {
        updateBooleanPref(
            context,
            "is_hide_link_card",
            newValue
        ) { it.copy(isHideLinkCard = newValue) }
    }

    fun handleHideMetaModuleImplementChange(context: Context, newValue: Boolean) {
        updateBooleanPref(context, "is_hide_meta_module_Implement", newValue) {
            it.copy(isHideMetaModuleImplement = newValue)
        }
    }

    fun handleHideZygiskImplementChange(context: Context, newValue: Boolean) {
        updateBooleanPref(context, "is_hide_zygisk_Implement", newValue) {
            it.copy(isHideZygiskImplement = newValue)
        }
    }

    fun handleHideSusfsStatusChange(context: Context, newValue: Boolean) {
        updateBooleanPref(
            context,
            "is_hide_susfs_status",
            newValue
        ) { it.copy(isHideSusfsStatus = newValue) }
    }

    fun handleSimpleModeChange(context: Context, newValue: Boolean) {
        updateBooleanPref(context, "is_simple_mode", newValue) { it.copy(isSimpleMode = newValue) }
    }

    fun handleHideVersionChange(newValue: Boolean) {
        handleHideVersionChange(ksuApp, newValue)
    }

    fun handleHideVersionChange(context: Context, newValue: Boolean) {
        updateBooleanPref(
            context,
            "is_hide_version",
            newValue
        ) { it.copy(isHideVersion = newValue) }
    }

    fun handleHideOtherInfoChange(newValue: Boolean) {
        handleHideOtherInfoChange(ksuApp, newValue)
    }

    fun handleHideOtherInfoChange(context: Context, newValue: Boolean) {
        updateBooleanPref(context, "is_hide_other_info", newValue) {
            it.copy(isHideOtherInfo = newValue)
        }
    }
    fun refreshData(
        context: Context,
        refreshUI: Boolean = false
    ): Job {
        if (!refreshUI) {
            dataLoadJob?.takeIf { it.isActive }?.let { return it }
            if (_uiState.value.isInitialDataLoaded) return completedJob()
        }

        if (context.appPreferences.getBoolean("check_update", true)) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val versionInfo = checkNewVersion()
                    _uiState.update {
                        it.copy(latestVersionInfo = versionInfo)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val job = viewModelScope.launch(Dispatchers.IO) {
            if (refreshUI) {
                _uiState.update {
                    it.copy(isRefreshing = true)
                }
            }

            try {
                loadingJobs.forEach { it.cancel() }
                loadingJobs.clear()

                val userSettings = async {
                    applyUserSettings(context)
                }
                val coreJob = loadCoreData(force = refreshUI)
                val extendedJob = loadExtendedData(context, force = refreshUI)

                coreJob?.join()
                extendedJob?.join()
                userSettings.join()
            } finally {
                _uiState.update {
                    it.copy(
                        isInitialDataLoaded = true,
                        isRefreshing = false,
                    )
                }
            }
        }

        dataLoadJob = job
        return job
    }

    private fun applyUserSettings(context: Context) {
        val settingsPrefs = context.appPreferences
        _uiState.update {
            it.copy(
                isSimpleMode = settingsPrefs.getBoolean("is_simple_mode", false),
                isHideVersion = settingsPrefs.getBoolean("is_hide_version", false),
                isHideOtherInfo = settingsPrefs.getBoolean("is_hide_other_info", false),
                isHideSusfsStatus = settingsPrefs.getBoolean("is_hide_susfs_status", false),
                isHideLinkCard = settingsPrefs.getBoolean("is_hide_link_card", false),
                isHideZygiskImplement = settingsPrefs.getBoolean("is_hide_zygisk_Implement", false),
                isHideMetaModuleImplement = settingsPrefs.getBoolean(
                    "is_hide_meta_module_Implement",
                    false
                ),
            )
        }
    }

    private suspend fun loadBasicSystemInfo(context: Context): Tuple6<String, String, String, Triple<String, Int, Int>, String, Int> {
        return withContext(Dispatchers.IO) {
            val uname = runCatching { Os.uname() }.getOrNull()
            Tuple6(
                uname?.release ?: "Unknown",
                Build.VERSION.RELEASE ?: "Unknown",
                runCatching { getDeviceModel() }.getOrDefault("Unknown"),
                runCatching { getManagerVersion() }.getOrDefault(Triple("Unknown", 0, 0)),
                runCatching { getSELinuxStatus(ksuApp.applicationContext) }.getOrDefault("Unknown"),
                runCatching { Os.prctl(21, 0, 0, 0, 0) }.getOrDefault(-1),
            )
        }
    }

    private suspend fun loadModuleInfo(): Tuple4<Int, Int, String, String> {
        return withContext(Dispatchers.IO) {
            Tuple4(
                runCatching { getSuperuserCount() }.getOrDefault(0),
                runCatching { getModuleCount() }.getOrDefault(0),
                runCatching { getZygiskImplement() }.getOrDefault("None"),
                runCatching { getMetaModuleImplement() }.getOrDefault("None"),
            )
        }
    }

    private suspend fun loadSuSFSInfo(): Triple<Boolean, String, String> {
        return withContext(Dispatchers.IO) {
            val susfsEnabled = runCatching {
                getSuSFSStatus().equals("true", ignoreCase = true)
            }.getOrDefault(false)

            if (!susfsEnabled) {
                return@withContext Triple(false, "", "")
            }

            val susfsVersion = runCatching { getSuSFSVersion() }.getOrDefault("")
            if (susfsVersion.isEmpty()) {
                return@withContext Triple(true, "", "")
            }

            Triple(
                true,
                susfsVersion,
                runCatching { getSuSFSFeatures() }.getOrDefault(""),
            )
        }
    }

    private suspend fun loadManagerInfo(): Pair<Natives.ManagersList?, Boolean> {
        return withContext(Dispatchers.IO) {
            val dynamicSignConfig = runCatching { Natives.getDynamicManager() }.getOrNull()
            Pair(
                runCatching { Natives.getManagersList() }.getOrNull(),
                runCatching { dynamicSignConfig?.isValid() == true }.getOrDefault(false),
            )
        }
    }

    @SuppressLint("PrivateApi")
    private fun getDeviceModel(): String {
        return runCatching {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
            val marketNameKeys = listOf(
                "ro.product.marketname",
                "ro.vendor.oplus.market.name",
                "ro.vivo.market.name",
                "ro.config.marketing_name"
            )
            var result = getDeviceInfo()
            for (key in marketNameKeys) {
                val marketName = runCatching { getMethod.invoke(null, key, "") as String }
                    .getOrDefault("")
                if (marketName.isNotEmpty()) {
                    result = marketName
                    break
                }
            }
            result
        }.getOrDefault(getDeviceInfo())
    }

    private fun getDeviceInfo(): String {
        return runCatching {
            var manufacturer = Build.MANUFACTURER ?: "Unknown"
            manufacturer = manufacturer[0].uppercaseChar().toString() + manufacturer.substring(1)

            val brand = Build.BRAND ?: ""
            if (brand.isNotEmpty() && !brand.equals(Build.MANUFACTURER, ignoreCase = true)) {
                manufacturer += " " + brand[0].uppercaseChar() + brand.substring(1)
            }

            val model = Build.MODEL ?: ""
            if (model.isNotEmpty()) {
                manufacturer += " $model "
            }

            manufacturer
        }.getOrDefault("Unknown Device")
    }

    private fun getManagerVersion(): Triple<String, Int, Int> {
        return runCatching {
            Triple(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Natives.managerUAPIVersion)
        }.getOrDefault(Triple("Unknown", 0, 0))
    }

    data class Tuple6<T1, T2, T3, T4, T5, T6>(
        val first: T1,
        val second: T2,
        val third: T3,
        val fourth: T4,
        val fifth: T5,
        val sixth: T6
    )

    data class Tuple4<T1, T2, T3, T4>(
        val first: T1,
        val second: T2,
        val third: T3,
        val fourth: T4
    )

    override fun onCleared() {
        super.onCleared()
        loadingJobs.forEach { it.cancel() }
        loadingJobs.clear()
    }
}
