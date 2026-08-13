package com.resukisu.resukisu.domain.model

enum class ManagerUpdateChannel {
    STABLE,
    BETA,
}

sealed interface ManagerApkSource {
    val url: String

    data class DirectApk(override val url: String) : ManagerApkSource

    data class NightlyArtifact(
        override val url: String,
        val preferredAbi: String,
        val expectedVersionCode: Int,
    ) : ManagerApkSource
}

data class ManagerUpdateInfo(
    val channel: ManagerUpdateChannel,
    val versionCode: Int,
    val versionName: String,
    val abi: String,
    val fileName: String,
    val source: ManagerApkSource,
    val changelog: String = "",
)

