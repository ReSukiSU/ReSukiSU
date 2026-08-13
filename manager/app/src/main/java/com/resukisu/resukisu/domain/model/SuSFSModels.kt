package com.resukisu.resukisu.domain.model

enum class SusKstatType {
    Normal,
    FullClone,
    Statically,
}

enum class UidScheme(val value: Int) {
    NonApp(0),
    RootExceptSu(1),
    NonSu(2),
    UnmountedApp(3),
    Unmounted(4),
}

data class UnameConfig(
    val version: String,
    val release: String,
)

data class SuSFSSlotInfo(
    val slotName: String,
    val uname: String,
    val buildTime: String,
)

data class SuSFSStatusInfo(
    val version: String,
    val enabledFeatures: String,
    val variant: String,
)

data class SusKstatStatically(
    val ino: Long?,
    val dev: Long?,
    val nlink: Long?,
    val size: Long?,
    val atime: Long?,
    val atime_nsec: Long?,
    val mtime: Long?,
    val mtime_nsec: Long?,
    val ctime: Long?,
    val ctime_nsec: Long?,
    val blocks: Long?,
    val blksize: Long?,
)

data class SusKstatItem(
    val path: String,
    val spoof_type: SusKstatType,
    val statically: SusKstatStatically?,
)

data class SusPathItem(
    val path: String,
    val is_loop: Boolean,
)

data class OpenRedirectItem(
    val target_path: String,
    val redirected_path: String,
    val uid_scheme: UidScheme,
)

data class SuSFSConfig(
    val version: Int,
    val enabled: Boolean,
    val cmdline_or_bootconfig: String,
    val avc_log_spoofing: Boolean,
    val logging: Boolean,
    val hide_sus_mnts_for_non_su_procs: Boolean,
    val uname: UnameConfig,
    val sus_path: Set<SusPathItem>,
    val sus_kstat: Set<SusKstatItem>,
    val open_redirect: Set<OpenRedirectItem>,
    val sus_map: Set<String>,
) {
}
