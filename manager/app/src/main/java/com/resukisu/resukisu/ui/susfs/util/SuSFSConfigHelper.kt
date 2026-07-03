package com.resukisu.resukisu.ui.susfs.util

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.resukisu.resukisu.ui.util.execKsud
import com.resukisu.resukisu.ui.util.getRootShell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader

/**
 * SuSFS Config Helper
 * 用于管理 SuSFS 配置的数据模型和基础结构
 *
 * 参考 Rust 实现:
 * - userspace/ksud/src/android/susfs/config/model.rs
 * - userspace/ksud/src/android/susfs/enums.rs
 * - userspace/ksud/src/defs.rs
 */
object SuSFSConfigHelper {
    private const val TAG = "SuSFSConfigHelper"

    // 配置文件路径，对应 Rust defs.rs 中的 SUSFS_CONFIG
    // WORKING_DIR = "/data/adb/ksu/"
    // SUSFS_CONFIG = concatcp!(WORKING_DIR, ".susfs.json")
    const val SUSFS_CONFIG_PATH = "/data/adb/ksu/.susfs.json"

    // 当前配置版本，对应 Rust model.rs 中的 CURRENT_VERSION
    const val CURRENT_VERSION: Int = 1

    // Gson 实例，用于 JSON 序列化/反序列化
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    // 缓存的配置
    @Volatile
    private var cachedConfig: SuSFSConfig? = null

    // ==================== 配置读取和反序列化 ====================

    /**
     * 加载配置文件
     * 对应 Rust file_ops.rs 中的 read() 方法
     *
     * @return 配置对象，失败时返回默认配置
     */
    suspend fun loadConfig(): SuSFSConfig = withContext(Dispatchers.IO) {
        try {
            // 尝试从缓存获取
            cachedConfig?.let { return@withContext it }

            // 读取配置文件
            val configFile = SuFile.open(SUSFS_CONFIG_PATH)
            if (!configFile.exists()) {
                Log.w(TAG, "Config file not found, returning default config")
                return@withContext SuSFSConfig.createDefault().also { cachedConfig = it }
            }

            // 读取 JSON 内容
            val jsonContent = BufferedReader(configFile.newInputStream().reader()).use { reader ->
                reader.readText()
            }

            // 解析 JSON
            val config = gson.fromJson(jsonContent, SuSFSConfig::class.java)

            // 验证版本
            if (config.version != CURRENT_VERSION) {
                Log.w(TAG, "Incompatible SUSFS config version: ${config.version}, expected: $CURRENT_VERSION")
                return@withContext SuSFSConfig.createDefault().also { cachedConfig = it }
            }

            // 更新缓存
            cachedConfig = config
            Log.i(TAG, "Config loaded successfully")
            config
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config, returning default", e)
            SuSFSConfig.createDefault().also { cachedConfig = it }
        }
    }

    /**
     * 刷新配置缓存
     * 强制重新从文件读取配置
     */
    suspend fun refreshConfig(): SuSFSConfig {
        cachedConfig = null
        return loadConfig()
    }

    // ==================== Get 接口 (对应 get_ops.rs) ====================

    /**
     * 获取配置版本
     * 对应 Rust get_version()
     */
    suspend fun getVersion(): Int {
        return loadConfig().version
    }

    /**
     * 获取 cmdline 或 bootconfig
     * 对应 Rust get_cmdline_or_bootconfig()
     */
    suspend fun getCmdlineOrBootconfig(): String {
        return loadConfig().cmdline_or_bootconfig
    }

    /**
     * 检查 AVC 日志欺骗是否启用
     * 对应 Rust is_avc_log_spoofing()
     */
    suspend fun isAvcLogSpoofingEnabled(): Boolean {
        return loadConfig().avc_log_spoofing
    }

    /**
     * 检查日志是否启用
     * 对应 Rust is_logging()
     */
    suspend fun isLoggingEnabled(): Boolean {
        return loadConfig().logging
    }

