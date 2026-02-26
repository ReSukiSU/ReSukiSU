package com.resukisu.resukisu.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.system.Os
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.KernelVersion
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.getKernelVersion
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.util.InfoCardItem
import com.resukisu.resukisu.ui.susfs.util.SuSFSManager
import com.resukisu.resukisu.ui.util.checkNewVersion
import com.resukisu.resukisu.ui.util.getKpmModuleCount
import com.resukisu.resukisu.ui.util.getKpmVersion
import com.resukisu.resukisu.ui.util.getMetaModuleImplement
import com.resukisu.resukisu.ui.util.getModuleCount
import com.resukisu.resukisu.ui.util.getSELinuxStatus
import com.resukisu.resukisu.ui.util.getSuSFSFeatures
import com.resukisu.resukisu.ui.util.getSuSFSStatus
import com.resukisu.resukisu.ui.util.getSuSFSVersion
import com.resukisu.resukisu.ui.util.getSuperuserCount
import com.resukisu.resukisu.ui.util.getZygiskImplement
import com.resukisu.resukisu.ui.util.module.LatestVersionInfo
import com.resukisu.resukisu.ui.util.rootAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {

    // 系统状态
    data class SystemStatus(
        val isManager: Boolean = false,
        val ksuVersion: Int? = null,
        val ksuFullVersion : String? = null,
        val lkmMode: Boolean? = null,
        val kernelVersion: KernelVersion = getKernelVersion(),
        val isRootAvailable: Boolean = false,
        val isKpmConfigured: Boolean = false,
        val requireNewKernel: Boolean = false
    )

    // 系统信息
    data class SystemInfo(
        val kernelRelease: String = "",
        val androidVersion: String = "",
        val deviceModel: String = "",
        val managerVersion: Pair<String, Long> = Pair("", 0L),
        val selinuxStatus: String = "",
        val kpmVersion: String = "",
        val susfsEnabled: Boolean = false,
        val susfsVersionSupported: Boolean = false,
        val susfsVersion: String = "",
        val susfsFeatures: String = "",
        val superuserCount: Int = 0,
        val moduleCount: Int = 0,
        val kpmModuleCount: Int = 0,
        val managersList: Natives.ManagersList? = null,
        val isDynamicSignEnabled: Boolean = false,
        val zygiskImplement: String = "",
        val metaModuleImplement: String = ""
    )

    // 状态变量
    var systemStatus by mutableStateOf(SystemStatus())
        private set

    var systemInfo by mutableStateOf(SystemInfo())
        private set

    var latestVersionInfo by mutableStateOf(LatestVersionInfo())
        private set

    var hiddenItems by mutableStateOf(emptySet<String>())
        private set

    // InfoCard 编辑模式
    var isEditingInfoCard by mutableStateOf(false)
    var editingHiddenItems by mutableStateOf(emptySet<String>())

    fun startEditingInfoCard() {
        editingHiddenItems = hiddenItems.toSet()
        isEditingInfoCard = true
    }

    fun toggleEditingItem(item: InfoCardItem) {
        val newSet = editingHiddenItems.toMutableSet()
        if (item.key in newSet) newSet.remove(item.key) else newSet.add(item.key)
        editingHiddenItems = newSet
    }

    fun saveInfoCardEdits(context: Context) {
        hiddenItems = editingHiddenItems.toSet()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putStringSet("hidden_info_items", hiddenItems)
        }
        isEditingInfoCard = false
    }

    fun cancelEditingInfoCard() {
        isEditingInfoCard = false
    }

    fun isVisible(item: InfoCardItem) = item.key !in hiddenItems

    var isCoreDataLoaded by mutableStateOf(false)
        private set
    var isExtendedDataLoaded by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    private var loadingJobs = mutableListOf<Job>()

    fun loadUserSettings(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            migrateOldSettings(settingsPrefs)
            hiddenItems = settingsPrefs.getStringSet("hidden_info_items", emptySet()) ?: emptySet()
        }
    }

    private fun migrateOldSettings(prefs: SharedPreferences) {
        if (!prefs.contains("is_simple_mode")) return
        val hidden = mutableSetOf<String>()
        if (prefs.getBoolean("is_simple_mode", false)) {
            hidden.addAll(listOf(
                "android_version", "hook_type", "active_managers",
                "zygisk_implement", "meta_module", "kpm_version", "susfs_version"
            ))
        }
        if (prefs.getBoolean("is_hide_version", false)) hidden.add("version_in_status")
        if (prefs.getBoolean("is_hide_susfs_status", false)) hidden.add("susfs_version")
        if (prefs.getBoolean("is_hide_zygisk_Implement", false)) hidden.add("zygisk_implement")
        if (prefs.getBoolean("is_hide_meta_module_Implement", false)) hidden.add("meta_module")
        if (prefs.getBoolean("show_kpm_info", false)) hidden.add("kpm_version")
        if (prefs.getBoolean("is_hide_link_card", false)) hidden.add("link_cards")
        if (prefs.getBoolean("is_hide_tag_row", false)) hidden.add("tag_row")
        if (prefs.getBoolean("is_kernel_simple_mode", false)) hidden.add("kernel_simple")
        prefs.edit {
            putStringSet("hidden_info_items", hidden)
            remove("is_simple_mode"); remove("is_hide_version")
            remove("is_hide_other_info"); remove("is_hide_susfs_status")
            remove("is_hide_zygisk_Implement"); remove("is_hide_meta_module_Implement")
            remove("show_kpm_info"); remove("is_hide_link_card")
            remove("is_hide_tag_row"); remove("is_kernel_simple_mode")
        }
    }

    fun loadCoreData() {
        if (isCoreDataLoaded) return

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val kernelVersion = getKernelVersion()
                val isManager = try {
                    Natives.isManager
                } catch (_: Exception) {
                    false
                }

                val ksuVersion = if (isManager) Natives.version else null

                val fullVersion = try {
                    Natives.getFullVersion()
                } catch (_: Exception) {
                    "Unknown"
                }

                val ksuFullVersion = if (InfoCardItem.KERNEL_SIMPLE.key in hiddenItems) {
                    try {
                        val startIndex = fullVersion.indexOf('v')
                        if (startIndex >= 0) {
                            val endIndex = fullVersion.indexOf('-', startIndex)
                            val versionStr = if (endIndex > startIndex) {
                                fullVersion.substring(startIndex, endIndex)
                            } else {
                                fullVersion.substring(startIndex)
                            }
                            val numericVersion = "v" + (Regex("""\d+(\.\d+)*""").find(versionStr)?.value ?: versionStr)
                            numericVersion
                        } else {
                            fullVersion
                        }
                    } catch (_: Exception) {
                        fullVersion
                    }
                } else {
                    fullVersion
                }

                val lkmMode = ksuVersion?.let {
                    if (kernelVersion.isGKI()) Natives.isLkmMode else null
                }

                val isRootAvailable = try {
                    rootAvailable()
                } catch (_: Exception) {
                    false
                }

                val isKpmConfigured = try {
                    Natives.isKPMEnabled()
                } catch (_: Exception) {
                    false
                }

                val requireNewKernel = try {
                    isManager && Natives.requireNewKernel()
                } catch (_: Exception) {
                    false
                }

                systemStatus = SystemStatus(
                    isManager = isManager,
                    ksuVersion = ksuVersion,
                    ksuFullVersion = ksuFullVersion,
                    lkmMode = lkmMode,
                    kernelVersion = kernelVersion,
                    isRootAvailable = isRootAvailable,
                    isKpmConfigured = isKpmConfigured,
                    requireNewKernel = requireNewKernel
                )

                isCoreDataLoaded = true
            } catch (_: Exception) {
            }
        }
        loadingJobs.add(job)
    }

    fun loadExtendedData(context: Context) {
        if (isExtendedDataLoaded) return

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val basicInfo = loadBasicSystemInfo(context)
                systemInfo = systemInfo.copy(
                    kernelRelease = basicInfo.first,
                    androidVersion = basicInfo.second,
                    deviceModel = basicInfo.third,
                    managerVersion = basicInfo.fourth,
                    selinuxStatus = basicInfo.fifth
                )

                val moduleInfo = loadModuleInfo()
                systemInfo = systemInfo.copy(
                    kpmVersion = moduleInfo.first,
                    superuserCount = moduleInfo.second,
                    moduleCount = moduleInfo.third,
                    kpmModuleCount = moduleInfo.fourth,
                    zygiskImplement = moduleInfo.fifth,
                    metaModuleImplement = moduleInfo.sixth
                )

                val susfsInfo = loadSuSFSInfo()
                systemInfo = systemInfo.copy(
                    susfsEnabled = susfsInfo.first,
                    susfsVersionSupported = susfsInfo.first && SuSFSManager.isBinaryAvailable(
                        context
                    ),
                    susfsVersion = susfsInfo.second,
                    susfsFeatures = susfsInfo.third,
                )

                val managerInfo = loadManagerInfo()
                systemInfo = systemInfo.copy(
                    managersList = managerInfo.first,
                    isDynamicSignEnabled = managerInfo.second
                )

                isExtendedDataLoaded = true
            } catch (_: Exception) {}
        }
        loadingJobs.add(job)
    }

    fun refreshData(
        context: Context,
        refreshUI: Boolean = false
    ) {
        viewModelScope.launch {
            if (refreshUI)
                isRefreshing = true

            try {
                // 取消正在进行的加载任务
                loadingJobs.forEach { it.cancel() }
                loadingJobs.clear()

                // 重置状态
                if (refreshUI) {
                    isCoreDataLoaded = false
                    isExtendedDataLoaded = false
                }

                // 重新加载用户设置
                loadUserSettings(context)

                // 重新加载核心数据
                loadCoreData()

                // 重新加载扩展数据
                loadExtendedData(context)

                // 检查更新
                val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val checkUpdate = settingsPrefs.getBoolean("check_update", true)
                if (checkUpdate) {
                    try {
                        val newVersionInfo = withContext(Dispatchers.IO) {
                            checkNewVersion()
                        }
                        latestVersionInfo = newVersionInfo
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
                // 静默处理错误
            } finally {
                isRefreshing = false
            }
        }
    }

    private suspend fun loadBasicSystemInfo(context: Context): Tuple5<String, String, String, Pair<String, Long>, String> {
        return withContext(Dispatchers.IO) {
            val uname = try {
                Os.uname()
            } catch (_: Exception) {
                null
            }

            val deviceModel = try {
                getDeviceModel()
            } catch (_: Exception) {
                "Unknown"
            }

            val managerVersion = try {
                getManagerVersion(context)
            } catch (_: Exception) {
                Pair("Unknown", 0L)
            }

            val seLinuxStatus = try {
                getSELinuxStatus(ksuApp.applicationContext)
            } catch (_: Exception) {
                "Unknown"
            }

            Tuple5(
                uname?.release ?: "Unknown",
                Build.VERSION.RELEASE ?: "Unknown",
                deviceModel,
                managerVersion,
                seLinuxStatus
            )
        }
    }

    private suspend fun loadModuleInfo(): Tuple6<String, Int, Int, Int, String, String> {
        return withContext(Dispatchers.IO) {
            val kpmVersion = try {
                getKpmVersion()
            } catch (_: Exception) {
                "Unknown"
            }

            val superuserCount = try {
                getSuperuserCount()
            } catch (_: Exception) {
                0
            }

            val moduleCount = try {
                getModuleCount()
            } catch (_: Exception) {
                0
            }

            val kpmModuleCount = try {
                getKpmModuleCount()
            } catch (_: Exception) {
                0
            }

            val zygiskImplement = try {
                getZygiskImplement()
            } catch (_: Exception) {
                "None"
            }

            val metaModuleImplement = try {
                getMetaModuleImplement()
            } catch (_: Exception) {
                "None"
            }

            Tuple6(kpmVersion, superuserCount, moduleCount, kpmModuleCount, zygiskImplement, metaModuleImplement)
        }
    }

    private suspend fun loadSuSFSInfo(): Triple<Boolean, String, String> {
        return withContext(Dispatchers.IO) {
            val susfsEnabled = try {
                getSuSFSStatus().equals("true", ignoreCase = true)
            } catch (_: Exception) {
                false
            }

            if (!susfsEnabled) {
                return@withContext Triple(false, "", "")
            }

            val susfsVersion = try {
                getSuSFSVersion()
            } catch (_: Exception) {
                ""
            }

            if (susfsVersion.isEmpty()) {
                return@withContext Triple(true, "", "")
            }

            val susfsFeatures = try {
                getSuSFSFeatures()
            } catch (_: Exception) {
                ""
            }

            Triple(true, susfsVersion, susfsFeatures)
        }
    }

    private suspend fun loadManagerInfo(): Pair<Natives.ManagersList?, Boolean> {
        return withContext(Dispatchers.IO) {
            val dynamicSignConfig = try {
                Natives.getDynamicManager()
            } catch (_: Exception) {
                null
            }

            val isDynamicSignEnabled = try {
                dynamicSignConfig?.isValid() == true
            } catch (_: Exception) {
                false
            }

            val managersList = try {
                Natives.getManagersList()
            } catch (_: Exception) {
                null
            }

            Pair(managersList, isDynamicSignEnabled)
        }
    }

    @SuppressLint("PrivateApi")
    private fun getDeviceModel(): String {
        return try {
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
                try {
                    val marketName = getMethod.invoke(null, key, "") as String
                    if (marketName.isNotEmpty()) {
                        result = marketName
                        break
                    }
                } catch (_: Exception) {
                }
            }
            result
        } catch (

            _: Exception) {
            getDeviceInfo()
        }
    }

    private fun getDeviceInfo(): String {
        return try {
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
        } catch (_: Exception) {
            "Unknown Device"
        }
    }

    private fun getManagerVersion(context: Context): Pair<String, Long> {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo)
            val versionName = packageInfo.versionName ?: "Unknown"
            Pair(versionName, versionCode)
        } catch (_: Exception) {
            Pair("Unknown", 0L)
        }
    }

    data class Tuple6<T1, T2, T3, T4, T5, T6>(
        val first: T1,
        val second: T2,
        val third: T3,
        val fourth: T4,
        val fifth: T5,
        val sixth: T6
    )

    data class Tuple5<T1, T2, T3, T4, T5>(
        val first: T1,
        val second: T2,
        val third: T3,
        val fourth: T4,
        val fifth: T5
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