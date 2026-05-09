package com.resukisu.resukisu.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.util.execKsud
import com.resukisu.resukisu.ui.util.getRootShell
import com.resukisu.resukisu.ui.util.getSuSFSFeatures
import com.resukisu.resukisu.ui.util.getSuSFSStatus
import com.resukisu.resukisu.ui.util.getSuSFSVersion
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private inline val defaultSusfsValue: String
    get() = "default"

private const val SUSFS_CONFIG_PATH = "/data/adb/ksu/.susfs.json"
private const val CGROUP_BASE_PATH = "/sys/fs/cgroup"
private const val MEDIA_DATA_PATH = "/data/media/0/Android/data"

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

    var toastMessage by mutableStateOf<String?>(null)
        private set

    var slotInfoList by mutableStateOf<List<SuSFSSlotInfo>>(emptyList())
        private set

    var currentActiveSlot by mutableStateOf("")
        private set

    var slotInfoLoading by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun consumeToastMessage() {
        toastMessage = null
    }

    fun postToast(message: String) {
        toastMessage = message
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
            if (uname == defaultSusfsValue && buildTime == defaultSusfsValue) {
                val success = runCommand("del_uname")
                if (success) {
                    postRebootToast()
                }
                return@launch
            }
            runCommand("set_uname ${shellQuote(uname)} ${shellQuote(buildTime)}")
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
                toastMessage = ksuApp.getString(
                    if (enabled) R.string.susfs_hide_mounts_all_enabled
                    else R.string.susfs_hide_mounts_all_disabled
                )
                uiState = uiState.copy(
                    hideSuSMntsForNonSUProcs = enabled,
                    hideMountsControlSupported = true,
                )
                return@launch
            }

            toastMessage = ksuApp.getString(R.string.feature_status_unsupported_summary)
            uiState = uiState.copy(
                hideMountsControlSupported = false,
            )
        }
    }

    fun setSusfsLogEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = runCommand("enable_log ${if (enabled) 1 else 0}")
            if (success) {
                toastMessage = ksuApp.getString(
                    if (enabled) R.string.susfs_log_enabled else R.string.susfs_log_disabled
                )
                uiState = uiState.copy(susfsLogEnabled = enabled)
                postToast(ksuApp.getString(R.string.reboot_to_apply))
            }
        }
    }

    fun resetAllSusPaths() {
        viewModelScope.launch(Dispatchers.IO) {
            var anySuccess = false
            uiState.susPaths.forEach { path ->
                if (runCommand("del_sus_path ${shellQuote(path)}", showSuccessToast = false)) {
                    anySuccess = true
                }
            }
            if (anySuccess) {
                postRebootToast()
                refresh()
            }
        }
    }

    fun addAppPaths(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var anySuccess = false
            packageNames.forEach { packageName ->
                val uid = resolveUid(packageName) ?: return@forEach
                val candidates = linkedSetOf<String>()
                candidates += "/sdcard/Android/data/$packageName"
                candidates += "$MEDIA_DATA_PATH/$packageName"
                candidates += buildUidPath(uid)

                candidates.forEach { path ->
                    val success = runCommand("add_sus_path ${shellQuote(path)}", showSuccessToast = false)
                    if (success) anySuccess = true
                }
            }
            if (anySuccess) {
                toastMessage = ksuApp.getString(R.string.kpm_control_success)
                refresh()
            }
        }
    }

    fun backupCurrentConfig(outputPath: String, onFinish: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val rawConfig = readSusfsConfigRaw()
            if (rawConfig.isNullOrBlank()) {
                onFinish(false, ksuApp.getString(R.string.susfs_backup_file_not_found))
                return@launch
            }

            val writeResult = runCatching {
                java.io.File(outputPath).parentFile?.mkdirs()
                java.io.File(outputPath).writeText(rawConfig)
            }
            if (writeResult.isSuccess) {
                onFinish(true, null)
            } else {
                onFinish(false, writeResult.exceptionOrNull()?.message)
            }
        }
    }

    fun restoreFromBackupFile(filePath: String, onFinish: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = runCatching { java.io.File(filePath).readText() }.getOrNull()
            if (content.isNullOrBlank()) {
                onFinish(false, ksuApp.getString(R.string.susfs_backup_invalid_format))
                return@launch
            }

            val validJson = runCatching { JSONObject(content) }.isSuccess
            if (!validJson) {
                onFinish(false, ksuApp.getString(R.string.susfs_backup_invalid_format))
                return@launch
            }

            val writeSuccess = writeRawConfig(content)
            if (!writeSuccess) {
                onFinish(false, ksuApp.getString(R.string.operation_failed))
                return@launch
            }

            refresh()
            onFinish(true, null)
        }
    }

    fun validateBackup(filePath: String, onFinish: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val content = java.io.File(filePath).readText()
                JSONObject(content)
                true
            }.getOrDefault(false)
            onFinish(ok, if (ok) null else ksuApp.getString(R.string.susfs_backup_invalid_format))
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
        val entries = SuperUserViewModel.apps
            .asSequence()
            .filter { appInfo ->
                val info = appInfo.packageInfo.applicationInfo ?: return@filter false
                (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }
            .map { appInfo ->
                SuSFSAppEntry(
                    packageName = appInfo.packageName,
                    label = appInfo.label.ifBlank { appInfo.packageName },
                    packageInfo = appInfo.packageInfo,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
        return@withContext entries
    }

    fun addSusPath(path: String) = addPath(path, showSuccessToast = true) { "add_sus_path $it" }
    fun removeSusPath(path: String) = removePath(path, showSuccessToast = true) { "del_sus_path $it" }

    fun addSusLoopPath(path: String) = addPath(path, showSuccessToast = true) { "add_sus_path_loop $it" }
    fun removeSusLoopPath(path: String) = removePath(path, showSuccessToast = true) { "del_sus_path_loop $it" }

    fun addSusMap(path: String) = addPath(path, showSuccessToast = true) { "add_sus_map $it" }
    fun removeSusMap(path: String) = removePath(path, showSuccessToast = true) { "del_sus_map $it" }

    fun addKstatPath(path: String) = addPath(path, showSuccessToast = true) { "add_sus_kstat $it" }
    fun removeKstatPath(path: String) = removePath(path, showSuccessToast = true) { "del_sus_kstat $it" }

    fun addKstatUpdatePath(path: String) = addPath(path, showSuccessToast = true) { "update_sus_kstat $it" }
    fun removeKstatUpdatePath(path: String) = removePath(path, showSuccessToast = true) { "del_update_sus_kstat $it" }

    fun addKstatFullClonePath(path: String) = addPath(path, showSuccessToast = true) { "update_sus_kstat_full_clone $it" }
    fun removeKstatFullClonePath(path: String) = removePath(path, showSuccessToast = true) { "del_sus_kstat_full_clone $it" }

    fun addStaticKstatPath(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val value = path.trim()
            if (value.isBlank()) return@launch
            runCommand("add_sus_kstat_statically ${shellQuote(value)}", showSuccessToast = true)
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
            runCommand("add_sus_kstat_statically $args", showSuccessToast = true)
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
            val deletedOld = runCommand("del_sus_kstat_statically $oldDeleteCommand", showSuccessToast = false)
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
            val addedNew = runCommand("add_sus_kstat_statically $newAddCommand", showSuccessToast = false)
            if (addedNew) {
                toastMessage = ksuApp.getString(R.string.kpm_control_success)
            }
        }
    }

    fun addSusPathEntries(rawInput: String) = addEntries(rawInput, showSuccessToast = true) { "add_sus_path $it" }
    fun addSusLoopPathEntries(rawInput: String) = addEntries(rawInput, showSuccessToast = true) { "add_sus_path_loop $it" }
    fun addKstatPathEntries(rawInput: String) = addEntries(rawInput, showSuccessToast = true) { "add_sus_kstat $it" }

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
            val success = runCommand("del_sus_kstat_statically $deleteArgs", showSuccessToast = true)
            if (success) {
                postRebootToast()
            }
        }
    }

    private fun addPath(
        rawPath: String,
        showSuccessToast: Boolean = false,
        commandBuilder: (String) -> String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val value = normalizePathEntry(rawPath) ?: return@launch
            runCommand(commandBuilder(shellQuote(value)), showSuccessToast)
        }
    }

    private fun addEntries(
        rawInput: String,
        showSuccessToast: Boolean = false,
        commandBuilder: (String) -> String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = parsePathEntries(rawInput)
            if (entries.isEmpty()) return@launch

            var anySuccess = false
            for (entry in entries) {
                val success = runCommand(commandBuilder(shellQuote(entry)), showSuccessToast = false)
                if (success) anySuccess = true
            }
            if (anySuccess && showSuccessToast) {
                toastMessage = ksuApp.getString(R.string.kpm_control_success)
            }
        }
    }

    private fun removePath(
        rawPath: String,
        showSuccessToast: Boolean = false,
        commandBuilder: (String) -> String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val value = normalizePathEntry(rawPath) ?: return@launch
            val success = runCommand(commandBuilder(shellQuote(value)), showSuccessToast)
            if (success) {
                postRebootToast()
            }
        }
    }

    private fun postRebootToast() {
        postToast(ksuApp.getString(R.string.reboot_to_apply))
    }

    private suspend fun runCommand(command: String, showSuccessToast: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            val success = execKsud("susfs $command")
            if (!success) {
                toastMessage = ksuApp.getString(R.string.operation_failed)
            }

            if (success) {
                if (showSuccessToast) {
                    toastMessage = ksuApp.getString(R.string.kpm_control_success)
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
            hideSuSMntsForNonSUProcs = false, // TODO native
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
        val suFile = SuFile(SUSFS_CONFIG_PATH).apply {
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

    private suspend fun readSusfsConfigRaw(): String? = withContext(Dispatchers.IO) {
        val suFile = SuFile(SUSFS_CONFIG_PATH).apply {
            shell = getRootShell()
        }
        if (!suFile.isFile) {
            return@withContext null
        }
        return@withContext runCatching {
            SuFileInputStream.open(suFile).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private suspend fun writeRawConfig(content: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = "cat <<'EOF' > $SUSFS_CONFIG_PATH\n$content\nEOF"
        return@withContext getRootShell().newJob().add(cmd).exec().isSuccess
    }

    private suspend fun getActiveBootSlot(): String = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        val suffix = runShellCmd(shell, "getprop ro.boot.slot_suffix").trim()
        return@withContext when (suffix) {
            "_a" -> "boot_a"
            "_b" -> "boot_b"
            else -> "unknown"
        }
    }

    private suspend fun loadSlotInfo(): List<SuSFSSlotInfo> = withContext(Dispatchers.IO) {
        val shell = getRootShell()
        val result = mutableListOf<SuSFSSlotInfo>()
        listOf("boot_a", "boot_b").forEach { slot ->
            val unameCmd = "strings -n 20 /dev/block/by-name/$slot | awk '/Linux version/ && ++c==2 {print \$3; exit}'"
            val buildTimeCmd = "strings -n 20 /dev/block/by-name/$slot | sed -n '/Linux version.*#/{s/.*#/#/p;q}'"
            val uname = runShellCmd(shell, unameCmd).trim()
            val buildTime = runShellCmd(shell, buildTimeCmd).trim()
            if (uname.isNotEmpty() && buildTime.isNotEmpty()) {
                result += SuSFSSlotInfo(
                    slotName = slot,
                    uname = uname,
                    buildTime = buildTime
                )
            }
        }
        return@withContext result
    }

    private fun runShellCmd(shell: Shell, cmd: String): String {
        val out = mutableListOf<String>()
        shell.newJob().add(cmd).to(out, null).exec()
        return out.joinToString("\n")
    }

    private suspend fun resolveUid(packageName: String): Int? = withContext(Dispatchers.IO) {
        SuperUserViewModel.apps.firstOrNull { it.packageName == packageName }?.packageInfo?.applicationInfo?.uid?.let {
            return@withContext it
        }
        return@withContext runCatching {
            val pkg = ksuApp.packageManager.getPackageInfo(packageName, 0)
            pkg.applicationInfo?.uid
        }.getOrNull()
    }

    private suspend fun buildUidPath(uid: Int): String = withContext(Dispatchers.IO) {
        val candidates = listOf(
            "$CGROUP_BASE_PATH/uid_$uid",
            "$CGROUP_BASE_PATH/apps/uid_$uid",
            "$CGROUP_BASE_PATH/system/uid_$uid",
            "$CGROUP_BASE_PATH/freezer/uid_$uid",
            "$CGROUP_BASE_PATH/memory/uid_$uid",
            "$CGROUP_BASE_PATH/cpuset/uid_$uid",
            "$CGROUP_BASE_PATH/cpu/uid_$uid",
        )
        val shell = getRootShell()
        for (path in candidates) {
            val ok = shell.newJob().add("[ -d ${shellQuote(path)} ]").exec().isSuccess
            if (ok) return@withContext path
        }
        return@withContext candidates.first()
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