    /**
     * 检查是否为非 SU 进程隐藏 SUS 挂载
     * 对应 Rust is_hiding_sus_mnts_for_non_su_procs()
     */
    suspend fun isHidingSusMountsForNonSuProcs(): Boolean {
        return loadConfig().hide_sus_mnts_for_non_su_procs
    }

    /**
     * 获取 uname version
     * 对应 Rust get_uname_version()
     */
    suspend fun getUnameVersion(): String {
        return loadConfig().uname.version
    }

    /**
     * 获取 uname release
     * 对应 Rust get_uname_release()
     */
    suspend fun getUnameRelease(): String {
        return loadConfig().uname.release
    }

    /**
     * 获取 SUS Paths
     * 对应 Rust get_sus_paths()
     */
    suspend fun getSusPaths(): Set<SusPathItem> {
        return loadConfig().sus_path
    }

    /**
     * 获取 SUS Kstats
     * 对应 Rust get_sus_kstats()
     */
    suspend fun getSusKstats(): Set<SusKstatItem> {
        return loadConfig().sus_kstat
    }

    /**
     * 获取 Open Redirects
     * 对应 Rust get_open_redirects()
     */
    suspend fun getOpenRedirects(): Set<OpenRedirectItem> {
        return loadConfig().open_redirect
    }

    /**
     * 获取 SUS Maps
     * 对应 Rust get_sus_maps()
     */
    suspend fun getSusMaps(): Set<String> {
        return loadConfig().sus_map
    }

    // ==================== Set 接口 (对应 cli.rs) ====================

    /**
     * 执行 ksud susfs 命令
     * 所有 CLI 操作的基础方法
     */
    private suspend fun executeSusfsCommand(subcommand: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fullCommand = "susfs $subcommand"
            val result = execKsud(fullCommand, newShell = true)
            if (result) {
                // 成功后刷新缓存
                refreshConfig()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command: susfs $subcommand", e)
            false
        }
    }

    // ==================== SUS Path 相关命令 ====================

    /**
     * 添加 SUS Path
     * 命令: ksud susfs add_sus_path <path>
     */
    suspend fun addSusPath(path: String): Boolean {
        return executeSusfsCommand("add_sus_path '$path'")
    }

    /**
     * 添加 SUS Path Loop
     * 命令: ksud susfs add_sus_path_loop <path>
     */
    suspend fun addSusPathLoop(path: String): Boolean {
        return executeSusfsCommand("add_sus_path_loop '$path'")
    }

    /**
     * 删除 SUS Path
     * 命令: ksud susfs del_sus_path <path>
     */
    suspend fun delSusPath(path: String): Boolean {
        return executeSusfsCommand("del_sus_path '$path'")
    }

    // ==================== SUS Kstat 相关命令 ====================

    /**
     * 添加 SUS Kstat
     * 命令: ksud susfs add_sus_kstat <path>
     */
    suspend fun addSusKstat(path: String): Boolean {
        return executeSusfsCommand("add_sus_kstat '$path'")
    }

    /**
     * 更新 SUS Kstat
     * 命令: ksud susfs update_sus_kstat <path>
     */
    suspend fun updateSusKstat(path: String): Boolean {
        return executeSusfsCommand("update_sus_kstat '$path'")
    }

    /**
     * 更新 SUS Kstat Full Clone
     * 命令: ksud susfs update_sus_kstat_full_clone <path>
     */
    suspend fun updateSusKstatFullClone(path: String): Boolean {
        return executeSusfsCommand("update_sus_kstat_full_clone '$path'")
    }

