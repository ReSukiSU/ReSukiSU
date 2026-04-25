package com.resukisu.resukisu.ui.viewmodel

import android.content.Context
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
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private inline val susfsConfigPath: String
    get() = "/data/adb/ksu/.susfs.json"
private inline val defaultSusfsValue: String
    get() = "default"
private const val settingsPrefsName = "settings"
private const val hideSusMountsForAllProcsKey = "susfs_hide_mounts_for_all_procs"

data class SuSFSFeatureStatus(
    val key: String,
    val title: String,
    val enabled: Boolean,
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

data class SuSFSUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val commandRunning: Boolean = false,
    val enabled: Boolean = false,
    val versionText: String = "",
    val unameValue: String = defaultSusfsValue,
    val buildTimeValue: String = defaultSusfsValue,
    val hideSusMountsForAllProcs: Boolean = false,
    val hideMountsControlSupported: Boolean = true,
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

    init {
        refresh()
    }

    fun consumeToastMessage() {
        toastMessage = null
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
                    commandRunning = uiState.commandRunning,
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
        val uname = unameValue.trim().ifEmpty { defaultSusfsValue }
        val buildTime = buildTimeValue.trim().ifEmpty { defaultSusfsValue }
        if (uname == defaultSusfsValue && buildTime == defaultSusfsValue) {
            runCommand("del_uname")
            return
        }
        runCommand("set_uname ${shellQuote(uname)} ${shellQuote(buildTime)}")
    }

    fun setAvcLogSpoofing(enabled: Boolean) {
        runCommand("enable_avc_log_spoofing ${if (enabled) 1 else 0}")
    }

    fun setHideSusMountsForAllProcs(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (uiState.commandRunning) return@launch
            uiState = uiState.copy(commandRunning = true)

            val success = runCommandCandidates(
                listOf(
                    "hide_sus_mnts_for_non_su_procs ${if (enabled) 1 else 0}",
                    "hide_sus_mnts_for_all_procs ${if (enabled) 1 else 0}",
                )
            )

            if (success) {
                ksuApp.getSharedPreferences(settingsPrefsName, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(hideSusMountsForAllProcsKey, enabled)
                    .apply()

                toastMessage = ksuApp.getString(
                    if (enabled) R.string.susfs_hide_mounts_all_enabled
                    else R.string.susfs_hide_mounts_all_disabled
                )
                uiState = uiState.copy(
                    commandRunning = false,
                    hideSusMountsForAllProcs = enabled,
                    hideMountsControlSupported = true,
                )
                return@launch
            }

            toastMessage = ksuApp.getString(R.string.feature_status_unsupported_summary)
            uiState = uiState.copy(
                commandRunning = false,
                hideMountsControlSupported = false,
            )
        }
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
        val value = path.trim()
        if (value.isBlank()) return
        runCommand("add_sus_kstat_statically ${shellQuote(value)}", showSuccessToast = true)
    }

    fun removeStaticKstat(entry: SuSFSStaticKstatEntry) {
        runCommand(
            listOf(
                "del_sus_kstat_statically",
                shellQuote(entry.path),
                shellQuote(entry.ino),
                shellQuote(entry.dev),
                shellQuote(entry.nlink),
                shellQuote(entry.size),
                shellQuote(entry.atime),
                shellQuote(entry.atimeNsec),
                shellQuote(entry.mtime),
                shellQuote(entry.mtimeNsec),
                shellQuote(entry.ctime),
                shellQuote(entry.ctimeNsec),
                shellQuote(entry.blocks),
                shellQuote(entry.blksize),
            ).joinToString(" "),
            showSuccessToast = true,
        )
    }

    private fun addPath(
        rawPath: String,
        showSuccessToast: Boolean = false,
        commandBuilder: (String) -> String,
    ) {
        val value = rawPath.trim()
        if (value.isBlank()) return
        runCommand(commandBuilder(shellQuote(value)), showSuccessToast)
    }

    private fun removePath(
        rawPath: String,
        showSuccessToast: Boolean = false,
        commandBuilder: (String) -> String,
    ) {
        val value = rawPath.trim()
        if (value.isBlank()) return
        runCommand(commandBuilder(shellQuote(value)), showSuccessToast)
    }

    private fun runCommand(command: String, showSuccessToast: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (uiState.commandRunning) return@launch
            uiState = uiState.copy(commandRunning = true)

            val success = runCommandCandidates(listOf(command))
            if (!success) {
                toastMessage = ksuApp.getString(R.string.operation_failed)
            }

            uiState = uiState.copy(commandRunning = false)
            if (success) {
                if (showSuccessToast) {
                    toastMessage = ksuApp.getString(R.string.kpm_control_success)
                }
                val newState = runCatching { loadState() }.getOrNull()
                if (newState != null) {
                    uiState = newState.copy(
                        isLoading = false,
                        isRefreshing = false,
                        commandRunning = false,
                    )
                } else {
                    refresh()
                }
            }
        }
    }

    private fun runCommandCandidates(commands: List<String>): Boolean {
        for (command in commands) {
            if (execKsud("susfs $command", true)) {
                return true
            }
        }
        return false
    }

    private suspend fun loadState(): SuSFSUiState {
        val statusEnabled = runCatching { getSuSFSStatus() }.getOrDefault(false)
        val hideSusMountsForAllProcs = ksuApp
            .getSharedPreferences(settingsPrefsName, Context.MODE_PRIVATE)
            .getBoolean(hideSusMountsForAllProcsKey, false)

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
            commandRunning = false,
            enabled = statusEnabled,
            versionText = version,
            unameValue = commonObject?.optString("release", defaultSusfsValue) ?: defaultSusfsValue,
            buildTimeValue = commonObject?.optString("version", defaultSusfsValue) ?: defaultSusfsValue,
            hideSusMountsForAllProcs = hideSusMountsForAllProcs,
            hideMountsControlSupported = uiState.hideMountsControlSupported,
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
        val suFile = SuFile(susfsConfigPath).apply {
            setShell(getRootShell())
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

    private fun shellQuote(text: String): String {
        return "'${text.replace("'", "'\"'\"'")}'"
    }
}
