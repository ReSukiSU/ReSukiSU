package com.resukisu.resukisu.ui.viewmodel

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.KsuService
import com.resukisu.resukisu.ui.util.execKsud
import com.resukisu.resukisu.ui.util.getRootShell
import com.resukisu.resukisu.ui.util.getSuSFSFeatures
import com.resukisu.resukisu.ui.util.getSuSFSSlotInfoJson
import com.resukisu.resukisu.ui.util.getSuSFSStatus
import com.resukisu.resukisu.ui.util.getSuSFSVersion
import com.resukisu.zako.IKsuInterface
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume

private const val CONFIG_PATH = "/data/adb/ksu/.susfs.json"

private val gson = GsonBuilder()
    .setPrettyPrinting()
    .create()

data class SusfsConfig(
    val common: SusfsCommonConfig = SusfsCommonConfig(),
    @SerializedName("sus_path") val susPath: SusPathConfig = SusPathConfig(),
    @SerializedName("sus_map") val susMap: List<String> = emptyList(),
    val kstat: SusfsKstatConfig = SusfsKstatConfig()
)

data class SusfsCommonConfig(
    val version: String = "default",
    val release: String = "default",
    @SerializedName("avc_spoofing") val avcSpoofing: Boolean = false,
    @SerializedName("enable_susfs_log") val enableSusfsLog: Boolean = false,
    @SerializedName("hide_sus_mnts_for_non_su_procs") val hideSusMntsForNonSuProcs: Boolean = false
)

data class SusPathConfig(
    @SerializedName("sus_path_loop") val susPathLoop: List<String> = emptyList(),
    @SerializedName("sus_path") val susPath: List<String> = emptyList()
)

data class SusfsKstatConfig(
    @SerializedName("sus_kstat") val susKstat: List<String> = emptyList(),
    @SerializedName("update_kstat") val updateKstat: List<String> = emptyList(),
    @SerializedName("full_clone") val fullClone: List<String> = emptyList(),
    val statically: List<SuSFSStaticKstatEntry> = emptyList()
)

open class SuSFSFeatureStatus(
    val key: String,
    val title: String,
    val enabled: Boolean,
    val configurable: Boolean = false,
)

data class NoNConfigurableSuSFSFeature(
    val featureKey: String,
    val featureTitle: String,
    val featureEnabled: Boolean,
) : SuSFSFeatureStatus(featureKey, featureTitle, featureEnabled, false)

abstract class ConfigurableSuSFSFeature(
    featureKey: String,
    featureTitle: String,
    featureEnabled: Boolean,
) : SuSFSFeatureStatus(featureKey, featureTitle, featureEnabled, true) {
    abstract fun onCheckedChange(checked: Boolean)
}

data class SuSFSStaticKstatEntry(
    val path: String = "",
    val ino: String = "default",
    val dev: String = "default",
    val nlink: String = "default",
    val size: String = "default",
    val atime: String = "default",
    @SerializedName("atime_nsec") val atimeNsec: String = "default",
    val mtime: String = "default",
    @SerializedName("mtime_nsec") val mtimeNsec: String = "default",
    val ctime: String = "default",
    @SerializedName("ctime_nsec") val ctimeNsec: String = "default",
    val blocks: String = "default",
    val blksize: String = "default",
) {
    @Transient
    val summary: String = "ino=$ino, dev=$dev, size=$size"
}

data class SuSFSAppEntry(
    val packageName: String,
    val label: String,
    val packageInfo: PackageInfo? = null,
)

data class SuSFSSlotInfo(
    @SerializedName("slot_name") val slotName: String = "",
    val uname: String = "",
    @SerializedName("build_time") val buildTime: String = "",
)

data class SuSFSUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val enabled: Boolean = false,
    val versionText: String = "",
    val unameValue: String = "default",
    val buildTimeValue: String = "default",
    val hideSuSMntsForNonSUProcs: Boolean = false,
    val hideMountsControlSupported: Boolean = true,
    val susfsLogEnabled: Boolean = false,
    val avcLogSpoofing: Boolean = false,
    val susPaths: List<String> = emptyList(),
    val susLoopPaths: List<String> = emptyList(),
    val susMaps: List<String> = emptyList(),
    val kstatPaths: List<String> = emptyList(),
    val kstatUpdatedPaths: List<String> = emptyList(),
    val kstatFullClonePaths: List<String> = emptyList(),
    val staticKstatEntries: List<SuSFSStaticKstatEntry> = emptyList(),
    val featureStatus: List<SuSFSFeatureStatus> = emptyList(),
    val loadError: String? = null,
)

