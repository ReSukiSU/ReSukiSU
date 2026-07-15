package com.resukisu.resukisu.ui.viewmodel

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.util.DynamicManagerCliConfig
import com.resukisu.resukisu.ui.util.clearDynamicManager
import com.resukisu.resukisu.ui.util.getDynamicManagerConfig
import com.resukisu.resukisu.ui.util.setDynamicManager
import com.resukisu.resukisu.ui.util.setDynamicManagerApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class DynamicManagerUiState(
    val config: DynamicManagerCliConfig? = null,
    val apps: List<DynamicManagerAppItem> = emptyList(),
    val search: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
)

@Immutable
data class DynamicManagerAppItem(
    val label: String,
    val packageName: String,
    val uid: Int,
    val packageInfo: PackageInfo,
    val apkPath: String,
    val isSelected: Boolean = false,
    val managerSignatureIndex: Int? = null,
    val isChangeable: Boolean = true,
)

class DynamicManagerViewModel : ViewModel() {
    private companion object {
        const val DYNAMIC_MANAGER_SIGNATURE_INDEX = 255
    }

    private val _uiState = MutableStateFlow(DynamicManagerUiState())
    val uiState: StateFlow<DynamicManagerUiState> = _uiState.asStateFlow()

    private var allApps: List<DynamicManagerAppItem> = emptyList()

    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            _uiState.update { it.copy(isRefreshing = !it.isLoading) }

            if (SuperUserViewModel.getCachedApps(includeManager = true).isEmpty()) {
                ViewModelProvider(ksuApp)[SuperUserViewModel::class.java].fetchAppList()
            }

            val config = getDynamicManagerConfig()
            val managerSignatureIndexes = getManagerSignatureIndexes()
            allApps = loadManagerCandidateApps()
                .mapNotNull { app ->
                    val applicationInfo = app.packageInfo.applicationInfo ?: return@mapNotNull null
                    if (!hasKsuDaemon(applicationInfo.nativeLibraryDir)) return@mapNotNull null
                    val managerSignatureIndex =
                        managerSignatureIndexes[app.uid % 100000] // PER_USER_RANGE
                    DynamicManagerAppItem(
                        label = app.label,
                        packageName = app.packageName,
                        uid = app.uid,
                        packageInfo = app.packageInfo,
                        apkPath = applicationInfo.sourceDir,
                        isSelected = managerSignatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX,
                        managerSignatureIndex = managerSignatureIndex,
                        isChangeable = managerSignatureIndex == null ||
                                managerSignatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX,
                    )
                }
                .sortedWith(dynamicManagerAppComparator())

            _uiState.update { state ->
                state.copy(
                    config = config,
                    apps = filterApps(state.search),
                    isLoading = false,
                    isRefreshing = false,
                )
            }
        }
    }

    private fun hasKsuDaemon(nativeLibraryDir: String?): Boolean {
        if (nativeLibraryDir.isNullOrBlank()) return false
        return File(nativeLibraryDir, "libksud.so").isFile
    }

    private fun loadManagerCandidateApps(): List<SuperUserViewModel.AppInfo> {
        val cachedApps = SuperUserViewModel.getCachedApps(includeManager = true)
        if (cachedApps.isNotEmpty()) return cachedApps

        return runCatching {
            val packageInfo = ksuApp.packageManager.getPackageInfo(
                ksuApp.packageName,
                PackageManager.GET_META_DATA
            )
            val label = packageInfo.applicationInfo
                ?.loadLabel(ksuApp.packageManager)
                ?.toString()
                ?: ksuApp.packageName
            listOf(
                SuperUserViewModel.AppInfo(
                    label = label,
                    packageInfo = packageInfo,
                    profile = null,
                )
            )
        }.getOrDefault(emptyList())
    }

    fun updateSearch(search: String) {
        _uiState.update {
            it.copy(
                search = search,
                apps = filterApps(search)
            )
        }
    }

    suspend fun setManagerApp(app: DynamicManagerAppItem): Boolean {
        return setManagerApk(app.apkPath)
    }

    suspend fun setManualConfig(size: Int, hash: String): Boolean {
        return submit {
            setDynamicManager(size, hash)
        }
    }

    suspend fun clearConfig(): Boolean {
        return submit {
            clearDynamicManager()
        }
    }

    private suspend fun setManagerApk(apkPath: String): Boolean {
        return submit {
            setDynamicManagerApk(apkPath)
        }
    }

    private suspend fun submit(block: () -> Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            _uiState.update { it.copy(isSubmitting = true) }
            val success = block()
            val config = if (success) getDynamicManagerConfig() else _uiState.value.config
            val managerSignatureIndexes = if (success) {
                getManagerSignatureIndexes()
            } else {
                selectedSignatureIndexes()
            }
            allApps = markSelected(allApps, managerSignatureIndexes)
            _uiState.update {
                it.copy(
                    config = config,
                    apps = filterApps(it.search),
                    isSubmitting = false,
                )
            }
            success
        }
    }

    private fun markSelected(
        apps: List<DynamicManagerAppItem>,
        managerSignatureIndexes: Map<Int, Int>
    ): List<DynamicManagerAppItem> {
        return apps.map { app ->
            val managerSignatureIndex = managerSignatureIndexes[app.uid % 100000] // PER_USER_RANGE
            app.copy(
                isSelected = managerSignatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX,
                managerSignatureIndex = managerSignatureIndex,
                isChangeable = managerSignatureIndex == null ||
                        managerSignatureIndex == DYNAMIC_MANAGER_SIGNATURE_INDEX,
            )
        }.sortedWith(dynamicManagerAppComparator())
    }

    private fun getManagerSignatureIndexes(): Map<Int, Int> {
        return runCatching {
            Natives.getManagersList()?.managers
                ?.associate { it.uid to it.signatureIndex }
                .orEmpty()
        }.getOrDefault(emptyMap())
    }

    private fun selectedSignatureIndexes(): Map<Int, Int> {
        return allApps.mapNotNull { app ->
            app.managerSignatureIndex?.let { app.uid to it }
        }.toMap()
    }

    private fun filterApps(search: String): List<DynamicManagerAppItem> {
        val query = search.trim()
        if (query.isEmpty()) return allApps

        return allApps.filter { app ->
            app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
        }
    }

    private fun dynamicManagerAppComparator(): Comparator<DynamicManagerAppItem> {
        return compareByDescending<DynamicManagerAppItem> { it.managerSignatureIndex != null }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
    }
}
