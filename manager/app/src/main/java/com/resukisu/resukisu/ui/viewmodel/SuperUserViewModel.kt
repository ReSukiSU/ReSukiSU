package com.resukisu.resukisu.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.KsuService
import com.resukisu.resukisu.ui.util.HanziToPinyin
import com.resukisu.zako.IKsuInterface
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.text.Collator
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal const val RECENTLY_INSTALLED_WINDOW_MILLIS = 60 * 60 * 1000L

enum class AppCategory(val displayNameRes: Int, val persistKey: String) {
    ALL(com.resukisu.resukisu.R.string.category_all_apps, "ALL"),
    ROOT(com.resukisu.resukisu.R.string.category_root_apps, "ROOT"),
    CUSTOM(com.resukisu.resukisu.R.string.category_custom_apps, "CUSTOM"),
    DEFAULT(com.resukisu.resukisu.R.string.category_default_apps, "DEFAULT");

    companion object {
        fun fromPersistKey(key: String): AppCategory = entries.find { it.persistKey == key } ?: ALL
    }
}

enum class SortType(val displayNameRes: Int, val persistKey: String) {
    NAME_ASC(com.resukisu.resukisu.R.string.sort_name_asc, "NAME_ASC"),
    NAME_DESC(com.resukisu.resukisu.R.string.sort_name_desc, "NAME_DESC"),
    INSTALL_TIME_NEW(com.resukisu.resukisu.R.string.sort_install_time_new, "INSTALL_TIME_NEW"),
    INSTALL_TIME_OLD(com.resukisu.resukisu.R.string.sort_install_time_old, "INSTALL_TIME_OLD"),
    SIZE_DESC(com.resukisu.resukisu.R.string.sort_size_desc, "SIZE_DESC"),
    SIZE_ASC(com.resukisu.resukisu.R.string.sort_size_asc, "SIZE_ASC"),
    USAGE_FREQ(com.resukisu.resukisu.R.string.sort_usage_freq, "USAGE_FREQ");

    companion object {
        fun fromPersistKey(key: String): SortType = entries.find { it.persistKey == key } ?: NAME_ASC
    }
}

data class SuperUserUiState(
    val appGroupList: List<SuperUserViewModel.AppGroup> = emptyList(),
    val search: String = "",
    val showSystemApps: Boolean = false,
    val selectedCategory: AppCategory = AppCategory.ALL,
    val currentSortType: SortType = SortType.NAME_ASC,
    val isRefreshing: Boolean = false,
    val loadingProgress: Float = 0f,
)

class SuperUserViewModel : ViewModel() {
    companion object {
        private const val TAG = "SuperUserViewModel"
        private val appsLock = Any()
        private var appsCache: List<AppInfo> = emptyList()

        @JvmStatic
        fun getAppIconDrawable(context: Context, packageName: String): Drawable? {
            val appList = synchronized(appsLock) { appsCache }
            return appList.find { it.packageName == packageName }
                ?.packageInfo?.applicationInfo?.loadIcon(context.packageManager)
        }

        private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        private const val KEY_SELECTED_CATEGORY = "selected_category"
        private const val KEY_CURRENT_SORT_TYPE = "current_sort_type"
        private const val CORE_POOL_SIZE = 8
        private const val MAX_POOL_SIZE = 16
        private const val KEEP_ALIVE_TIME = 60L

        @JvmStatic
        fun getCachedApps(): List<AppInfo> = synchronized(appsLock) { appsCache }
    }

    @Immutable
    @Parcelize
    data class AppInfo(
        val label: String,
        val packageInfo: PackageInfo,
        val profile: Natives.Profile?,
    ) : Parcelable {
        @IgnoredOnParcel
        val packageName: String = packageInfo.packageName
        @IgnoredOnParcel
        val uid: Int = packageInfo.applicationInfo!!.uid
    }

    @Immutable
    @Parcelize
    data class AppGroup(
        val uid: Int,
        val apps: List<AppInfo>,
        val profile: Natives.Profile?,
    ) : Parcelable {
        @IgnoredOnParcel
        val mainApp: AppInfo = apps.first()
        @IgnoredOnParcel
        val packageNames: List<String> = apps.map { it.packageName }
        @IgnoredOnParcel
        val allowSu: Boolean = profile?.allowSu == true
        @IgnoredOnParcel
        val userName: String? = Natives.getUserName(uid)
        @IgnoredOnParcel
        val hasCustomProfile: Boolean = profile?.let {
            if (it.allowSu) !it.rootUseDefault else !it.nonRootUseDefault
        } ?: false

        @IgnoredOnParcel
        val isRecentlyInstalled: Boolean = run {
            val cutoffMillis = System.currentTimeMillis() - RECENTLY_INSTALLED_WINDOW_MILLIS
            apps.maxOfOrNull { it.packageInfo.firstInstallTime }?.let { it >= cutoffMillis } == true
        }
    }