class SuSFSScreenViewModel : ViewModel() {
    var uiState by mutableStateOf(SuSFSUiState())
        private set

    var snackbarText by mutableStateOf<String?>(null)
        private set

    var slotInfoList by mutableStateOf<List<SuSFSSlotInfo>>(emptyList())
        private set

    var currentActiveSlot by mutableStateOf("")
        private set

    var slotInfoLoading by mutableStateOf(true)
        private set

    private var serviceConnection: ServiceConnection? = null

    init {
        refresh()
    }

    fun consumeToastMessage() {
        snackbarText = null
    }

    fun postToast(message: String) {
        snackbarText = message
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val shouldShowLoading = uiState.isLoading
            uiState = uiState.copy(
                isLoading = shouldShowLoading,
                isRefreshing = !shouldShowLoading,
                loadError = null,
            )

            runCatching { loadState() }
                .onSuccess { newState ->
                    uiState = newState.copy(
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
                .onFailure {
                    uiState = uiState.copy(
                        isLoading = false,
                        isRefreshing = false,
                        loadError = it.message ?: ksuApp.getString(R.string.operation_failed),
                    )
                }
        }
    }

    fun setUnameAndBuildTime(unameValue: String, buildTimeValue: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val uname = unameValue.trim().ifEmpty { "default" }
            val buildTime = buildTimeValue.trim().ifEmpty { "default" }

            if (uname == uiState.unameValue && buildTime == uiState.buildTimeValue) {
                return@launch
            }
            if (uname == "default" && buildTime == "default") {
                val success = runCommand("del_uname", showSuccessSnackbar = false)
                if (success) postToast(ksuApp.getString(R.string.susfs_uname_build_time_reset))
                return@launch
            }
            val success = runCommand("set_uname ${shellQuote(uname)} ${shellQuote(buildTime)}")
            if (success) {
                postToast(ksuApp.getString(R.string.susfs_uname_build_time_updated))
            }
        }
    }

    fun useSlotUname(uname: String) {
        setUnameAndBuildTime(uname, uiState.buildTimeValue)
    }

    fun useSlotBuildTime(buildTime: String) {
        setUnameAndBuildTime(uiState.unameValue, buildTime)
    }