    /**
     * 添加 SUS Kstat Statically
     * 命令: ksud susfs add_sus_kstat_statically <path> [ino] [dev] ...
     * 可选参数为 null 时使用 "default"
     */
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
        blksize: Long? = null
    ): Boolean {
        val args = buildList {
            add("'$path'")
            add(ino?.toString() ?: "default")
            add(dev?.toString() ?: "default")
            add(nlink?.toString() ?: "default")
            add(size?.toString() ?: "default")
            add(atime?.toString() ?: "default")
            add(atime_nsec?.toString() ?: "default")
            add(mtime?.toString() ?: "default")
            add(mtime_nsec?.toString() ?: "default")
            add(ctime?.toString() ?: "default")
            add(ctime_nsec?.toString() ?: "default")
            add(blocks?.toString() ?: "default")
            add(blksize?.toString() ?: "default")
        }.joinToString(" ")

        return executeSusfsCommand("add_sus_kstat_statically $args")
    }

    /**
     * 删除 SUS Kstat
     * 命令: ksud susfs del_sus_kstat <path>
     */
    suspend fun delSusKstat(path: String): Boolean {
        return executeSusfsCommand("del_sus_kstat '$path'")
    }

    // ==================== Uname 相关命令 ====================

    /**
     * 设置 Uname
     * 命令: ksud susfs set_uname <version> <release>
     */
    suspend fun setUname(version: String, release: String): Boolean {
        return executeSusfsCommand("set_uname '$version' '$release'")
    }

    // ==================== 日志和挂载隐藏相关命令 ====================

    /**
     * 启用/禁用日志
     * 命令: ksud susfs enable_log <0|1>
     * Boolean 参数映射为 0/1
     */
    suspend fun enableLog(enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        return executeSusfsCommand("enable_log $value")
    }

    /**
     * 为非 SU 进程隐藏 SUS 挂载
     * 命令: ksud susfs hide_sus_mnts_for_non_su_procs <0|1>
     * Boolean 参数映射为 0/1
     */
    suspend fun hideSusMntsForNonSuProcs(enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        return executeSusfsCommand("hide_sus_mnts_for_non_su_procs $value")
    }

    /**
     * 启用/禁用 AVC 日志欺骗
     * 命令: ksud susfs enable_avc_log_spoofing <0|1>
     * Boolean 参数映射为 0/1
     */
    suspend fun enableAvcLogSpoofing(enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        return executeSusfsCommand("enable_avc_log_spoofing $value")
    }

    // ==================== Cmdline/Bootconfig 相关命令 ====================

    /**
     * 设置 Cmdline 或 Bootconfig
     * 命令: ksud susfs set_cmdline_or_bootconfig <path>
     */
    suspend fun setCmdlineOrBootconfig(path: String): Boolean {
        return executeSusfsCommand("set_cmdline_or_bootconfig '$path'")
    }

    // ==================== Open Redirect 相关命令 ====================

    /**
     * 添加 Open Redirect
     * 命令: ksud susfs add_open_redirect <target_path> <redirected_path> <uid_scheme>
     * 注意: 配置文件中 uid_scheme 使用枚举变体名称，但 CLI 中使用数值
     */
    suspend fun addOpenRedirect(targetPath: String, redirectedPath: String, uidScheme: UidScheme): Boolean {
        return executeSusfsCommand("add_open_redirect '$targetPath' '$redirectedPath' ${uidScheme.value}")
    }

    /**
     * 删除 Open Redirect
     * 命令: ksud susfs del_open_redirect <target_path>
     */
    suspend fun delOpenRedirect(targetPath: String): Boolean {
        return executeSusfsCommand("del_open_redirect '$targetPath'")
    }

    // ==================== SUS Map 相关命令 ====================

    /**
     * 添加 SUS Map
     * 命令: ksud susfs add_sus_map <path>
     */
    suspend fun addSusMap(path: String): Boolean {
        return executeSusfsCommand("add_sus_map '$path'")
    }

    /**
     * 删除 SUS Map
     * 命令: ksud susfs del_sus_map <path>
     */
    suspend fun delSusMap(path: String): Boolean {
        return executeSusfsCommand("del_sus_map '$path'")
    }

    // ==================== Show 相关命令 ====================

    /**
     * 显示版本
     * 命令: ksud susfs show version
     */
    suspend fun showVersion(): String = withContext(Dispatchers.IO) {
        try {
            val shell = getRootShell()
            val cmd = "susfs show version"
            val result = shell.newJob()
                .add("${getKsuDaemonPath()} $cmd")
                .to(ArrayList<String>(), null)
                .exec()
            result.out.firstOrNull()?.trim() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show version", e)
            ""
        }
    }

    /**
     * 显示启用的特性
     * 命令: ksud susfs show enabled_features
     */
    suspend fun showEnabledFeatures(): String = withContext(Dispatchers.IO) {
        try {
            val shell = getRootShell()
            val cmd = "susfs show enabled_features"
            val result = shell.newJob()
                .add("${getKsuDaemonPath()} $cmd")
                .to(ArrayList<String>(), null)
                .exec()
            result.out.joinToString("\n").trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show enabled features", e)
            ""
        }
    }

    /**
     * 显示变体信息
     * 命令: ksud susfs show variant
     */
    suspend fun showVariant(): String = withContext(Dispatchers.IO) {
        try {
            val shell = getRootShell()
            val cmd = "susfs show variant"
            val result = shell.newJob()
                .add("${getKsuDaemonPath()} $cmd")
                .to(ArrayList<String>(), null)
                .exec()
            result.out.firstOrNull()?.trim() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show variant", e)
            ""
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取 ksud 守护进程路径
     * 参考 KsuCli.kt 中的实现
     */
    private fun getKsuDaemonPath(): String {
        return com.resukisu.resukisu.ksuApp.applicationInfo.nativeLibraryDir + java.io.File.separator + "libksud.so"
    }
}

// ==================== 枚举类 ====================

/**
 * SUS Kstat 类型枚举
 * 对应 Rust enums.rs 中的 SusKstatType
 *
 * Rust serde 默认序列化为变体名称字符串（"Normal", "FullClone", "Statically"）
 *
 * Rust 定义:
 * #[derive(Serialize, Deserialize)]
 * pub enum SusKstatType {
 *     Normal,
 *     FullClone,
 *     Statically,
 * }
 */
enum class SusKstatType {
    @SerializedName("Normal")
    Normal,

    @SerializedName("FullClone")
    FullClone,

    @SerializedName("Statically")
    Statically
}

/**
 * UID Scheme 枚举
 * 对应 Rust enums.rs 中的 UidScheme
 *
 * Rust serde 序列化为变体名称字符串（"NonApp", "RootExceptSu", "NonSu", "UnmountedApp", "Unmounted"）
 * CLI 中使用数值（0-4），通过 value 属性获取
 *
 * Rust 定义:
 * #[derive(Debug, Eq, PartialEq, TryFromPrimitive, Copy, Clone, Serialize, Deserialize)]
 * #[repr(i32)]
 * pub enum UidScheme {
 *     NonApp = 0,
 *     RootExceptSu = 1,
 *     NonSu = 2,
 *     UnmountedApp = 3,
 *     Unmounted = 4,
 * }
 */
enum class UidScheme(val value: Int) {
    @SerializedName("NonApp")
    NonApp(0),              // 有效于非应用进程 (uid < 10000)

    @SerializedName("RootExceptSu")
    RootExceptSu(1),        // 有效于非SU进程且uid为0（所有root进程但不包含su域）

    @SerializedName("NonSu")
    NonSu(2),               // 有效于非SU进程

    @SerializedName("UnmountedApp")
    UnmountedApp(3),        // 有效于标记为umounted且uid >= 10000的进程

    @SerializedName("Unmounted")
    Unmounted(4);           // 有效于标记为umounted的进程（包括大多数init启动的进程）

    companion object {
        fun fromValue(value: Int): UidScheme {
            return values().find { it.value == value } ?: NonApp
        }
    }
}

// ==================== 数据类 ====================

/**
 * Uname 配置
 * 对应 Rust model.rs 中的 Uname 结构
 * 
 * Rust 定义:
 * #[derive(Serialize, Deserialize)]
 * pub struct Uname {
 *     pub version: String,
 *     pub release: String,
 * }
 */
data class UnameConfig(
    @SerializedName("version")
    val version: String,
    
    @SerializedName("release")
    val release: String
)

/**
 * SUS Kstat 静态配置
 * 对应 Rust model.rs 中的 SusKstatStatically 结构
 * 所有字段为 Long? (Option<i64>)
 * 
 * Rust 定义:
 * #[derive(Serialize, Deserialize)]
 * pub struct SusKstatStatically {
 *     pub ino: Option<i64>,
 *     pub dev: Option<i64>,
 *     pub nlink: Option<i64>,
 *     pub size: Option<i64>,
 *     pub atime: Option<i64>,
 *     pub atime_nsec: Option<i64>,
 *     pub mtime: Option<i64>,
 *     pub mtime_nsec: Option<i64>,
 *     pub ctime: Option<i64>,
 *     pub ctime_nsec: Option<i64>,
 *     pub blocks: Option<i64>,
 *     pub blksize: Option<i64>,
 * }
 */
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
    val blksize: Long?
)

