package com.resukisu.resukisu.ui.viewmodel

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume

private inline val defaultSusfsValue: String
    get() = "default"

private const val CONFIG_PATH = "/data/adb/ksu/.susfs.json"
private const val SUSFS_VM_TAG = "SuSFSScreenViewModel"
private const val DEFAULT_SUSFS_CONFIG_TEMPLATE = """{
  "common": {
    "version": "default",
    "release": "default",
    "avc_spoofing": false,
    "enable_susfs_log": false,
    "hide_sus_mnts_for_non_su_procs": false
  },
  "sus_path": {
    "sus_path_loop": [],
    "sus_path": []
  },
  "sus_map": [],
  "kstat": {
    "sus_kstat": [],
    "update_kstat": [],
    "full_clone": [],
    "statically": []
  }
}
"""

data class SuSFSFeatureStatus(
    val key: String,
    val title: String,
    val enabled: Boolean,
    val configurable: Boolean = false,
)

data class SuSFSStaticKstatEntry(
    val path: String,
    val ino: String,
    val dev: String,
    val nlink: String,
    val size: String,
    val atime: String,
    val atimeNsec: String,
    val mtime: String,
    val mtimeNsec: String,
    val ctime: String,
    val ctimeNsec: String,
    val blocks: String,
    val blksize: String,
) {
    val summary: String
        get() = "ino=$ino, dev=$dev, size=$size"
}

data class SuSFSAppEntry(
    val packageName: String,
    val label: String,
    val packageInfo: PackageInfo? = null,
)

data class SuSFSSlotInfo(
    val slotName: String,
    val uname: String,
    val buildTime: String,
)

