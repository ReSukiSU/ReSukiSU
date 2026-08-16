package com.resukisu.resukisu.domain.model

data class InstalledPackageInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val appLabel: String,
    val isSystem: Boolean,
    val uid: Int,
    val apkPath: String = "",
    val nativeLibraryDir: String? = null,
)