/**
 * SUS Kstat 项
 * 对应 Rust model.rs 中的 SusKstatItem 结构
 * 
 * Rust 定义:
 * #[derive(Serialize, Deserialize)]
 * pub struct SusKstatItem {
 *     pub path: String,
 *     pub spoof_type: SusKstatType,
 *     pub statically: Option<SusKstatStatically>,
 * }
 */
data class SusKstatItem(
    @SerializedName("path")
    val path: String,
    
    @SerializedName("spoof_type")
    val spoof_type: SusKstatType,
    
    @SerializedName("statically")
    val statically: SusKstatStatically?
)

/**
 * SUS Path 项
 * 对应 Rust model.rs 中的 SusPathItem 结构
 * 
 * Rust 定义:
 * #[derive(Serialize, Deserialize)]
 * pub struct SusPathItem {
 *     pub path: String,
 *     pub is_loop: bool,
 * }
 */
data class SusPathItem(
    @SerializedName("path")
    val path: String,
    
    @SerializedName("is_loop")
    val is_loop: Boolean
)

/**
 * Open Redirect 项
 * 对应 Rust model.rs 中的 OpenRedirectItem 结构
 *
 * Rust 定义:
 * #[derive(Serialize, Deserialize)]
 * pub struct OpenRedirectItem {
 *     pub target_path: String,
 *     pub redirected_path: String,
 *     pub uid_scheme: UidScheme,
 * }
 */
