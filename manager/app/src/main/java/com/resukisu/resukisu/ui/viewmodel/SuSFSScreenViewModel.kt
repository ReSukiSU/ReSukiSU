package com.resukisu.resukisu.ui.viewmodel

import android.annotation.SuppressLint
import android.content.ServiceConnection
import androidx.annotation.Keep
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
import com.resukisu.resukisu.ui.util.execKsud
import com.resukisu.resukisu.ui.util.getRootShell
import com.resukisu.resukisu.ui.util.getSuSFSFeatures
import com.resukisu.resukisu.ui.util.getSuSFSSlotInfoJson
import com.resukisu.resukisu.ui.util.getSuSFSVersion
import com.resukisu.resukisu.ui.util.listModules
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStream
import java.io.OutputStream

private const val CONFIG_PATH = "/data/adb/ksu/.susfs.json"

private val gson = GsonBuilder()
    .setPrettyPrinting()
    .create()

@Keep
data class SusfsConfig(
    val common: SusfsCommonConfig = SusfsCommonConfig(),
    @SerializedName("sus_path") val susPath: SusPathConfig = SusPathConfig(),
    @SerializedName("sus_map") val susMap: List<String> = emptyList(),
    val kstat: SusfsKstatConfig = SusfsKstatConfig()
)

@Keep
data class SusfsCommonConfig(
    val version: String = "default",
    val release: String = "default",
    @SerializedName("avc_spoofing") val avcSpoofing: Boolean = false,
    @SerializedName("enable_susfs_log") val enableSusfsLog: Boolean = false,
    @SerializedName("hide_sus_mnts_for_non_su_procs") val hideSusMntsForNonSuProcs: Boolean = false
)

@Keep
data class SusPathConfig(
    @SerializedName("sus_path_loop") val susPathLoop: List<String> = emptyList(),
    @SerializedName("sus_path") val susPath: List<String> = emptyList()
)

@Keep
data class SusfsKstatConfig(
    @SerializedName("sus_kstat") val susKstat: List<String> = emptyList(),
    @SerializedName("update_kstat") val updateKstat: List<String> = emptyList(),
    @SerializedName("full_clone") val fullClone: List<String> = emptyList(),
    val statically: List<SuSFSStaticKstatEntry> = emptyList()
)

@Keep
open class SuSFSFeatureStatus(
    val key: String,
    val title: String,
    val enabled: Boolean,
    val configurable: Boolean = false,
)

@Keep
data class NoNConfigurableSuSFSFeature(
    val featureKey: String,
    val featureTitle: String,
    val featureEnabled: Boolean,
) : SuSFSFeatureStatus(featureKey, featureTitle, featureEnabled, false)

@Keep
abstract class ConfigurableSuSFSFeature(
    featureKey: String,
    featureTitle: String,
    featureEnabled: Boolean,
) : SuSFSFeatureStatus(featureKey, featureTitle, featureEnabled, true) {
    abstract fun onCheckedChange(checked: Boolean)
}

@Keep
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