    fun setAvcLogSpoofing(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCommand("enable_avc_log_spoofing ${if (enabled) 1 else 0}")
        }
    }

    fun setHideSusMountsForNonSUProcs(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = runCommand("hide_sus_mnts_for_non_su_procs ${if (enabled) 1 else 0}")

            if (success) {
                snackbarText = ksuApp.getString(
                    if (enabled) R.string.susfs_hide_mounts_all_enabled
                    else R.string.susfs_hide_mounts_all_disabled
                )
                uiState = uiState.copy(
                    hideSuSMntsForNonSUProcs = enabled,
                    hideMountsControlSupported = true,
                )
                return@launch
            }

            snackbarText = ksuApp.getString(R.string.feature_status_unsupported_summary)
            uiState = uiState.copy(hideMountsControlSupported = false)
        }
    }

    fun setSusfsLogEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = runCommand("enable_log ${if (enabled) 1 else 0}")
            if (success) {
                snackbarText = ksuApp.getString(
                    if (enabled) R.string.susfs_log_enabled else R.string.susfs_log_disabled
                )
                uiState = uiState.copy(susfsLogEnabled = enabled)
                postToast(ksuApp.getString(R.string.reboot_to_apply))
            }
        }
    }

    fun addAppPaths(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var anySuccess = false
            packageNames.forEach { packageName ->
                val candidates = setOf(
                    // FIXME Use Environment.getExternalStorageDirectory() and use reflection get current userId to replace hardcode user 0
                    // And, I don't know there really need or not, users can just enable `persist.sys.vold_app_data_isolation_enabled` property,right?
                    "/sdcard/Android/data/$packageName",
                    "/data/media/0/Android/data/$packageName"
                )

                candidates.forEach { path ->
                    if (runCommand(
                            "add_sus_path ${shellQuote(path)}",
                            showSuccessSnackbar = false
                        )
                    ) {
                        anySuccess = true
                    }
                }
            }
            if (anySuccess) {
                snackbarText = ksuApp.getString(R.string.kpm_control_success)
                refresh()
            }
        }
    }

    fun backupConfig(outputStream: OutputStream) =
        viewModelScope.launch(Dispatchers.IO) {
            outputStream.use { os ->
                SuFileInputStream.open(SuFile(CONFIG_PATH)).use { it.copyTo(os) }
            }
        }

    fun restoreConfig(inputStream: InputStream, onFinish: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes =
                runCatching { inputStream.use { it.readBytes() } }.getOrDefault(ByteArray(0))
            if (bytes.isEmpty()) {
                onFinish(false)
                return@launch
            }

            val isValid = runCatching {
                val jsonString = String(bytes, Charsets.UTF_8)
                gson.fromJson(jsonString, SusfsConfig::class.java) != null
            }.getOrDefault(false)

            if (!isValid) {
                onFinish(false)
                return@launch
            }

            val writeOk = runCatching {
                SuFileOutputStream.open(SuFile(CONFIG_PATH)).use { os ->
                    bytes.inputStream().use { it.copyTo(os) }
                }
                true
            }.getOrDefault(false)

            if (writeOk) refresh()
            onFinish(writeOk)
        }
    }

    fun resetAllSusfsConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val defaultConfigStr = gson.toJson(SusfsConfig())
            val reset = runCatching {
                SuFileOutputStream.open(SuFile(CONFIG_PATH)).use { os ->
                    os.write(defaultConfigStr.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                true
            }.getOrDefault(false)

            if (reset) {
                refresh()
                postToast(ksuApp.getString(R.string.susfs_reset_all_success))
            } else {
                postToast(ksuApp.getString(R.string.operation_failed))
            }
        }
    }

    fun refreshSlotInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            slotInfoLoading = true
            slotInfoList = runCatching { loadSlotInfo() }.getOrDefault(emptyList())
            currentActiveSlot = getActiveBootSlot()
            slotInfoLoading = false
        }
    }

    suspend fun loadSelectableApps(): List<SuSFSAppEntry> = withContext(Dispatchers.IO) {
        val superUserEntries = SuperUserViewModel.apps
            .asSequence()
            .mapNotNull { app ->
                val applicationInfo = app.packageInfo.applicationInfo ?: return@mapNotNull null
                val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystemApp || app.packageName.isBlank()) null
                else SuSFSAppEntry(
                    packageName = app.packageName,
                    label = app.label.ifBlank { app.packageName },
                    packageInfo = app.packageInfo,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()

        if (superUserEntries.isNotEmpty()) return@withContext superUserEntries

        val packageManager = ksuApp.packageManager
        val packageInfos =
            fetchInstalledPackagesViaRootService().ifEmpty { getInstalledPackagesFallback() }

        packageInfos.asSequence()
            .filter { it.packageName.isNotBlank() }
            .distinctBy { it.packageName }
            .map { packageInfo ->
                val packageInfoWithAppInfo = runCatching {
                    if (packageInfo.applicationInfo == null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            packageManager.getPackageInfo(
                                packageInfo.packageName,
                                android.content.pm.PackageManager.PackageInfoFlags.of(0)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            packageManager.getPackageInfo(packageInfo.packageName, 0)
                        }
                    } else packageInfo
                }.getOrNull() ?: packageInfo

                val label = runCatching {
                    packageInfoWithAppInfo.applicationInfo?.loadLabel(packageManager)?.toString()
                }.getOrNull()

                SuSFSAppEntry(
                    packageName = packageInfo.packageName,
                    label = label?.ifBlank { packageInfo.packageName } ?: packageInfo.packageName,
                    packageInfo = packageInfoWithAppInfo,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun getInstalledPackagesFallback(): List<PackageInfo> {
        val packageManager = ksuApp.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }
    }

    private suspend fun fetchInstalledPackagesViaRootService(): List<PackageInfo> {
        val binder = connectKsuService() ?: return emptyList()
        return try {
            val remote = IKsuInterface.Stub.asInterface(binder) ?: return emptyList()
            val total = remote.packageCount
            val pageSize = 100
            val result = mutableListOf<PackageInfo>()
            var start = 0

            while (start < total) {
                val page = remote.getPackages(start, pageSize)
                if (page.isEmpty()) break
                result += page
                start += page.size
            }
            result
        } catch (t: Throwable) {
            Log.e("SuSFSScreenViewModel", "fetchInstalledPackagesViaRootService failed", t)
            emptyList()
        } finally {
            stopKsuService()
        }
    }

    private suspend fun connectKsuService(): IBinder? = suspendCancellableCoroutine { continuation ->
        val connection = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
                serviceConnection = null
            }

            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (continuation.isActive) continuation.resume(binder)
            }
        }
        serviceConnection = connection
        val intent = Intent(ksuApp, KsuService::class.java)

        try {
            val task = RootService.bindOrTask(
                intent,
                Shell.EXECUTOR,
                connection
            )
            task?.let { Shell.getShell().execTask(it) }
        } catch (t: Throwable) {
            Log.e("SuSFSScreenViewModel", "connectKsuService failed", t)
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private fun stopKsuService() {
        if (serviceConnection == null) return
        serviceConnection?.let {
            try {
                RootService.unbind(it)
            } catch (t: Throwable) {
                Log.e("SuSFSScreenViewModel", "stopKsuService failed", t)
            } finally {
                serviceConnection = null
            }
        }
    }

    fun removeSusPath(path: String) = removePath(path) { "del_sus_path $it" }
    fun removeSusLoopPath(path: String) = removePath(path) { "del_sus_path_loop $it" }
    fun removeSusMap(path: String) = removePath(path) { "del_sus_map $it" }
    fun removeKstatPath(path: String) = removePath(path) { "del_sus_kstat $it" }
    fun addSusMap(path: String) = addPath(path) { "add_sus_map $it" }
    fun addKstatUpdatePath(path: String) = addPath(path) { "update_sus_kstat $it" }
    fun removeKstatUpdatePath(path: String) = removePath(path) { "del_update_sus_kstat $it" }
    fun addKstatFullClonePath(path: String) = addPath(path) { "update_sus_kstat_full_clone $it" }
    fun removeKstatFullClonePath(path: String) = removePath(path) { "del_sus_kstat_full_clone $it" }

    fun addStaticKstatPath(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val value = path.trim()
            if (value.isNotBlank()) {
                runCommand(
                    "add_sus_kstat_statically ${shellQuote(value)}",
                    showSuccessSnackbar = true
                )
            }
        }
    }

    fun addStaticKstatEntry(
        path: String, ino: String, dev: String, nlink: String, size: String,
        atime: String, atimeNsec: String, mtime: String, mtimeNsec: String,
        ctime: String, ctimeNsec: String, blocks: String, blksize: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedPath = normalizePathEntry(path) ?: return@launch
            val args = buildStaticKstatCommandArgs(
                normalizedPath, ino, dev, nlink, size, atime, atimeNsec,
                mtime, mtimeNsec, ctime, ctimeNsec, blocks, blksize
            )
            runCommand("add_sus_kstat_statically $args", showSuccessSnackbar = true)
        }
    }

    fun editStaticKstatEntry(
        oldEntry: SuSFSStaticKstatEntry, path: String, ino: String, dev: String,
        nlink: String, size: String, atime: String, atimeNsec: String,
        mtime: String, mtimeNsec: String, ctime: String, ctimeNsec: String,
        blocks: String, blksize: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedPath = normalizePathEntry(path) ?: return@launch
            val oldArgs = buildStaticKstatCommandArgs(
                oldEntry.path, oldEntry.ino, oldEntry.dev, oldEntry.nlink, oldEntry.size,
                oldEntry.atime, oldEntry.atimeNsec, oldEntry.mtime, oldEntry.mtimeNsec,
                oldEntry.ctime, oldEntry.ctimeNsec, oldEntry.blocks, oldEntry.blksize,
            )

            if (!runCommand(
                    "del_sus_kstat_statically $oldArgs",
                    showSuccessSnackbar = false
                )
            ) return@launch

            val newArgs = buildStaticKstatCommandArgs(
                normalizedPath, ino, dev, nlink, size, atime, atimeNsec,
                mtime, mtimeNsec, ctime, ctimeNsec, blocks, blksize
            )
            if (runCommand("add_sus_kstat_statically $newArgs", showSuccessSnackbar = false)) {
                snackbarText = ksuApp.getString(R.string.kpm_control_success)
            }
        }
    }

    fun addSusPathEntries(rawInput: String) = addEntries(rawInput) { "add_sus_path $it" }
    fun addSusLoopPathEntries(rawInput: String) = addEntries(rawInput) { "add_sus_path_loop $it" }
    fun addKstatPathEntries(rawInput: String) = addEntries(rawInput) { "add_sus_kstat $it" }

    fun removeStaticKstat(entry: SuSFSStaticKstatEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleteArgs = buildStaticKstatCommandArgs(
                entry.path, entry.ino, entry.dev, entry.nlink, entry.size,
                entry.atime, entry.atimeNsec, entry.mtime, entry.mtimeNsec,
                entry.ctime, entry.ctimeNsec, entry.blocks, entry.blksize,
            )
            if (runCommand("del_sus_kstat_statically $deleteArgs", showSuccessSnackbar = true)) {
                postRebootToast()
            }
        }
    }

    private fun addPath(rawPath: String, commandBuilder: (String) -> String) {
        viewModelScope.launch(Dispatchers.IO) {
            val value = normalizePathEntry(rawPath) ?: return@launch
            runCommand(commandBuilder(shellQuote(value)), true)
        }
    }

    private fun addEntries(rawInput: String, commandBuilder: (String) -> String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = parsePathEntries(rawInput)
            var anySuccess = false
            for (entry in entries) {
                if (runCommand(commandBuilder(shellQuote(entry)), showSuccessSnackbar = false)) {
                    anySuccess = true
                }
            }
            if (anySuccess) snackbarText = ksuApp.getString(R.string.kpm_control_success)
        }
    }

    private fun removePath(rawPath: String, commandBuilder: (String) -> String) {
        viewModelScope.launch(Dispatchers.IO) {
            val value = normalizePathEntry(rawPath) ?: return@launch
            if (runCommand(commandBuilder(shellQuote(value)), true)) postRebootToast()
        }
    }

    private fun postRebootToast() {
        postToast(ksuApp.getString(R.string.reboot_to_apply))
    }

    private suspend fun runCommand(command: String, showSuccessSnackbar: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            val success = execKsud("susfs $command")
            if (!success) {
                snackbarText = ksuApp.getString(R.string.operation_failed)
            } else {
                if (showSuccessSnackbar) snackbarText =
                    ksuApp.getString(R.string.kpm_control_success)
                val newState = runCatching { loadState() }.getOrNull()
                if (newState != null) {
                    uiState = newState.copy(isLoading = false, isRefreshing = false)
                } else {
                    refresh()
                }
            }
            success
        }

    private suspend fun loadState(): SuSFSUiState {
        val statusEnabled = runCatching { getSuSFSStatus() }.getOrDefault(false)
        val version = runCatching { getSuSFSVersion().trim() }.getOrDefault("")
        val featureStatus = parseFeatureStatus(runCatching { getSuSFSFeatures() }.getOrDefault(""))
        val config = readSusfsConfig() ?: SusfsConfig()

        return SuSFSUiState(
            isLoading = false,
            isRefreshing = false,
            enabled = statusEnabled,
            versionText = version,
            unameValue = config.common.release.ifBlank { "default" },
            buildTimeValue = config.common.version.ifBlank { "default" },
            hideSuSMntsForNonSUProcs = config.common.hideSusMntsForNonSuProcs,
            hideMountsControlSupported = uiState.hideMountsControlSupported,
            susfsLogEnabled = config.common.enableSusfsLog,
            avcLogSpoofing = config.common.avcSpoofing,
            susPaths = config.susPath.susPath.sorted(),
            susLoopPaths = config.susPath.susPathLoop.sorted(),
            susMaps = config.susMap.sorted(),
            kstatPaths = config.kstat.susKstat.sorted(),
            kstatUpdatedPaths = config.kstat.updateKstat.sorted(),
            kstatFullClonePaths = config.kstat.fullClone.sorted(),
            staticKstatEntries = config.kstat.statically.sortedBy { it.path },
            featureStatus = featureStatus,
            loadError = null,
        )
    }

    private suspend fun readSusfsConfig(): SusfsConfig? = withContext(Dispatchers.IO) {
        val suFile = SuFile(CONFIG_PATH).apply { shell = getRootShell() }
        if (!suFile.isFile) return@withContext null

        val content = SuFileInputStream.open(suFile).bufferedReader().use { it.readText() }
        if (content.isBlank()) return@withContext null

        runCatching { gson.fromJson(content, SusfsConfig::class.java) }.getOrNull()
    }

    private val systemPropertiesClass by lazy { @SuppressLint("PrivateApi") Class.forName("android.os.SystemProperties") }

    private suspend fun getActiveBootSlot(): String = withContext(Dispatchers.IO) {
        val suffix = systemPropertiesClass
            .getDeclaredMethod("get", String::class.java, String::class.java)
            .invoke(null, "ro.boot.slot_suffix", "unknown") as String

        when (suffix) {
            "_a" -> "boot_a"
            "_b" -> "boot_b"
            else -> "boot"
        }
    }

    private suspend fun loadSlotInfo(): List<SuSFSSlotInfo> = withContext(Dispatchers.IO) {
        val raw = runCatching { getSuSFSSlotInfoJson() }.getOrDefault("[]")
        runCatching {
            val listType = object : TypeToken<List<SuSFSSlotInfo>>() {}.type
            val list: List<SuSFSSlotInfo> = gson.fromJson(raw, listType) ?: emptyList()
            list.filter { it.slotName.isNotBlank() && it.uname.isNotBlank() && it.buildTime.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun parseFeatureStatus(rawOutput: String): List<SuSFSFeatureStatus> {
        val enabledConfig = rawOutput.lines()
            .map { it.trim().substringBefore("=").substringBefore(":").trim() }
            .filter { it.startsWith("CONFIG_KSU_SUSFS_") }
            .toSet()

        val mappings = listOf(
            "CONFIG_KSU_SUSFS_SUS_PATH" to R.string.sus_path_feature_label,
            "CONFIG_KSU_SUSFS_SUS_MOUNT" to R.string.sus_mount_feature_label,
            "CONFIG_KSU_SUSFS_SPOOF_UNAME" to R.string.spoof_uname_feature_label,
            "CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG" to R.string.spoof_cmdline_feature_label,
            "CONFIG_KSU_SUSFS_OPEN_REDIRECT" to R.string.open_redirect_feature_label,
            "CONFIG_KSU_SUSFS_ENABLE_LOG" to R.string.enable_log_feature_label,
            "CONFIG_KSU_SUSFS_HIDE_KSU_SUSFS_SYMBOLS" to R.string.hide_symbols_feature_label,
            "CONFIG_KSU_SUSFS_SUS_KSTAT" to R.string.sus_kstat_feature_label,
            "CONFIG_KSU_SUSFS_SUS_MAP" to R.string.sus_map_feature_label,
        )

        return mappings.map { (key, titleRes) ->
            if (key == "CONFIG_KSU_SUSFS_ENABLE_LOG") {
                object : ConfigurableSuSFSFeature(
                    key,
                    ksuApp.getString(titleRes),
                    enabledConfig.contains(key)
                ) {
                    override fun onCheckedChange(checked: Boolean) = setSusfsLogEnabled(checked)
                }
            } else {
                NoNConfigurableSuSFSFeature(
                    key,
                    ksuApp.getString(titleRes),
                    enabledConfig.contains(key)
                )
            }
        }.sortedBy { it.title }
    }

    private fun parsePathEntries(rawInput: String): List<String> {
        val jsonLikeEntries = extractJsonLikePathEntries(rawInput)
        if (jsonLikeEntries.isNotEmpty()) return jsonLikeEntries

        return rawInput.lineSequence()
            .mapNotNull { normalizePathEntry(it) }
            .distinct()
            .toList()
    }

    private fun extractJsonLikePathEntries(rawInput: String): List<String> {
        val quotedPathRegex = Regex("['\"]([^'\"]+)['\"]")
        return quotedPathRegex.findAll(rawInput)
            .mapNotNull { normalizePathEntry(it.groupValues.getOrNull(1).orEmpty()) }
            .filter { it.startsWith("/") }
            .distinct()
            .toList()
    }

    private fun normalizePathEntry(raw: String): String? {
        var value = raw.trim()
        if (value.isEmpty()) return null

        value = value.removePrefix("[").removeSuffix("]").trim()
        while (value.endsWith(",")) value = value.dropLast(1).trimEnd()
        value = value.trim().trim('"', '\'').trim()
        while (value.endsWith(",")) value = value.dropLast(1).trimEnd()

        return value.takeIf { it.isNotEmpty() }
    }

    private fun toDefaultIfBlank(value: String): String = value.trim().ifBlank { "default" }

    private fun buildStaticKstatCommandArgs(vararg args: String): String {
        return args.joinToString(" ") { shellQuote(toDefaultIfBlank(it)) }
    }

    private fun shellQuote(text: String): String = "'${text.replace("'", "'\"'\"'")}'"
}