data class SuSFSUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val enabled: Boolean = false,
    val versionText: String = "",
    val unameValue: String = defaultSusfsValue,
    val buildTimeValue: String = defaultSusfsValue,
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

            val loaded = runCatching { loadState() }
            loaded.onSuccess { newState ->
                uiState = newState.copy(
                    isLoading = false,
                    isRefreshing = false,
                )
            }.onFailure {
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
            val uname = unameValue.trim().ifEmpty { defaultSusfsValue }
            val buildTime = buildTimeValue.trim().ifEmpty { defaultSusfsValue }
            if (uname == uiState.unameValue && buildTime == uiState.buildTimeValue) {
                return@launch
            }
            if (uname == defaultSusfsValue && buildTime == defaultSusfsValue) {
                val success = runCommand("del_uname", showSuccessSnackbar = false)
                if (success) {
                    postToast(ksuApp.getString(R.string.susfs_uname_build_time_reset))
                }
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
            uiState = uiState.copy(
                hideMountsControlSupported = false,
            )
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
                val candidates = linkedSetOf<String>()
                // FIXME Use Environment.getExternalStorageDirectory() and use reflection get current userId to replace hardcode user 0
                // And, I don't know there really need or not, users can just enable `persist.sys.vold_app_data_isolation_enabled` property,right?
                candidates += "/sdcard/Android/data/$packageName"
                candidates += "/data/media/0/Android/data/$packageName"

                candidates.forEach { path ->
                    val success =
                        runCommand("add_sus_path ${shellQuote(path)}", showSuccessSnackbar = false)
                    if (success) anySuccess = true
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
            val bytes: ByteArray = runCatching {
                inputStream.use { it.readBytes() }
            }.getOrElse {
                ByteArray(0)
            }
            if (bytes.isEmpty()) {
                onFinish(false)
                return@launch
            }

            val ok = runCatching {
                JSONObject(String(bytes, Charsets.UTF_8))
                true
            }.getOrDefault(false)

            if (!ok) {
                onFinish(false)
                return@launch
            }

            val writeOk = runCatching {
                SuFileOutputStream.open(SuFile(CONFIG_PATH)).use { os ->
                    bytes.inputStream().use { it.copyTo(os) }
                }
                true
            }.getOrDefault(false)
            if (!writeOk) {
                onFinish(false)
                return@launch
            }

            refresh()
            onFinish(true)
        }
    }

    fun resetAllSusfsConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val reset = runCatching {
                SuFileOutputStream.open(SuFile(CONFIG_PATH)).use { os ->
                    os.write(DEFAULT_SUSFS_CONFIG_TEMPLATE.toByteArray(Charsets.UTF_8))
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
            val loaded = runCatching { loadSlotInfo() }.getOrElse { emptyList() }
            slotInfoList = loaded
            currentActiveSlot = getActiveBootSlot()
            slotInfoLoading = false
        }
    }

    suspend fun loadSelectableApps(): List<SuSFSAppEntry> = withContext(Dispatchers.IO) {
        val packageManager = ksuApp.packageManager
        val packageInfos = fetchInstalledPackagesViaRootService()
            .ifEmpty { getInstalledPackagesFallback() }

        val entries = packageInfos
            .asSequence()
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
                    } else {
                        packageInfo
                    }
                }.getOrNull() ?: packageInfo

                val label = runCatching {
                    packageInfoWithAppInfo.applicationInfo
                        ?.loadLabel(packageManager)
                        ?.toString()
                }.getOrNull()

                SuSFSAppEntry(
                    packageName = packageInfo.packageName,
                    label = label?.ifBlank { packageInfo.packageName } ?: packageInfo.packageName,
                    packageInfo = packageInfoWithAppInfo,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
        return@withContext entries
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
            Log.e(SUSFS_VM_TAG, "fetchInstalledPackagesViaRootService failed", t)
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
                if (continuation.isActive) {
                    continuation.resume(binder)
                }
            }
        }
        serviceConnection = connection

        val intent = Intent(ksuApp, KsuService::class.java)
        try {
            val task = com.topjohnwu.superuser.ipc.RootService.bindOrTask(
                intent,
                Shell.EXECUTOR,
                connection
            )
            task?.let { Shell.getShell().execTask(it) }
        } catch (t: Throwable) {
            Log.e(SUSFS_VM_TAG, "connectKsuService failed", t)
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }

    private fun stopKsuService() {
        if (serviceConnection == null) return
        try {
            val intent = Intent(ksuApp, KsuService::class.java)
            com.topjohnwu.superuser.ipc.RootService.stop(intent)
        } catch (t: Throwable) {
            Log.e(SUSFS_VM_TAG, "stopKsuService failed", t)
        } finally {
            serviceConnection = null
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
            if (value.isBlank()) return@launch
            runCommand("add_sus_kstat_statically ${shellQuote(value)}", showSuccessSnackbar = true)
        }
    }

    fun addStaticKstatEntry(
        path: String,
        ino: String,
        dev: String,
        nlink: String,
        size: String,
        atime: String,
        atimeNsec: String,
        mtime: String,
        mtimeNsec: String,
        ctime: String,
        ctimeNsec: String,
        blocks: String,
        blksize: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedPath = normalizePathEntry(path) ?: return@launch
            val args = listOf(
                shellQuote(normalizedPath),
                shellQuote(toDefaultIfBlank(ino)),
                shellQuote(toDefaultIfBlank(dev)),
                shellQuote(toDefaultIfBlank(nlink)),
                shellQuote(toDefaultIfBlank(size)),
                shellQuote(toDefaultIfBlank(atime)),
                shellQuote(toDefaultIfBlank(atimeNsec)),
                shellQuote(toDefaultIfBlank(mtime)),
                shellQuote(toDefaultIfBlank(mtimeNsec)),
                shellQuote(toDefaultIfBlank(ctime)),
                shellQuote(toDefaultIfBlank(ctimeNsec)),
                shellQuote(toDefaultIfBlank(blocks)),
                shellQuote(toDefaultIfBlank(blksize)),
            ).joinToString(" ")
            runCommand("add_sus_kstat_statically $args", showSuccessSnackbar = true)
        }
    }

    fun editStaticKstatEntry(
        oldEntry: SuSFSStaticKstatEntry,
        path: String,
        ino: String,
        dev: String,
        nlink: String,
        size: String,
        atime: String,
        atimeNsec: String,
        mtime: String,
        mtimeNsec: String,
        ctime: String,
        ctimeNsec: String,
        blocks: String,
        blksize: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedPath = normalizePathEntry(path) ?: return@launch
            val oldDeleteCommand = buildStaticKstatCommandArgs(
                oldEntry.path,
                oldEntry.ino,
                oldEntry.dev,
                oldEntry.nlink,
                oldEntry.size,
                oldEntry.atime,
                oldEntry.atimeNsec,
                oldEntry.mtime,
                oldEntry.mtimeNsec,
                oldEntry.ctime,
                oldEntry.ctimeNsec,
                oldEntry.blocks,
                oldEntry.blksize,
            )
            val deletedOld = runCommand(
                "del_sus_kstat_statically $oldDeleteCommand",
                showSuccessSnackbar = false
            )
            if (!deletedOld) return@launch

            val newAddCommand = buildStaticKstatCommandArgs(
                normalizedPath,
                ino,
                dev,
                nlink,
                size,
                atime,
                atimeNsec,
                mtime,
                mtimeNsec,
                ctime,
                ctimeNsec,
                blocks,
                blksize,
            )
            val addedNew =
                runCommand("add_sus_kstat_statically $newAddCommand", showSuccessSnackbar = false)
            if (addedNew) {
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
                entry.path,
                entry.ino,
                entry.dev,
                entry.nlink,
                entry.size,
                entry.atime,
                entry.atimeNsec,
                entry.mtime,
                entry.mtimeNsec,
                entry.ctime,
                entry.ctimeNsec,
                entry.blocks,
                entry.blksize,
            )
            val success =
                runCommand("del_sus_kstat_statically $deleteArgs", showSuccessSnackbar = true)
            if (success) {
                postRebootToast()
            }
        }
    }

    private fun addPath(
        rawPath: String,
        commandBuilder: (String) -> String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val value = normalizePathEntry(rawPath) ?: return@launch
            runCommand(commandBuilder(shellQuote(value)), true)
        }
    }

    private fun addEntries(
        rawInput: String,
        commandBuilder: (String) -> String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = parsePathEntries(rawInput)
            if (entries.isEmpty()) return@launch

            var anySuccess = false
            for (entry in entries) {
                val success =
                    runCommand(commandBuilder(shellQuote(entry)), showSuccessSnackbar = false)
                if (success) anySuccess = true
            }
            if (anySuccess) {
                snackbarText = ksuApp.getString(R.string.kpm_control_success)
            }
        }
    }

    private fun removePath(
        rawPath: String,
        commandBuilder: (String) -> String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val value = normalizePathEntry(rawPath) ?: return@launch
            val success = runCommand(commandBuilder(shellQuote(value)), true)
            if (success) {
                postRebootToast()
            }
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
            }

            if (success) {
                if (showSuccessSnackbar) {
                    snackbarText = ksuApp.getString(R.string.kpm_control_success)
                }
                val newState = runCatching { loadState() }.getOrNull()
                if (newState != null) {
                    uiState = newState.copy(
                        isLoading = false,
                        isRefreshing = false,
                    )
                } else {
                    refresh()
                }
            }
            return@withContext success
    }

    private suspend fun loadState(): SuSFSUiState {
        val statusEnabled = runCatching { getSuSFSStatus() }.getOrDefault(false)

        val version = runCatching {
            getSuSFSVersion().trim()
        }.getOrDefault("")

        val featuresOutput = runCatching {
            getSuSFSFeatures()
        }.getOrDefault("")
        val featureStatus = parseFeatureStatus(featuresOutput)

        val configJson = readSusfsConfigJson()
        val commonObject = configJson?.optJSONObject("common")
        val susPathObject = configJson?.optJSONObject("sus_path")
        val kstatObject = configJson?.optJSONObject("kstat")

        return SuSFSUiState(
            isLoading = false,
            isRefreshing = false,
            enabled = statusEnabled,
            versionText = version,
            unameValue = commonObject?.optString("release", defaultSusfsValue) ?: defaultSusfsValue,
            buildTimeValue = commonObject?.optString("version", defaultSusfsValue) ?: defaultSusfsValue,
            hideSuSMntsForNonSUProcs = commonObject?.optBoolean(
                "hide_sus_mnts_for_non_su_procs",
                false
            ) ?: false,
            hideMountsControlSupported = uiState.hideMountsControlSupported,
            susfsLogEnabled = commonObject?.optBoolean("enable_susfs_log", false) ?: false,
            avcLogSpoofing = commonObject?.optBoolean("avc_spoofing", false) ?: false,
            susPaths = jsonArrayToSortedList(susPathObject?.optJSONArray("sus_path")),
            susLoopPaths = jsonArrayToSortedList(susPathObject?.optJSONArray("sus_path_loop")),
            susMaps = jsonArrayToSortedList(configJson?.optJSONArray("sus_map")),
            kstatPaths = jsonArrayToSortedList(kstatObject?.optJSONArray("sus_kstat")),
            kstatUpdatedPaths = jsonArrayToSortedList(kstatObject?.optJSONArray("update_kstat")),
            kstatFullClonePaths = jsonArrayToSortedList(kstatObject?.optJSONArray("full_clone")),
            staticKstatEntries = parseStaticKstatEntries(kstatObject?.optJSONArray("statically")),
            featureStatus = featureStatus,
            loadError = null,
        )
    }

    private suspend fun readSusfsConfigJson(): JSONObject? = withContext(Dispatchers.IO) {
        val suFile = SuFile(CONFIG_PATH).apply {
            shell = getRootShell()
        }
        if (!suFile.isFile) {
            return@withContext null
        }

        val content = SuFileInputStream.open(suFile).bufferedReader().use { it.readText() }
        if (content.isBlank()) {
            return@withContext null
        }
        return@withContext runCatching { JSONObject(content) }.getOrNull()
    }

    private val systemPropertiesClass by lazy { @SuppressLint("PrivateApi") Class.forName("android.os.SystemProperties") }

    private suspend fun getActiveBootSlot(): String = withContext(Dispatchers.IO) {
        val suffix = systemPropertiesClass
            .getDeclaredMethod(
                "get",
                String::class.java, String::class.java
            )
            .invoke(null, "ro.boot.slot_suffix", "unknown") // unknown = A only

        return@withContext when (suffix) {
            "_a" -> "boot_a"
            "_b" -> "boot_b"
            else -> "boot"
        }
    }

    private suspend fun loadSlotInfo(): List<SuSFSSlotInfo> = withContext(Dispatchers.IO) {
        val raw = runCatching { getSuSFSSlotInfoJson() }.getOrDefault("[]")
        val json = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val result = mutableListOf<SuSFSSlotInfo>()
        for (i in 0 until json.length()) {
            val item = json.optJSONObject(i) ?: continue
            val slotName = item.optString("slot_name", "").trim()
            val uname = item.optString("uname", "").trim()
            val buildTime = item.optString("build_time", "").trim()
            if (slotName.isNotEmpty() && uname.isNotEmpty() && buildTime.isNotEmpty()) {
                result += SuSFSSlotInfo(
                    slotName = slotName,
                    uname = uname,
                    buildTime = buildTime,
                )
            }
        }
        result
    }

    private fun parseFeatureStatus(rawOutput: String): List<SuSFSFeatureStatus> {
        val enabledConfig = rawOutput.lines()
            .map { line ->
                line.trim()
                    .substringBefore("=")
                    .substringBefore(":")
                    .trim()
            }
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
            SuSFSFeatureStatus(
                key = key,
                title = ksuApp.getString(titleRes),
                enabled = enabledConfig.contains(key),
                configurable = key == "CONFIG_KSU_SUSFS_ENABLE_LOG",
            )
        }.sortedBy { it.title }
    }

    private fun parseStaticKstatEntries(array: JSONArray?): List<SuSFSStaticKstatEntry> {
        if (array == null) return emptyList()
        val result = mutableListOf<SuSFSStaticKstatEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            result += SuSFSStaticKstatEntry(
                path = item.optString("path", ""),
                ino = item.optString("ino", defaultSusfsValue),
                dev = item.optString("dev", defaultSusfsValue),
                nlink = item.optString("nlink", defaultSusfsValue),
                size = item.optString("size", defaultSusfsValue),
                atime = item.optString("atime", defaultSusfsValue),
                atimeNsec = item.optString("atime_nsec", defaultSusfsValue),
                mtime = item.optString("mtime", defaultSusfsValue),
                mtimeNsec = item.optString("mtime_nsec", defaultSusfsValue),
                ctime = item.optString("ctime", defaultSusfsValue),
                ctimeNsec = item.optString("ctime_nsec", defaultSusfsValue),
                blocks = item.optString("blocks", defaultSusfsValue),
                blksize = item.optString("blksize", defaultSusfsValue),
            )
        }
        return result.sortedBy { it.path }
    }

    private fun jsonArrayToSortedList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val values = buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
        return values.sorted()
    }

    private fun parsePathEntries(rawInput: String): List<String> {
        extractJsonLikePathEntries(rawInput).takeIf { it.isNotEmpty() }?.let { return it }

        val seen = linkedSetOf<String>()
        rawInput.lineSequence()
            .mapNotNull { normalizePathEntry(it) }
            .forEach { seen.add(it) }
        return seen.toList()
    }

    private fun extractJsonLikePathEntries(rawInput: String): List<String> {
        val quotedPathRegex = Regex("['\"]([^'\"]+)['\"]")
        val seen = linkedSetOf<String>()
        quotedPathRegex.findAll(rawInput).forEach { match ->
            val candidate = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val normalized = normalizePathEntry(candidate)
            if (normalized != null && normalized.startsWith("/")) {
                seen += normalized
            }
        }
        return seen.toList()
    }

    private fun normalizePathEntry(raw: String): String? {
        var value = raw.trim()
        if (value.isEmpty()) return null

        value = value.removePrefix("[").removeSuffix("]").trim()
        while (value.endsWith(",")) {
            value = value.dropLast(1).trimEnd()
        }
        value = value.trim().trim('"', '\'').trim()
        if (value.isEmpty()) return null

        while (value.endsWith(",")) {
            value = value.dropLast(1).trimEnd()
        }

        return value.takeIf { it.isNotEmpty() }
    }

    private fun toDefaultIfBlank(value: String): String {
        return value.trim().ifBlank { defaultSusfsValue }
    }

    private fun buildStaticKstatCommandArgs(
        path: String,
        ino: String,
        dev: String,
        nlink: String,
        size: String,
        atime: String,
        atimeNsec: String,
        mtime: String,
        mtimeNsec: String,
        ctime: String,
        ctimeNsec: String,
        blocks: String,
        blksize: String,
    ): String {
        return listOf(
            shellQuote(path),
            shellQuote(toDefaultIfBlank(ino)),
            shellQuote(toDefaultIfBlank(dev)),
            shellQuote(toDefaultIfBlank(nlink)),
            shellQuote(toDefaultIfBlank(size)),
            shellQuote(toDefaultIfBlank(atime)),
            shellQuote(toDefaultIfBlank(atimeNsec)),
            shellQuote(toDefaultIfBlank(mtime)),
            shellQuote(toDefaultIfBlank(mtimeNsec)),
            shellQuote(toDefaultIfBlank(ctime)),
            shellQuote(toDefaultIfBlank(ctimeNsec)),
            shellQuote(toDefaultIfBlank(blocks)),
            shellQuote(toDefaultIfBlank(blksize)),
        ).joinToString(" ")
    }

    private fun shellQuote(text: String): String {
        return "'${text.replace("'", "'\"'\"'")}'"
    }
}