data class OpenRedirectItem(
    @SerializedName("target_path")
    val target_path: String,

    @SerializedName("redirected_path")
    val redirected_path: String,

    @SerializedName("uid_scheme")
    val uid_scheme: UidScheme
)

/**
 * SuSFS 配置主结构
 * 对应 Rust model.rs 中的 Config 结构
 * 
 * Rust 定义:
 * #[derive(Serialize, Deserialize)]
 * pub struct Config {
 *     pub(super) version: u8,
 *     pub(super) cmdline_or_bootconfig: String,
 *     pub(super) avc_log_spoofing: bool,
 *     pub(super) logging: bool,
 *     pub(super) hide_sus_mnts_for_non_su_procs: bool,
 *     pub(super) uname: Uname,
 *     pub(super) sus_path: HashSet<SusPathItem>,
 *     pub(super) sus_kstat: HashSet<SusKstatItem>,
 *     pub(super) open_redirect: HashSet<OpenRedirectItem>,
 *     pub(super) sus_map: HashSet<String>,
 * }
 */
data class SuSFSConfig(
    @SerializedName("version")
    val version: Int,
    
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
    val sus_map: Set<String>
) {
    /**
     * 创建默认配置
     * 对应 Rust Config 的 Default trait 实现
     */
    companion object {
        fun createDefault(): SuSFSConfig {
            return SuSFSConfig(
                version = SuSFSConfigHelper.CURRENT_VERSION,
                cmdline_or_bootconfig = "",
                avc_log_spoofing = false,
                logging = false,
                hide_sus_mnts_for_non_su_procs = false,
                uname = UnameConfig(
                    version = "default",
                    release = "default"
                ),
                sus_path = emptySet(),
                sus_kstat = emptySet(),
                open_redirect = emptySet(),
                sus_map = emptySet()
            )
        }
    }
}