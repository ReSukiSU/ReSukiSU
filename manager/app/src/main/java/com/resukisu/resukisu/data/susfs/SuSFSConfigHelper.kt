package com.resukisu.resukisu.data.susfs

import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.util.withNewRootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

object SuSFSConfigHelper {
    private const val TAG = "SuSFSConfigHelper"
    const val CURRENT_VERSION: Int = 2

    private val gson = Gson()

    @Volatile
    private var cachedConfig: SuSFSConfig? = null

    @Volatile
    private var cachedStatusInfo: SuSFSStatusInfo? = null

    private val statusInfoMutex = Mutex()

    private data class CommandResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String,
    )

    suspend fun loadConfig(): SuSFSConfig = withContext(Dispatchers.IO) {
        cachedConfig?.let { return@withContext it }

        val result = executeSusfsCommand("config list_all")
        if (!result.success || result.stdout.isBlank()) {
            Log.e(TAG, "Failed to list SUSFS config: ${result.stderr}")
            return@withContext SuSFSConfig.createDefault().also { cachedConfig = it }
        }

        try {
            val config = gson.fromJson(result.stdout, SuSFSConfig::class.java)
            if (config.version != CURRENT_VERSION) {
                Log.e(
                    TAG,
                    "Incompatible SUSFS config version: ${config.version}, expected: $CURRENT_VERSION"
                )
                return@withContext SuSFSConfig.createDefault().also { cachedConfig = it }
            }

            config.also { cachedConfig = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SUSFS config", e)
            SuSFSConfig.createDefault().also { cachedConfig = it }
        }
    }

    suspend fun refreshConfig(): SuSFSConfig {
        cachedConfig = null
        return loadConfig()
    }

    suspend fun restoreDefaultConfig(): Boolean {
        return executeConfigMutation("restore")
    }

    suspend fun setConfigEnabled(enabled: Boolean): Boolean {
        return executeConfigMutation(if (enabled) "enable" else "disable")
    }

    suspend fun loadStatusInfo(forceRefresh: Boolean = false): SuSFSStatusInfo {
        if (!forceRefresh) {
            cachedStatusInfo?.let { return it }
        }

        return statusInfoMutex.withLock {
            if (!forceRefresh) {
                cachedStatusInfo?.let { return@withLock it }
            }

            val statusInfo = withContext(Dispatchers.IO) {
                val version = executeSusfsCommand("show version")
                val enabledFeatures = executeSusfsCommand("show enabled_features")
                val variant = executeSusfsCommand("show variant")

                SuSFSStatusInfo(
                    version = version.stdout.takeIf { version.success }.orEmpty(),
                    enabledFeatures = enabledFeatures.stdout.takeIf { enabledFeatures.success }
                        .orEmpty(),
                    variant = variant.stdout.takeIf { variant.success }.orEmpty(),
                )
            }

            cachedStatusInfo = statusInfo
            statusInfo
        }
    }

    suspend fun addSusPath(path: String): Boolean {
        return executeConfigMutation(
            command = "sus_path add ${shellQuote(path)}",
            currentKernelCommands = listOf("add_sus_path ${shellQuote(path)}"),
        )
    }

    suspend fun addSusPathLoop(path: String): Boolean {
        return executeConfigMutation(
            command = "sus_path add ${shellQuote(path)} --loop",
            currentKernelCommands = listOf("add_sus_path_loop ${shellQuote(path)}"),
        )
    }

    suspend fun removeSusPath(path: String): Boolean {
        return executeConfigMutation("sus_path remove ${shellQuote(path)}")
    }

    suspend fun addSusKstat(path: String): Boolean {
        val quotedPath = shellQuote(path)
        return executeConfigMutation(
            command = "sus_kstat add $quotedPath normal",
            currentKernelCommands = listOf(
                "add_sus_kstat $quotedPath",
                "update_sus_kstat $quotedPath",
            ),
        )
    }

    suspend fun addSusKstatFullClone(path: String): Boolean {
        val quotedPath = shellQuote(path)
        return executeConfigMutation(
            command = "sus_kstat add $quotedPath full_clone",
            currentKernelCommands = listOf(
                "add_sus_kstat $quotedPath",
                "update_sus_kstat_full_clone $quotedPath",
            ),
        )
    }

    suspend fun addSusKstatStatically(
        path: String,
        ino: Long? = null,
        dev: Long? = null,
        nlink: Long? = null,
        size: Long? = null,
        atime: Long? = null,
        atime_nsec: Long? = null,
        mtime: Long? = null,
        mtime_nsec: Long? = null,
        ctime: Long? = null,
        ctime_nsec: Long? = null,
        blocks: Long? = null,
        blksize: Long? = null,
    ): Boolean {
        val values = listOf(
            ino,
            dev,
            nlink,
            size,
            atime,
            atime_nsec,
            mtime,
            mtime_nsec,
            ctime,
            ctime_nsec,
            blocks,
            blksize,
        ).joinToString(" ") { it?.toString() ?: "default" }

        val quotedPath = shellQuote(path)
        return executeConfigMutation(
            command = "sus_kstat add $quotedPath statically $values",
            currentKernelCommands = listOf("add_sus_kstat_statically $quotedPath $values"),
        )
    }

    suspend fun removeSusKstat(path: String): Boolean {
        return executeConfigMutation("sus_kstat remove ${shellQuote(path)}")
    }

    suspend fun setUname(release: String, version: String): Boolean {
        val arguments = "${shellQuote(release)} ${shellQuote(version)}"
        return executeConfigMutation(
            command = "uname add $arguments",
            currentKernelCommands = listOf("set_uname $arguments"),
        )
    }

    suspend fun enableLog(enabled: Boolean): Boolean {
        return executeConfigMutation(
            command = "logging ${if (enabled) "add" else "remove"}",
            currentKernelCommands = listOf("enable_log ${if (enabled) 1 else 0}"),
        )
    }

    suspend fun hideSusMntsForNonSuProcs(enabled: Boolean): Boolean {
        return executeConfigMutation(
            command = "hide_sus_mnts_for_non_su_procs ${if (enabled) "add" else "remove"}",
            currentKernelCommands = listOf(
                "hide_sus_mnts_for_non_su_procs ${if (enabled) 1 else 0}"
            ),
        )
    }

    suspend fun enableAvcLogSpoofing(enabled: Boolean): Boolean {
        return executeConfigMutation(
            command = "avc_log_spoofing ${if (enabled) "add" else "remove"}",
            currentKernelCommands = listOf(
                "enable_avc_log_spoofing ${if (enabled) 1 else 0}"
            ),
        )
    }

    suspend fun setCmdlineOrBootconfig(path: String): Boolean {
        val command = if (path.isBlank()) {
            "cmdline_or_bootconfig remove"
        } else {
            "cmdline_or_bootconfig add ${shellQuote(path)}"
        }
        return if (path.isBlank()) {
            executeConfigMutation(command)
        } else {
            executeConfigMutation(
                command = command,
                currentKernelCommands = listOf(
                    "set_cmdline_or_bootconfig ${shellQuote(path)}"
                ),
            )
        }
    }

    suspend fun addOpenRedirect(
        targetPath: String,
        redirectedPath: String,
        uidScheme: UidScheme,
    ): Boolean {
        val arguments =
            "${shellQuote(targetPath)} ${shellQuote(redirectedPath)} ${uidScheme.value}"
        return executeConfigMutation(
            command = "open_redirect add $arguments",
            currentKernelCommands = listOf("add_open_redirect $arguments"),
        )
    }

    suspend fun removeOpenRedirect(targetPath: String): Boolean {
        return executeConfigMutation("open_redirect remove ${shellQuote(targetPath)}")
    }

    suspend fun addSusMap(path: String): Boolean {
        return executeConfigMutation(
            command = "sus_map add ${shellQuote(path)}",
            currentKernelCommands = listOf("add_sus_map ${shellQuote(path)}"),
        )
    }

    suspend fun removeSusMap(path: String): Boolean {
        return executeConfigMutation("sus_map remove ${shellQuote(path)}")
    }

    suspend fun showVersion(): String = loadStatusInfo().version

    suspend fun showEnabledFeatures(): String = loadStatusInfo().enabledFeatures

    suspend fun showVariant(): String = loadStatusInfo().variant

    suspend fun exportConfigToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = executeSusfsCommand("config backup")
            if (!result.success || result.stdout.isBlank()) {
                Log.e(TAG, "Failed to back up SUSFS config: ${result.stderr}")
                return@withContext false
            }

            ksuApp.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(result.stdout)
                writer.newLine()
            } ?: return@withContext false

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export SUSFS config", e)
            false
        }
    }

    suspend fun importConfigFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("susfs_restore", ".json", ksuApp.cacheDir)
        try {
            val fileName = DocumentFile.fromSingleUri(ksuApp, uri)?.name.orEmpty()
            if (!fileName.endsWith(".json", ignoreCase = true)) {
                Log.e(TAG, "Rejected SUSFS backup with invalid extension: $fileName")
                return@withContext false
            }

            ksuApp.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false

            executeConfigMutation("restore ${shellQuote(tempFile.absolutePath)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore SUSFS config", e)
            false
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun executeConfigMutation(
        command: String,
        currentKernelCommands: List<String> = emptyList(),
    ): Boolean {
        currentKernelCommands.forEach { currentKernelCommand ->
            val result = executeSusfsCommand(currentKernelCommand)
            if (!result.success) {
                Log.e(
                    TAG,
                    "SUSFS kernel command failed: $currentKernelCommand: ${result.stderr}"
                )
                return false
            }
        }

        val result = executeSusfsCommand("config $command")
        if (result.success) {
            cachedConfig = null
        } else {
            Log.e(TAG, "SUSFS config command failed: $command: ${result.stderr}")
        }
        return result.success
    }

    private suspend fun executeSusfsCommand(command: String): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val stdout = ArrayList<String>()
                val stderr = ArrayList<String>()
                val result = withNewRootShell {
                    newJob()
                        .add("${shellQuote(getKsuDaemonPath())} susfs $command")
                        .to(stdout, stderr)
                        .exec()
                }

                CommandResult(
                    success = result.isSuccess,
                    stdout = stdout.joinToString("\n").trim(),
                    stderr = stderr.joinToString("\n").trim(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute ksud susfs $command", e)
                CommandResult(false, "", e.message.orEmpty())
            }
        }

    private fun getKsuDaemonPath(): String {
        return ksuApp.applicationInfo.nativeLibraryDir + File.separator + "libksud.so"
    }

    private fun shellQuote(value: String): String {
        return "'${value.replace("'", "'\"'\"'")}'"
    }
}