@Keep
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

    var hasEnabledThirdPartySusfsModule by mutableStateOf(false)
        private set

    /**
     * Resolved kernel-default uname from the currently active boot slot.
     * Empty when slot information is not yet available.
     */
    val kernelDefaultUname: String
        get() = slotInfoList.firstOrNull { it.slotName == currentActiveSlot }
            ?.uname.orEmpty().takeIf { it.isNotBlank() }.orEmpty()

    /**
     * Resolved kernel-default build time from the currently active boot slot.
     * Empty when slot information is not yet available.
     */
    val kernelDefaultBuildTime: String
        get() = slotInfoList.firstOrNull { it.slotName == currentActiveSlot }
            ?.buildTime.orEmpty().takeIf { it.isNotBlank() }.orEmpty()

    /**
     * Value that should be shown in the uname text field. Falls back to the
     * active slot's actual kernel uname so the field never displays the literal
     * "default" placeholder. May be empty if slot info has not been read yet.
     */
    val displayUnameValue: String
        get() {
            val raw = uiState.unameValue
            return if (raw.isBlank() || raw == "default") kernelDefaultUname else raw
        }

    /**
     * Value that should be shown in the build-time text field. Falls back to
     * the active slot's actual kernel build time so the field never displays
     * the literal "default" placeholder. May be empty if slot info has not
     * been read yet.
     */
    val displayBuildTimeValue: String
        get() {
            val raw = uiState.buildTimeValue
            return if (raw.isBlank() || raw == "default") kernelDefaultBuildTime else raw
        }

    private var serviceConnection: ServiceConnection? = null

    /**
     * Serialises every susfs-related batch operation. The susfs JSON config
     * is the source of truth for the UI: each `runCommand` / batch reload
     * reads the file and writes `uiState`, and two batches racing against
     * each other could observe partially-written intermediate states and
     * crash the manager during the recomposition that followed. Wrapping
     * each user-facing batch in this mutex guarantees they are observed in
     * a strictly serial order, even if the user double-taps a delete button.
     */
    private val batchMutex = Mutex()

    init {
        refresh()
        refreshSlotInfo()
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
                    // Detect third-party susfs modules
                    hasEnabledThirdPartySusfsModule = detectEnabledThirdPartySusfsModule()
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
            val trimmedUname = unameValue.trim()
            val trimmedBuildTime = buildTimeValue.trim()

            // If either field is empty, the user wants that single field reset to
            // the kernel default. Resolve those defaults from the active boot
            // slot via ksud so we apply the actual kernel value (not the literal
            // "default" placeholder).
            val needsActiveSlot = trimmedUname.isEmpty() || trimmedBuildTime.isEmpty()
            if (needsActiveSlot && !hasActiveSlotInfo()) {
                ensureSlotInfoLoaded()
            }

            val resolvedUname = when {
                trimmedUname.isNotEmpty() -> trimmedUname
                kernelDefaultUname.isNotBlank() -> kernelDefaultUname
                else -> "default"
            }
            val resolvedBuildTime = when {
                trimmedBuildTime.isNotEmpty() -> trimmedBuildTime
                kernelDefaultBuildTime.isNotBlank() -> kernelDefaultBuildTime
                else -> "default"
            }

            if (resolvedUname == uiState.unameValue && resolvedBuildTime == uiState.buildTimeValue) {
                return@launch
            }

            val success = if (resolvedUname == "default" && resolvedBuildTime == "default") {
                runCommand("del_uname", showSuccessSnackbar = false)
            } else {
                runCommand(
                    "set_uname ${shellQuote(resolvedUname)} ${shellQuote(resolvedBuildTime)}",
                    showSuccessSnackbar = false,
                )
            }
            if (success) {
                postToast(ksuApp.getString(R.string.susfs_uname_build_time_updated))
            }
        }
    }

    fun resetUnameAndBuildTime() {
        viewModelScope.launch(Dispatchers.IO) {
            // Read the active boot slot's actual kernel uname / build time so
            // that we can apply (not erase) those values. This guarantees the
            // text fields can later display the real kernel value instead of
            // the literal "default" placeholder.
            if (!hasActiveSlotInfo()) {
                ensureSlotInfoLoaded()
            }

            val uname = kernelDefaultUname
            val buildTime = kernelDefaultBuildTime

            val success = if (uname.isNotBlank() && buildTime.isNotBlank()) {
                runCommand(
                    "set_uname ${shellQuote(uname)} ${shellQuote(buildTime)}",
                    showSuccessSnackbar = false,
                )
            } else {
                // Fall back to erasing the override when slot info is
                // unavailable (e.g. encrypted boot image, unsupported device).
                runCommand("del_uname", showSuccessSnackbar = false)
            }

            if (success) {
                postToast(ksuApp.getString(R.string.susfs_uname_build_time_reset))
            } else {
                postToast(ksuApp.getString(R.string.operation_failed))
            }
        }
    }

    private fun hasActiveSlotInfo(): Boolean {
        if (currentActiveSlot.isBlank()) return false
        val slot = slotInfoList.firstOrNull { it.slotName == currentActiveSlot } ?: return false
        return slot.uname.isNotBlank() && slot.buildTime.isNotBlank()
    }

    private suspend fun ensureSlotInfoLoaded() {
        if (hasActiveSlotInfo()) return
        slotInfoLoading = true
        try {
            slotInfoList = runCatching { loadSlotInfo() }.getOrDefault(emptyList())
            currentActiveSlot = getActiveBootSlot()
        } finally {
            slotInfoLoading = false
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
                // Update UI state immediately
                uiState = uiState.copy(susfsLogEnabled = enabled)
                snackbarText = ksuApp.getString(
                    if (enabled) R.string.susfs_log_enabled else R.string.susfs_log_disabled
                )
                postToast(ksuApp.getString(R.string.reboot_to_apply))
            } else {
                // Revert UI state on failure
                uiState = uiState.copy(susfsLogEnabled = !enabled)
            }
        }
    }

    fun addAppPaths(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            // Run the entire add-batch as one logical operation so that the
            // recomposition triggered by the final `uiState` write happens
            // exactly once. Issuing the per-package state reloads inline
            // produced N intermediate recompositions and visibly flickered
            // the path list while the batch was in flight.
            batchMutex.withLock {
                val shell = runCatching { getRootShell() }.getOrNull()
                var successCount = 0
                var failureCount = 0
                packageNames.forEach { packageName ->
                    // Susfs add_sus_path performs a kern_path() resolution against
                    // the supplied path and rejects the request if the path does
                    // not currently exist. For app data directories this often
                    // happens before the user has launched the app, so create the
                    // directory tree as root first.
                    val candidates = listOf(
                        "/storage/emulated/0/Android/data/$packageName",
                        "/data/media/0/Android/data/$packageName",
                        "/sdcard/Android/data/$packageName",
                    )

                    if (shell != null) {
                        candidates.forEach { candidate ->
                            runCatching {
                                ShellUtils.fastCmdResult(
                                    shell,
                                    "mkdir -p ${shellQuote(candidate)} 2>/dev/null"
                                )
                            }
                        }
                    }

                    var packageSucceeded = false
                    for (path in candidates) {
                        val ok = runCatching {
                            execSusfsCommand(
                                "add_sus_path ${shellQuote(path)}",
                                showFailureSnackbar = false,
                            )
                        }.getOrDefault(false)
                        if (ok) {
                            packageSucceeded = true
                            break
                        }
                    }

                    if (packageSucceeded) successCount++ else failureCount++
                }

                if (successCount > 0) {
                    // Reload exactly once after the whole batch so Compose
                    // only sees a single state transition.
                    reloadSusfsState()
                }
                when {
                    successCount > 0 -> {
                        snackbarText = ksuApp.getString(R.string.kpm_control_success)
                    }
                    failureCount > 0 -> {
                        snackbarText = ksuApp.getString(R.string.operation_failed)
                    }
                }
            }
        }
    }

    /**
     * Remove every sus path under an app group as a single sequential batch so
     * that concurrent updates of the underlying config cannot race each other
     * (which previously caused the manager to crash when deleting multi-path
     * groups quickly).
     *
     * The state reload is performed exactly once after every `del_sus_path`
     * invocation completes. Previously each iteration reloaded the file and
     * wrote `uiState` separately, which produced one recomposition per path
     * and made the LazyColumn diff toggle through several intermediate sizes
     * before settling on the empty state. That cadence intermittently raced
     * the lazy-list machinery and crashed the manager when the user removed
     * an app whose group still contained multiple paths.
     */
    fun removeSusPaths(paths: Collection<String>) {
        if (paths.isEmpty()) return
        // Snapshot the caller-supplied collection up-front. The caller passes
        // a list captured from a Compose closure (the app-group row) — that
        // closure's identity changes on every recomposition triggered by
        // `uiState` updates, so the underlying reference could in theory be
        // garbage-collected (or in the case of `MutableList` backing some
        // intermediate buffer, mutated) before this coroutine reaches the
        // `for (raw in paths)` loop and racing the next recomposition.
        // Copying eagerly to an immutable `List` decouples the coroutine
        // from the UI lifecycle entirely.
        val pathsCopy = paths.toList()
        viewModelScope.launch(Dispatchers.IO) {
            batchMutex.withLock {
                var anySuccess = false
                for (raw in pathsCopy) {
                    val value = normalizePathEntry(raw) ?: continue
                    val ok = runCatching {
                        execSusfsCommand(
                            "del_sus_path ${shellQuote(value)}",
                            showFailureSnackbar = false,
                        )
                    }.getOrDefault(false)
                    if (ok) anySuccess = true
                }
                if (anySuccess) {
                    reloadSusfsState()
                    snackbarText = ksuApp.getString(R.string.kpm_control_success)
                    postRebootToast()
                } else {
                    snackbarText = ksuApp.getString(R.string.operation_failed)
                }
            }
        }
    }

    fun backupConfig(outputStream: OutputStream) =
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                outputStream.use { os ->
                    SuFileInputStream.open(SuFile(CONFIG_PATH)).use { it.copyTo(os) }
                }
            }.onFailure {
                postToast(ksuApp.getString(R.string.operation_failed))
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
            runCatching {
                runCommand(commandBuilder(shellQuote(value)), true)
            }.onFailure {
                snackbarText = ksuApp.getString(R.string.operation_failed)
            }
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
            runCatching {
                if (runCommand(commandBuilder(shellQuote(value)), true)) postRebootToast()
            }.onFailure {
                snackbarText = ksuApp.getString(R.string.operation_failed)
            }
        }
    }

    private fun postRebootToast() {
        postToast(ksuApp.getString(R.string.reboot_to_apply))
    }

    /**
     * Execute a single susfs CLI command via ksud without touching
     * `uiState`. Used by batch operations that want to amortise the state
     * reload over many commands.
     */
    private suspend fun execSusfsCommand(
        command: String,
        showFailureSnackbar: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        val success = runCatching { execKsud("susfs $command") }.getOrDefault(false)
        if (!success && showFailureSnackbar) {
            snackbarText = ksuApp.getString(R.string.operation_failed)
        }
        success
    }

    /**
     * Read the current susfs config from disk and publish it to the UI as
     * a single `uiState` write. Batch operations call this once, after all
     * the underlying CLI commands have completed, so Compose only observes
     * one state transition for the entire user action.
     */
    private suspend fun reloadSusfsState() = withContext(Dispatchers.IO) {
        val newState = runCatching { loadState() }.getOrNull()
        if (newState != null) {
            uiState = newState.copy(isLoading = false, isRefreshing = false)
        } else {
            refresh()
        }
    }

    private suspend fun runCommand(
        command: String,
        showSuccessSnackbar: Boolean = false,
        showFailureSnackbar: Boolean = true,
    ): Boolean {
        val success = execSusfsCommand(command, showFailureSnackbar)
        if (success) {
            if (showSuccessSnackbar) {
                snackbarText = ksuApp.getString(R.string.kpm_control_success)
            }
            reloadSusfsState()
        }
        return success
    }

    private suspend fun loadState(): SuSFSUiState {
        val config = readSusfsConfig() ?: SusfsConfig()
        val featureStatus = parseFeatureStatus(
            runCatching { getSuSFSFeatures() }.getOrDefault(""),
            config,
        )
        val version = runCatching { getSuSFSVersion().trim() }.getOrDefault("")

        // Determine if susfs is enabled based on whether any feature is enabled
        // This fixes the issue where missing a single CONFIG option would disable all susfs functionality
        val statusEnabled = featureStatus.any { it.enabled }

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
            // Gson bypasses Kotlin's default-argument initialisers when it
            // constructs data classes via reflection, so a `null` (or an
            // entirely missing) JSON array deserialises into a plain `null`
            // even though the property is typed as a non-null `List`. Calling
            // `.sorted()` on such a value previously produced an NPE during
            // the recomposition that fired right after the very last sus_path
            // was deleted, which surfaced as a manager crash. Coerce every
            // collection to a non-null list before sorting.
            //
            // Each list is also de-duplicated and stripped of blank entries
            // before being handed to Compose. The sus path list is the source
            // of the Compose keys used by the LazyColumn / SegmentedColumn
            // pair on the "Sus paths" tab — duplicates (which can sneak in
            // from a hand-edited or partially-restored config) would otherwise
            // produce two LazyColumn items with the same key and crash the
            // manager with `IllegalArgumentException: Key ... was already used`
            // the next time the user removed or added an entry. Blank entries
            // would similarly produce items with an unstable identity.
            susPaths = config.susPath.susPath.orEmpty()
                .filter { it.isNotBlank() }
                .distinct()
                .sorted(),
            susLoopPaths = config.susPath.susPathLoop.orEmpty()
                .filter { it.isNotBlank() }
                .distinct()
                .sorted(),
            susMaps = config.susMap.orEmpty()
                .filter { it.isNotBlank() }
                .distinct()
                .sorted(),
            kstatPaths = config.kstat.susKstat.orEmpty()
                .filter { it.isNotBlank() }
                .distinct()
                .sorted(),
            kstatUpdatedPaths = config.kstat.updateKstat.orEmpty()
                .filter { it.isNotBlank() }
                .distinct()
                .sorted(),
            kstatFullClonePaths = config.kstat.fullClone.orEmpty()
                .filter { it.isNotBlank() }
                .distinct()
                .sorted(),
            staticKstatEntries = config.kstat.statically.orEmpty()
                .distinctBy { "${it.path}|${it.ino}|${it.dev}|${it.size}" }
                .sortedBy { it.path },
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

    private fun parseFeatureStatus(
        rawOutput: String,
        config: SusfsConfig,
    ): List<SuSFSFeatureStatus> {
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
            val kernelHasFeature = enabledConfig.contains(key)
            if (key == "CONFIG_KSU_SUSFS_ENABLE_LOG" && kernelHasFeature) {
                // The switch in the "Enabled feature status" tab represents
                // the runtime configuration, not just the kernel build flag.
                // Reading config.common.enableSusfsLog here lets the switch
                // visually flip every time the user toggles it.
                object : ConfigurableSuSFSFeature(
                    key,
                    ksuApp.getString(titleRes),
                    config.common.enableSusfsLog,
                ) {
                    override fun onCheckedChange(checked: Boolean) = setSusfsLogEnabled(checked)
                }
            } else {
                NoNConfigurableSuSFSFeature(
                    key,
                    ksuApp.getString(titleRes),
                    kernelHasFeature,
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

    private suspend fun detectEnabledThirdPartySusfsModule(): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val modulesJson = listModules()
                val array = JSONArray(modulesJson)
                for (i in 0 until array.length()) {
                    val module = array.optJSONObject(i) ?: continue
                    val enabled = module.optBoolean("enabled", false)
                    if (!enabled) continue

                    val id = module.optString("id", "")
                    if (id.contains("susfs", ignoreCase = true)) {
                        return@withContext true
                    }
                }
                false
            }.getOrDefault(false)
        }
    }
}
