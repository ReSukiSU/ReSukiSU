package com.resukisu.resukisu.ui.viewmodel

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.data.appPreferences
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.util.HanziToPinyin
import com.resukisu.resukisu.ui.util.getRootShell
import com.resukisu.resukisu.ui.util.listModules
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

data class ModuleUiState(
    val moduleList: List<ModuleViewModel.ModuleInfo> = emptyList(),
    val moduleSizes: Map<String, String> = emptyMap(),
    val isRefreshing: Boolean = false,
    val search: String = "",
    val sortEnabledFirst: Boolean = false,
    val sortActionFirst: Boolean = false,
    val hasModuleRequireMount: Boolean = false,
    val isNeedRefresh: Boolean = false,
    val showMoreModuleInfo: Boolean = false,
    val isHideTagRow: Boolean = false
)

class ModuleViewModel : ViewModel() {

    companion object {
        private const val TAG = "ModuleViewModel"
    }

    private var modules: List<ModuleInfo> = emptyList()
    private val _uiState = MutableStateFlow(ModuleUiState())
    val uiState: StateFlow<ModuleUiState> = _uiState.asStateFlow()

    fun loadSize(dirId: String) = viewModelScope.launch(Dispatchers.IO) {
        val size = formatFileSize(
            try {
                val shell = getRootShell()
                val command = "/data/adb/ksu/bin/busybox du -sb /data/adb/modules/$dirId"
                val result = shell.newJob().add(command).to(ArrayList(), null).exec()

                if (result.isSuccess && result.out.isNotEmpty()) {
                    val sizeStr = result.out.firstOrNull()?.split("\t")?.firstOrNull()
                    sizeStr?.toLongOrNull() ?: 0L
                } else {
                    0L
                }
            } catch (e: Exception) {
                Log.e(TAG, "calculate module size failed $dirId: ${e.message}")
                0L
            }
        )
        _uiState.update { state ->
            state.copy(moduleSizes = state.moduleSizes + (dirId to size))
        }
    }

    private fun updateBooleanPref(
        context: Context,
        key: String,
        value: Boolean,
        reducer: (ModuleUiState) -> ModuleUiState,
    ) {
        context.appPreferences.putBoolean(key, value)
        _uiState.update(reducer)
    }

    fun handleHideTagRowChange(context: Context, newValue: Boolean) {
        updateBooleanPref(context, "is_hide_tag_row", newValue) { it.copy(isHideTagRow = newValue) }
    }

    fun handleHideTagRowChange(newValue: Boolean) {
        handleHideTagRowChange(ksuApp, newValue)
    }

    fun handleShowMoreModuleInfoChange(context: Context, newValue: Boolean) {
        updateBooleanPref(context, "show_more_module_info", newValue) {
            it.copy(showMoreModuleInfo = newValue)
        }
    }

    data class ModuleUpdateInfo(
        val zipUrl: String,
        val version: String,
        val changelog: String
    )

    @Stable
    data class ModuleInfo(
        val id: String,
        val name: String,
        val author: String,
        val version: String,
        val versionCode: Int,
        val description: String,
        val enabled: Boolean,
        val update: Boolean,
        val remove: Boolean,
        val updateJson: String,
        val hasWebUi: Boolean,
        val hasActionScript: Boolean,
        val metamodule: Boolean,
        val actionIconPath: String?,
        val webUiIconPath: String?,
        val dirId: String,
        val moduleUpdate: ModuleUpdateInfo?
    )

    fun updateSearch(search: String) {
        _uiState.update { state ->
            state.copy(
                search = search,
                moduleList = buildModuleList(
                    search = search,
                    sortEnabledFirst = state.sortEnabledFirst,
                    sortActionFirst = state.sortActionFirst,
                )
            )
        }
    }

