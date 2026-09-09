package com.resukisu.resukisu.domain.model

const val WEBVIEW_ZYGOTE_UID = 1053
const val WEBVIEW_ZYGOTE_PROFILE_KEY = "webview_zygote"

data class InstalledApp(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean = false,
    val firstInstallTime: Long = 0L,
    val profileKey: String = packageName,
    val special: Boolean = false,
) {
    val displayIdentifier: String
        get() = if (special) profileKey else packageName

    val isWebViewZygote: Boolean
        get() = special && uid == WEBVIEW_ZYGOTE_UID
}

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

    val profileKey: String
        get() = mainApp.profileKey

    val isWebViewZygote: Boolean
        get() = mainApp.isWebViewZygote

    val packageNames: List<String>
        get() = apps.map(InstalledApp::packageName)

    val allowSu: Boolean
        get() = !isWebViewZygote && profile?.allowSu == true

    val hasCustomProfile: Boolean
        get() = profile?.let {
            if (isWebViewZygote) {
                !it.nonRootUseDefault
            } else if (it.allowSu) {
                !it.rootUseDefault
            } else {
                !it.nonRootUseDefault
            }
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