    private val appProcessingThreadPool = ThreadPoolExecutor(
        CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
        LinkedBlockingQueue()
    ) { runnable ->
        Thread(runnable, "AppProcessing-${System.currentTimeMillis()}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }.asCoroutineDispatcher()

    private val appListMutex = Mutex()
    private val configChangeListeners = mutableSetOf<(String) -> Unit>()
    private val prefs = ksuApp.ensurePreferencesRepository()
    private var appGroupsCache: List<AppGroup> = emptyList()

    private val _uiState = MutableStateFlow(
        SuperUserUiState(
            showSystemApps = prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false),
            selectedCategory = loadSelectedCategory(),
            currentSortType = loadCurrentSortType(),
        )
    )
    val uiState: StateFlow<SuperUserUiState> = _uiState.asStateFlow()

    private fun loadSelectedCategory(): AppCategory {
        val categoryKey = prefs.getString(KEY_SELECTED_CATEGORY, AppCategory.ALL.persistKey)
            ?: AppCategory.ALL.persistKey
        return AppCategory.fromPersistKey(categoryKey)
    }

    private fun loadCurrentSortType(): SortType {
        val sortKey = prefs.getString(KEY_CURRENT_SORT_TYPE, SortType.NAME_ASC.persistKey)
            ?: SortType.NAME_ASC.persistKey
        return SortType.fromPersistKey(sortKey)
    }

    fun updateSearch(search: String) {
        _uiState.update { state ->
            state.copy(
                search = search,
                appGroupList = buildAppGroupList(
                    search = search,
                    showSystemApps = state.showSystemApps,
                    selectedCategory = state.selectedCategory,
                    currentSortType = state.currentSortType,
                )
            )
        }
    }

    fun updateShowSystemApps(newValue: Boolean) {
        prefs.putBoolean(KEY_SHOW_SYSTEM_APPS, newValue)
        _uiState.update { state ->
            state.copy(
                showSystemApps = newValue,
                appGroupList = buildAppGroupList(
                    search = state.search,
                    showSystemApps = newValue,
                    selectedCategory = state.selectedCategory,
                    currentSortType = state.currentSortType,
                )
            )
        }
    }

    fun updateSelectedCategory(newCategory: AppCategory) {
        prefs.putString(KEY_SELECTED_CATEGORY, newCategory.persistKey)
        _uiState.update { state ->
            state.copy(
                selectedCategory = newCategory,
                appGroupList = buildAppGroupList(
                    search = state.search,
                    showSystemApps = state.showSystemApps,
                    selectedCategory = newCategory,
                    currentSortType = state.currentSortType,
                )
            )
        }
    }

    fun updateCurrentSortType(newSortType: SortType) {
        prefs.putString(KEY_CURRENT_SORT_TYPE, newSortType.persistKey)
        _uiState.update { state ->
            state.copy(
                currentSortType = newSortType,
                appGroupList = buildAppGroupList(
                    search = state.search,
                    showSystemApps = state.showSystemApps,
                    selectedCategory = state.selectedCategory,
                    currentSortType = newSortType,
                )
            )
        }
    }

    private suspend fun connectKsuService(onDisconnect: () -> Unit = {}): IBinder? =
        suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceDisconnected(name: ComponentName?) {
                    onDisconnect()
                }

                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    continuation.resume(binder)
                }
            }
            val intent = Intent(ksuApp, KsuService::class.java)
            try {
                val task = com.topjohnwu.superuser.ipc.RootService.bindOrTask(
                    intent, Shell.EXECUTOR, connection
                )
                task?.let { Shell.getShell().execTask(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind KsuService", e)
                continuation.resume(null)
            }
        }

    private fun stopKsuService() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val intent = Intent(ksuApp, KsuService::class.java)
                com.topjohnwu.superuser.ipc.RootService.stop(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop KsuService", e)
            }
        }
    }

    suspend fun fetchAppList() {
        if (_uiState.value.isRefreshing) return

        _uiState.update { it.copy(isRefreshing = true, loadingProgress = 0f) }

        try {
            val binder = connectKsuService() ?: run {
                _uiState.update { it.copy(isRefreshing = false) }
                return
            }

            withContext(Dispatchers.IO) {
                val pm = ksuApp.packageManager
                val allPackages = IKsuInterface.Stub.asInterface(binder)
                val total = allPackages.packageCount
                val pageSize = 100
                val result = mutableListOf<AppInfo>()

                var start = 0
                while (start < total) {
                    val page = allPackages.getPackages(start, pageSize)
                    if (page.isEmpty()) break

                    result += page.mapNotNull { packageInfo ->
                        packageInfo.applicationInfo?.let { appInfo ->
                            AppInfo(
                                label = appInfo.loadLabel(pm).toString(),
                                packageInfo = packageInfo,
                                profile = Natives.getAppProfile(
                                    packageInfo.packageName,
                                    appInfo.uid
                                )
                            )
                        }
                    }
                    start += page.size
                    _uiState.update { it.copy(loadingProgress = start.toFloat() / total) }
                }

                appListMutex.withLock {
                    val filteredApps = result.filter { it.packageName != ksuApp.packageName }
                    synchronized(appsLock) {
                        appsCache = filteredApps
                    }
                    appGroupsCache = groupAppsByUid(filteredApps)
                }
                _uiState.update { state ->
                    state.copy(
                        appGroupList = buildAppGroupList(
                            search = state.search,
                            showSystemApps = state.showSystemApps,
                            selectedCategory = state.selectedCategory,
                            currentSortType = state.currentSortType,
                        ),
                        loadingProgress = 1f,
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refresh app list", e)
        } finally {
            _uiState.update { it.copy(isRefreshing = false) }
            stopKsuService()
        }
    }

    private fun buildAppGroupList(
        search: String,
        showSystemApps: Boolean,
        selectedCategory: AppCategory,
        currentSortType: SortType,
    ): List<AppGroup> {
        return appGroupsCache.filter { group ->
            group.apps.any { app ->
                app.label.contains(search, true) ||
                        app.packageName.contains(search, true) ||
                        HanziToPinyin.getInstance().toPinyinString(app.label)?.contains(search, true) == true
            }
        }.filter { group ->
            group.uid == 2000 || showSystemApps ||
                    group.apps.any { it.packageInfo.applicationInfo!!.flags.and(ApplicationInfo.FLAG_SYSTEM) == 0 }
        }.run {
            when (selectedCategory) {
                AppCategory.ALL -> this
                AppCategory.ROOT -> this.filter { it.allowSu }
                AppCategory.CUSTOM -> this.filter { !it.allowSu && it.hasCustomProfile }
                AppCategory.DEFAULT -> this.filter { !it.allowSu && !it.hasCustomProfile }
            }
        }.sortedWith { group1, group2 ->
            val priority1 = when {
                group1.allowSu -> 0
                group1.isRecentlyInstalled -> 1
                group1.hasCustomProfile -> 2
                else -> 3
            }
            val priority2 = when {
                group2.allowSu -> 0
                group2.isRecentlyInstalled -> 1
                group2.hasCustomProfile -> 2
                else -> 3
            }

            val priorityComparison = priority1.compareTo(priority2)
            if (priorityComparison != 0) {
                priorityComparison
            } else {
                when (currentSortType) {
                    SortType.NAME_ASC -> group1.mainApp.label.lowercase()
                        .compareTo(group2.mainApp.label.lowercase())
                    SortType.NAME_DESC -> group2.mainApp.label.lowercase()
                        .compareTo(group1.mainApp.label.lowercase())
                    SortType.INSTALL_TIME_NEW -> group2.mainApp.packageInfo.firstInstallTime
                        .compareTo(group1.mainApp.packageInfo.firstInstallTime)
                    SortType.INSTALL_TIME_OLD -> group1.mainApp.packageInfo.firstInstallTime
                        .compareTo(group2.mainApp.packageInfo.firstInstallTime)
                    else -> group1.mainApp.label.lowercase()
                        .compareTo(group2.mainApp.label.lowercase())
                }
            }
        }
    }

    private fun groupAppsByUid(appList: List<AppInfo>): List<AppGroup> {
        return appList.groupBy { it.uid }
            .map { (uid, apps) ->
                val sortedApps = apps.sortedBy { it.label }
                val profile = apps.firstOrNull()?.let { Natives.getAppProfile(it.packageName, uid) }
                AppGroup(uid = uid, apps = sortedApps, profile = profile)
            }
            .sortedWith(
                compareBy<AppGroup> {
                    when {
                        it.allowSu -> 0
                        it.hasCustomProfile -> 1
                        else -> 2
                    }
                }.thenBy(Collator.getInstance(Locale.getDefault())) {
                    it.userName?.takeIf { name -> name.isNotBlank() } ?: it.uid.toString()
                }.thenBy(Collator.getInstance(Locale.getDefault())) { it.mainApp.label }
            )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            stopKsuService()
            appProcessingThreadPool.close()
            configChangeListeners.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up resources", e)
        }
    }
}