enum class SusKstatType {
    @SerializedName("Normal")
    Normal,

    @SerializedName("FullClone")
    FullClone,

    @SerializedName("Statically")
    Statically,
}

enum class UidScheme(val value: Int) {
    @SerializedName("NonApp")
    NonApp(0),

    @SerializedName("RootExceptSu")
    RootExceptSu(1),

    @SerializedName("NonSu")
    NonSu(2),

    @SerializedName("UnmountedApp")
    UnmountedApp(3),

    @SerializedName("Unmounted")
    Unmounted(4),
}

data class UnameConfig(
    @SerializedName("version")
    val version: String,
    @SerializedName("release")
    val release: String,
)

data class SuSFSStatusInfo(
    val version: String,
    val enabledFeatures: String,
    val variant: String,
)

data class SusKstatStatically(
    @SerializedName("ino")
    val ino: Long?,
    @SerializedName("dev")
    val dev: Long?,
    @SerializedName("nlink")
    val nlink: Long?,
    @SerializedName("size")
    val size: Long?,
    @SerializedName("atime")
    val atime: Long?,
    @SerializedName("atime_nsec")
    val atime_nsec: Long?,
    @SerializedName("mtime")
    val mtime: Long?,
    @SerializedName("mtime_nsec")
    val mtime_nsec: Long?,
    @SerializedName("ctime")
    val ctime: Long?,
    @SerializedName("ctime_nsec")
    val ctime_nsec: Long?,
    @SerializedName("blocks")
    val blocks: Long?,
    @SerializedName("blksize")
    val blksize: Long?,
)