    fun setSortEnabledFirst(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                sortEnabledFirst = enabled,
                moduleList = buildModuleList(
                    search = state.search,
                    sortEnabledFirst = enabled,
                    sortActionFirst = state.sortActionFirst,
                )
            )
        }
    }

    fun setSortActionFirst(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                sortActionFirst = enabled,
                moduleList = buildModuleList(
                    search = state.search,
                    sortEnabledFirst = state.sortEnabledFirst,
                    sortActionFirst = enabled,
                )
            )
        }
    }

    fun setSortOptions(sortEnabledFirst: Boolean, sortActionFirst: Boolean) {
        _uiState.update { state ->
            state.copy(
                sortEnabledFirst = sortEnabledFirst,
                sortActionFirst = sortActionFirst,
                moduleList = buildModuleList(
                    search = state.search,
                    sortEnabledFirst = sortEnabledFirst,
                    sortActionFirst = sortActionFirst,
                )
            )
        }
    }

    fun markNeedRefresh() {
        _uiState.update { it.copy(isNeedRefresh = true) }
    }

    fun fetchModuleList(
        manualRefresh: Boolean = false,
        callBack: () -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRefreshing = true) }

            val oldModuleList = modules
            val start = SystemClock.elapsedRealtime()

            runCatching {
                val result = listModules()
                Log.i(TAG, "result: $result")

                val moduleVersionKeys = mutableListOf<String>()
                if (!manualRefresh) {
                    oldModuleList.forEach { module ->
                        moduleVersionKeys.add(module.id + module.versionCode)
                    }
                }

                val array = JSONArray(result)
                modules = (0 until array.length())
                    .asSequence()
                    .map { array.getJSONObject(it) }
                    .map { obj ->
                        val moduleId = obj.getString("id")
                        val moduleVersionCode = obj.getIntCompat("versionCode", 0)
                        val enabled = obj.getBooleanCompat("enabled")
                        val update = obj.getBooleanCompat("update")
                        val remove = obj.getBooleanCompat("remove")
                        val updateJson = obj.optString("updateJson")

                        ModuleInfo(
                            id = moduleId,
                            name = obj.optString("name"),
                            author = obj.optString("author", "Unknown"),
                            version = obj.optString("version", "Unknown"),
                            versionCode = moduleVersionCode,
                            description = obj.optString("description"),
                            enabled = enabled,
                            update = update,
                            remove = remove,
                            updateJson = updateJson,
                            hasWebUi = obj.getBooleanCompat("web"),
                            hasActionScript = obj.getBooleanCompat("action"),
                            metamodule = obj.getBooleanCompat("metamodule"),
                            actionIconPath = obj.optString("actionIcon").takeIf { it.isNotBlank() },
                            webUiIconPath = obj.optString("webuiIcon").takeIf { it.isNotBlank() },
                            dirId = obj.optString("dir_id", obj.getString("id")),
                            moduleUpdate = null,
                        )
                    }.toList()

                val hasModuleRequireMount = modules.map { module ->
                    async(Dispatchers.IO) {
                        SuFile.open("/data/adb/modules/${module.id}/system").exists()
                                && !SuFile.open("/data/adb/modules/${module.id}/skip_mount")
                            .exists()
                                && !SuFile.open("/data/adb/modules/${module.id}/disable").exists()
                                && !SuFile.open("/data/adb/modules/${module.id}/remove").exists()
                    }
                }.awaitAll().any { it }

                modules = modules.map { module ->
                    async(Dispatchers.IO) {
                        module.copy(
                            moduleUpdate = if (
                                !moduleVersionKeys.contains(module.id + module.versionCode) ||
                                module.updateJson.isEmpty() ||
                                module.remove ||
                                module.update ||
                                !module.enabled
                            ) {
                                checkUpdate(module.updateJson, module.versionCode)
                            } else {
                                null
                            }
                        )
                    }
                }.awaitAll()

                _uiState.update { state ->
                    state.copy(
                        moduleList = buildModuleList(
                            search = state.search,
                            sortEnabledFirst = state.sortEnabledFirst,
                            sortActionFirst = state.sortActionFirst,
                        ),
                        hasModuleRequireMount = hasModuleRequireMount,
                        isNeedRefresh = false,
                        isRefreshing = false,
                    )
                }
            }.onFailure { e ->
                Log.e(TAG, "fetchModuleList: ", e)
                _uiState.update { it.copy(isRefreshing = false) }
            }

            if (oldModuleList === modules) {
                _uiState.update { it.copy(isRefreshing = false) }
            }

            Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}, modules: $modules")
            callBack()
        }
    }

    private fun buildModuleList(
        search: String,
        sortEnabledFirst: Boolean,
        sortActionFirst: Boolean,
    ): List<ModuleInfo> {
        val comparator =
            compareBy<ModuleInfo>(
                {
                    val executable = it.hasWebUi || it.hasActionScript
                    when {
                        it.metamodule && it.enabled -> 0
                        sortEnabledFirst && sortActionFirst -> when {
                            it.enabled && executable -> 1
                            it.enabled -> 2
                            executable -> 3
                            else -> 4
                        }

                        sortEnabledFirst && !sortActionFirst -> if (it.enabled) 1 else 2
                        !sortEnabledFirst && sortActionFirst -> if (executable) 1 else 2
                        else -> 1
                    }
                },
                { if (sortEnabledFirst) !it.enabled else false },
                { if (sortActionFirst) !(it.hasWebUi || it.hasActionScript) else false },
            ).thenBy(Collator.getInstance(Locale.getDefault()), ModuleInfo::id)

        return modules.filter {
            it.id.contains(search, true) ||
                    it.name.contains(search, true) ||
                    HanziToPinyin.getInstance().toPinyinString(it.name)
                        ?.contains(search, true) == true
        }.sortedWith(comparator)
    }

    private fun sanitizeVersionString(version: String): String {
        return version.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
    }

    fun checkUpdate(updateUrl: String, versionCode: Int): ModuleUpdateInfo? {
        val isCheckUpdateEnabled =
            ksuApp.ensurePreferencesRepository().getBoolean("check_update", true)
        if (!isCheckUpdateEnabled) {
            return null
        }

        val result = runCatching {
            Log.i(TAG, "checkUpdate url: $updateUrl")
            val request = okhttp3.Request.Builder()
                .url(updateUrl)
                .build()
            val response = ksuApp.okhttpClient.newCall(request).execute()
            Log.d(TAG, "checkUpdate code: ${response.code}")
            if (response.isSuccessful) {
                response.body?.string() ?: ""
            } else {
                Log.d(TAG, "checkUpdate failed: ${response.message}")
                ""
            }
        }.getOrElse { e ->
            Log.e(TAG, "checkUpdate exception", e)
            ""
        }

        Log.i(TAG, "checkUpdate result: $result")

        if (result.isEmpty()) {
            return null
        }

        val updateJson = runCatching { JSONObject(result) }.getOrNull() ?: return null
        val version = sanitizeVersionString(updateJson.optString("version", ""))
        val onlineVersionCode = updateJson.optInt("versionCode", 0)
        val zipUrl = updateJson.optString("zipUrl", "")
        val changelog = updateJson.optString("changelog", "")
        if (onlineVersionCode <= versionCode || zipUrl.isEmpty()) {
            return null
        }

        return ModuleUpdateInfo(zipUrl, version, changelog)
    }
}

private fun JSONObject.getBooleanCompat(key: String, default: Boolean = false): Boolean {
    if (!has(key)) return default
    return when (val value = opt(key)) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true) || value == "1"
        is Number -> value.toInt() != 0
        else -> default
    }
}

private fun JSONObject.getIntCompat(key: String, default: Int = 0): Int {
    if (!has(key)) return default
    return when (val value = opt(key)) {
        is Int -> value
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }
}

fun formatFileSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val tb = gb * 1024

    return when {
        bytes >= tb -> "%.2f TB".format(bytes / tb)
        bytes >= gb -> "%.2f GB".format(bytes / gb)
        bytes >= mb -> "%.2f MB".format(bytes / mb)
        bytes >= kb -> "%.2f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}
