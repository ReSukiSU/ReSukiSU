package com.resukisu.resukisu.data.packageinfo

import android.app.Application
import androidx.core.content.pm.PackageInfoCompat
import com.resukisu.resukisu.domain.model.InstalledPackageInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class InstalledPackageRepository(
    application: Application,
    private val packageCache: InstalledPackageCache,
    private val rootServiceRepository: RootServiceRepository,
) {
    private val packageManager = application.packageManager
    private val refreshMutex = Mutex()
    private val mutablePackages = MutableStateFlow<List<InstalledPackageInfo>>(emptyList())

    val packages: StateFlow<List<InstalledPackageInfo>> = mutablePackages.asStateFlow()

    suspend fun refresh(): Result<Unit> = refreshMutex.withLock {
        try {
            val sourcePackages = rootServiceRepository.getInstalledPackages()
            packageCache.replace(sourcePackages)
            mutablePackages.value = withContext(Dispatchers.IO) {
                sourcePackages.map { packageInfo ->
                    val appInfo = packageInfo.applicationInfo
                    InstalledPackageInfo(
                        packageName = packageInfo.packageName,
                        versionName = packageInfo.versionName.orEmpty(),
                        versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                        appLabel = appInfo?.loadLabel(packageManager)?.toString().orEmpty(),
                        isSystem = appInfo?.let {
                            it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                        } ?: false,
                        uid = appInfo?.uid ?: -1,
                        apkPath = appInfo?.sourceDir.orEmpty(),
                        nativeLibraryDir = appInfo?.nativeLibraryDir,
                    )
                }
            }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
