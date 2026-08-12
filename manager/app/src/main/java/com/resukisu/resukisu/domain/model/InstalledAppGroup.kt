package com.resukisu.resukisu.domain.model

data class InstalledApp(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean = false,
    val firstInstallTime: Long = 0L,
)

data class InstalledAppGroup(
    val uid: Int,
    val primaryPackageName: String,
    val apps: List<InstalledApp>,
    val profile: AppProfile? = null,
    val userName: String? = null,
    val shouldUmount: Boolean = false,
) {
    val mainApp: InstalledApp
        get() = apps.first { it.packageName == primaryPackageName }

    val packageNames: List<String>
        get() = apps.map(InstalledApp::packageName)

    val allowSu: Boolean
        get() = profile?.allowSu == true

    val hasCustomProfile: Boolean
        get() = profile?.let {
            if (it.allowSu) !it.rootUseDefault else !it.nonRootUseDefault
        } ?: false

    val isRecentlyInstalled: Boolean
        get() {
            val cutoff = System.currentTimeMillis() - RECENTLY_INSTALLED_WINDOW_MILLIS
            return apps.maxOfOrNull(InstalledApp::firstInstallTime)?.let { it >= cutoff } == true
        }
}

data class SuperUserState(
    val groups: List<InstalledAppGroup> = emptyList(),
    val refreshing: Boolean = false,
    val loadingProgress: Float = 0f,
)

sealed interface AllowlistOperationResult {
    data object Success : AllowlistOperationResult
    data object InvalidFile : AllowlistOperationResult
    data object UnsupportedVersion : AllowlistOperationResult
    data class ProfileUpdateFailed(val uid: Int) : AllowlistOperationResult
    data class Failed(val cause: Throwable? = null) : AllowlistOperationResult
}

private const val RECENTLY_INSTALLED_WINDOW_MILLIS = 60 * 60 * 1000L
