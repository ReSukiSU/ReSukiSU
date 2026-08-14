package com.resukisu.resukisu.data.profile

import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.domain.model.AllowlistRestoreResult
import com.resukisu.resukisu.domain.model.AppControlAction
import com.resukisu.resukisu.domain.model.AppProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class AppProfileKey(
    val packageName: String,
    val uid: Int,
)

data class AppProfileSnapshot(
    val profile: AppProfile,
    val shouldUmount: Boolean,
)

class ProfileRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    private val mutableProfiles =
        MutableStateFlow<Map<AppProfileKey, AppProfileSnapshot>>(emptyMap())
    val profiles: StateFlow<Map<AppProfileKey, AppProfileSnapshot>> =
        mutableProfiles.asStateFlow()

    suspend fun getProfile(packageName: String, uid: Int): AppProfile =
        getProfileSnapshot(packageName, uid).profile

    suspend fun getProfileSnapshot(packageName: String, uid: Int): AppProfileSnapshot =
        withContext(Dispatchers.IO) {
            AppProfileSnapshot(
                profile = Natives.getAppProfile(packageName, uid).toDomain(),
                shouldUmount = Natives.uidShouldUmount(uid),
            ).also { snapshot ->
                updateProfile(AppProfileKey(packageName, uid), snapshot)
            }
        }

    suspend fun setProfile(profile: AppProfile): Boolean = withContext(Dispatchers.IO) {
        Natives.setAppProfile(profile.toNative()).also { saved ->
            if (saved) {
                updateProfile(
                    key = AppProfileKey(profile.name, profile.currentUid),
                    snapshot = AppProfileSnapshot(
                        profile = profile,
                        shouldUmount = Natives.uidShouldUmount(profile.currentUid),
                    ),
                )
            }
        }
    }

    suspend fun getUserName(uid: Int): String? =
        withContext(Dispatchers.IO) { Natives.getUserName(uid) }

    suspend fun uidShouldUmount(uid: Int): Boolean =
        withContext(Dispatchers.IO) { Natives.uidShouldUmount(uid) }

    suspend fun isDefaultUmountModules(): Boolean =
        withContext(Dispatchers.IO) { Natives.isDefaultUmountModules() }

    suspend fun restoreAllowlist(fd: Int): AllowlistRestoreResult =
        withContext(Dispatchers.IO) {
            val failedUid = IntArray(1)
            when (Natives.restoreAllowlistFromFd(fd, failedUid)) {
                Natives.ALLOWLIST_RESTORE_SUCCESS -> AllowlistRestoreResult.Success
                Natives.ALLOWLIST_RESTORE_INVALID_FILE -> AllowlistRestoreResult.InvalidFile
                Natives.ALLOWLIST_RESTORE_UNSUPPORTED_VERSION -> AllowlistRestoreResult.UnsupportedVersion
                Natives.ALLOWLIST_RESTORE_PROFILE_ERROR -> AllowlistRestoreResult.ProfileUpdateFailed(
                    failedUid[0]
                )

                else -> AllowlistRestoreResult.Failed
            }
        }

    suspend fun getSepolicy(packageName: String): String = withContext(Dispatchers.IO) {
        ksuCliRepository.getSepolicy(packageName)
    }

    suspend fun setSepolicy(packageName: String, rules: String): Boolean =
        withContext(Dispatchers.IO) { ksuCliRepository.setSepolicy(packageName, rules) }

    suspend fun isSepolicyValid(rules: String): Boolean =
        withContext(Dispatchers.IO) { ksuCliRepository.isSepolicyValid(rules) }

    suspend fun controlApp(
        packageName: String,
        action: AppControlAction,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (action) {
                AppControlAction.LAUNCH -> ksuCliRepository.launchApp(packageName)
                AppControlAction.FORCE_STOP -> ksuCliRepository.forceStopApp(packageName)
                AppControlAction.RESTART -> ksuCliRepository.restartApp(packageName)
            }
        }
    }

    private fun updateProfile(key: AppProfileKey, snapshot: AppProfileSnapshot) {
        mutableProfiles.update { profiles -> profiles + (key to snapshot) }
    }
}
