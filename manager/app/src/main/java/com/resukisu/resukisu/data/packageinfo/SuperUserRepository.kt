package com.resukisu.resukisu.data.packageinfo

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import com.resukisu.resukisu.data.profile.AppProfileKey
import com.resukisu.resukisu.data.profile.ProfileRepository
import com.resukisu.resukisu.domain.model.AllowlistOperationResult
import com.resukisu.resukisu.domain.model.AllowlistRestoreResult
import com.resukisu.resukisu.domain.model.InstalledApp
import com.resukisu.resukisu.domain.model.InstalledAppGroup
import com.resukisu.resukisu.domain.model.SuperUserState
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SuperUserRepository(
    private val application: Application,
    private val cache: InstalledPackageCache,
    private val installedPackageRepository: InstalledPackageRepository,
    private val profileRepository: ProfileRepository,
    applicationScope: CoroutineScope,
) {
    private val refreshMutex = Mutex()
    private val mutableState = MutableStateFlow(SuperUserState())
    val state: StateFlow<SuperUserState> = combine(
        mutableState,
        profileRepository.profiles,
    ) { source, profiles ->
        source.copy(
            groups = source.groups.map { group ->
                val snapshot = profiles[AppProfileKey(group.primaryPackageName, group.uid)]
                    ?: return@map group
                group.copy(
                    profile = snapshot.profile,
                    shouldUmount = snapshot.shouldUmount,
                )
            }
        )
    }.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = SuperUserState(),
    )

    suspend fun refresh(): Result<Unit> = refreshMutex.withLock {
        mutableState.update { it.copy(refreshing = true, loadingProgress = 0f) }
        try {
            installedPackageRepository.refresh().getOrThrow()
            val packages = cache.packages.value
            val groups = withContext(Dispatchers.IO) {
                val packageManager = application.packageManager
                packages.mapNotNull { info ->
                    val applicationInfo = info.applicationInfo ?: return@mapNotNull null
                    if (info.packageName == application.packageName) return@mapNotNull null
                    InstalledApp(
                        packageName = info.packageName,
                        label = applicationInfo.loadLabel(packageManager).toString(),
                        uid = applicationInfo.uid,
                        isSystem = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        firstInstallTime = info.firstInstallTime,
                    )
                }.groupBy(InstalledApp::uid).map { (uid, uidApps) ->
                    val sorted = uidApps.sortedBy(InstalledApp::label)
                    val primary = sorted.first()
                    val profile = profileRepository.getProfileSnapshot(primary.packageName, uid)
                    InstalledAppGroup(
                        uid = uid,
                        primaryPackageName = primary.packageName,
                        apps = sorted,
                        profile = profile.profile,
                        userName = profileRepository.getUserName(uid),
                        shouldUmount = profile.shouldUmount,
                    )
                }
            }
            mutableState.value = SuperUserState(
                groups = groups,
                refreshing = false,
                loadingProgress = 1f,
            )
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            mutableState.update { it.copy(refreshing = false) }
        }
    }

    suspend fun backupAllowlist(uri: String): AllowlistOperationResult =
        withContext(Dispatchers.IO) {
            try {
                SuFileInputStream.open(SuFile("/data/adb/ksu/.allowlist")).use { input ->
                    val output = application.contentResolver.openOutputStream(uri.toUri())
                        ?: return@withContext AllowlistOperationResult.Failed()
                    output.use(input::copyTo)
                }
                AllowlistOperationResult.Success
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AllowlistOperationResult.Failed(error)
            }
        }

    suspend fun restoreAllowlist(uri: String): AllowlistOperationResult =
        withContext(Dispatchers.IO) {
            try {
                val status = application.contentResolver.openFileDescriptor(uri.toUri(), "r")
                    ?.use { profileRepository.restoreAllowlist(it.fd) }
                    ?: return@withContext AllowlistOperationResult.InvalidFile
                when (status) {
                    AllowlistRestoreResult.Success -> AllowlistOperationResult.Success
                    AllowlistRestoreResult.InvalidFile -> AllowlistOperationResult.InvalidFile
                    AllowlistRestoreResult.UnsupportedVersion -> AllowlistOperationResult.UnsupportedVersion
                    is AllowlistRestoreResult.ProfileUpdateFailed ->
                        AllowlistOperationResult.ProfileUpdateFailed(status.uid)

                    AllowlistRestoreResult.Failed -> AllowlistOperationResult.Failed()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AllowlistOperationResult.Failed(error)
            }
        }

    suspend fun getAppGroup(uid: Int, primaryPackageName: String): InstalledAppGroup =
        withContext(Dispatchers.IO) {
            val packageManager = application.packageManager
            val cached = cache.packages.value
            val packages = (cached.ifEmpty { installedPackages(packageManager) })
                .filter { it.applicationInfo?.uid == uid }
                .ifEmpty {
                    listOfNotNull(runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            packageManager.getPackageInfo(
                                primaryPackageName,
                                PackageManager.PackageInfoFlags.of(0),
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            packageManager.getPackageInfo(primaryPackageName, 0)
                        }
                    }.getOrNull())
                }
            val apps = packages.map { it.toDomain(packageManager, uid) }
                .sortedWith(compareBy<InstalledApp> { it.packageName != primaryPackageName }
                    .thenBy(InstalledApp::label))
            require(apps.any { it.packageName == primaryPackageName })
            InstalledAppGroup(uid, primaryPackageName, apps)
        }

    private fun installedPackages(packageManager: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }

    private fun PackageInfo.toDomain(
        packageManager: PackageManager,
        fallbackUid: Int
    ): InstalledApp {
        val info = applicationInfo
        return InstalledApp(
            packageName = packageName,
            label = info?.loadLabel(packageManager)?.toString().orEmpty().ifBlank { packageName },
            uid = info?.uid ?: fallbackUid,
            isSystem = info?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0,
            firstInstallTime = firstInstallTime,
        )
    }
}
