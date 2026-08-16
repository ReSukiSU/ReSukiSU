package com.resukisu.resukisu.data.application

import com.resukisu.resukisu.data.kernel.KernelRepository
import com.resukisu.resukisu.data.packageinfo.InstalledPackageRepository
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.domain.model.DynamicManagerApp
import com.resukisu.resukisu.domain.model.DynamicManagerConfig
import com.resukisu.resukisu.domain.model.DynamicManagerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class DynamicManagerRepository(
    private val kernelRepository: KernelRepository,
    private val installedPackageRepository: InstalledPackageRepository,
    private val ksuCliRepository: KsuCliRepository,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(DynamicManagerState())
    val state: StateFlow<DynamicManagerState> = mutableState.asStateFlow()

    suspend fun refresh(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            mutableState.update { it.copy(isRefreshing = !it.isLoading) }
            runCatching {
                installedPackageRepository.refresh().getOrThrow()
                val signatureIndexes = managerSignatureIndexes()
                val apps = installedPackageRepository.packages.value
                    .mapNotNull { packageInfo ->
                        val nativeLibraryDir = packageInfo.nativeLibraryDir
                        if (nativeLibraryDir.isNullOrBlank() ||
                            !File(nativeLibraryDir, "libksud.so").isFile
                        ) {
                            return@mapNotNull null
                        }
                        val uid = packageInfo.uid
                        val signatureIndex = signatureIndexes[uid % PER_USER_RANGE]
                        DynamicManagerApp(
                            label = packageInfo.appLabel.ifBlank { packageInfo.packageName },
                            packageName = packageInfo.packageName,
                            uid = uid,
                            apkPath = packageInfo.apkPath,
                            isSelected = signatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX,
                            managerSignatureIndex = signatureIndex,
                            isChangeable = signatureIndex == null ||
                                    signatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX,
                        )
                    }
                    .sortedWith(appComparator)
                mutableState.value = DynamicManagerState(
                    config = ksuCliRepository.getDynamicManagerConfig()?.let {
                        DynamicManagerConfig(size = it.size, hash = it.hash)
                    },
                    apps = apps,
                    isLoading = false,
                    isRefreshing = false,
                )
            }.onFailure {
                mutableState.update { current ->
                    current.copy(isLoading = false, isRefreshing = false)
                }
            }
        }
    }

    suspend fun selectManager(apkPath: String): Result<Unit> = submit {
        ksuCliRepository.setDynamicManagerApk(apkPath)
    }

    suspend fun setManual(size: Int, hash: String): Result<Unit> = submit {
        ksuCliRepository.setDynamicManager(size, hash)
    }

    suspend fun clear(): Result<Unit> = submit(ksuCliRepository::clearDynamicManager)

    private suspend fun submit(command: () -> Boolean): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            mutableState.update { it.copy(isSubmitting = true) }
            runCatching {
                check(command())
                val signatureIndexes = managerSignatureIndexes()
                mutableState.update { current ->
                    current.copy(
                        config = ksuCliRepository.getDynamicManagerConfig()?.let {
                            DynamicManagerConfig(size = it.size, hash = it.hash)
                        },
                        apps = current.apps.map { app ->
                            val signatureIndex = signatureIndexes[app.uid % PER_USER_RANGE]
                            app.copy(
                                isSelected = signatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX,
                                managerSignatureIndex = signatureIndex,
                                isChangeable = signatureIndex == null ||
                                        signatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX,
                            )
                        }.sortedWith(appComparator),
                        isSubmitting = false,
                    )
                }
            }.onFailure {
                mutableState.update { current -> current.copy(isSubmitting = false) }
            }
        }
    }

    private suspend fun managerSignatureIndexes(): Map<Int, Int> =
        kernelRepository.getManagerRuntimeInfo().managers.associate { it.uid to it.signatureIndex }

    private companion object {
        const val DYNAMIC_MANAGER_SIGNATURE_INDEX = 255
        const val PER_USER_RANGE = 100_000
        val appComparator =
            compareByDescending<DynamicManagerApp> { it.managerSignatureIndex != null }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
    }
}