data class SusKstatItem(
    @SerializedName("path")
    val path: String,
    @SerializedName("spoof_type")
    val spoof_type: SusKstatType,
    @SerializedName("statically")
    val statically: SusKstatStatically?,
)

data class SusPathItem(
    @SerializedName("path")
    val path: String,
    @SerializedName("is_loop")
    val is_loop: Boolean,
)

data class OpenRedirectItem(
    @SerializedName("target_path")
    val target_path: String,
    @SerializedName("redirected_path")
    val redirected_path: String,
    @SerializedName("uid_scheme")
    val uid_scheme: UidScheme,
)

data class SuSFSConfig(
    @SerializedName("version")
    val version: Int,
    @SerializedName("enabled")
    val enabled: Boolean,
    @SerializedName("cmdline_or_bootconfig")
    val cmdline_or_bootconfig: String,
    @SerializedName("avc_log_spoofing")
    val avc_log_spoofing: Boolean,
    @SerializedName("logging")
    val logging: Boolean,
    @SerializedName("hide_sus_mnts_for_non_su_procs")
    val hide_sus_mnts_for_non_su_procs: Boolean,
    @SerializedName("uname")
    val uname: UnameConfig,
    @SerializedName("sus_path")
    val sus_path: Set<SusPathItem>,
    @SerializedName("sus_kstat")
    val sus_kstat: Set<SusKstatItem>,
    @SerializedName("open_redirect")
    val open_redirect: Set<OpenRedirectItem>,
    @SerializedName("sus_map")
    val sus_map: Set<String>,
) {
    companion object {
        fun createDefault(): SuSFSConfig {
            return SuSFSConfig(
                version = SuSFSConfigHelper.CURRENT_VERSION,
                enabled = true,
                cmdline_or_bootconfig = "",
                avc_log_spoofing = false,
                logging = false,
                hide_sus_mnts_for_non_su_procs = false,
                uname = UnameConfig(version = "default", release = "default"),
                sus_path = emptySet(),
                sus_kstat = emptySet(),
                open_redirect = emptySet(),
                sus_map = emptySet(),
            )
        }
    }
}
