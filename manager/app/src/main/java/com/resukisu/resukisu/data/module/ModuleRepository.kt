package com.resukisu.resukisu.data.module

import com.resukisu.resukisu.data.network.NetworkRequestRepository
import com.resukisu.resukisu.data.network.NetworkStatusRepository
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.domain.model.InstalledModule
import com.resukisu.resukisu.domain.model.InstalledModulesState
import com.resukisu.resukisu.domain.model.MetaModuleStatus
import com.resukisu.resukisu.domain.model.ModuleUpdateMetadata
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ModuleRepository(
    private val networkStatusRepository: NetworkStatusRepository,
    private val networkRequestRepository: NetworkRequestRepository,
    private val ksuCliRepository: KsuCliRepository,
) {
    private val refreshMutex = Mutex()
    private val mutableInstalledModules = MutableStateFlow(InstalledModulesState())
    val installedModules: StateFlow<InstalledModulesState> =
        mutableInstalledModules.asStateFlow()

    suspend fun refreshInstalledModules(
        manual: Boolean,
        checkUpdates: Boolean,
    ): Result<Unit> = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            mutableInstalledModules.update { it.copy(refreshing = true) }
            runCatching {
                val previous = mutableInstalledModules.value.modules
                val previousVersionKeys = if (manual) {
                    emptySet()
                } else {
                    previous.mapTo(mutableSetOf()) { it.id + it.versionCode }
                }
                val modules = parseModules(ksuCliRepository.listModules())
                val requiresMount = coroutineScope {
                    modules.map { module ->
                        async(Dispatchers.IO) {
                            val directory = "/data/adb/modules/${module.id}"
                            SuFile.open("$directory/system").exists() &&
                                    !SuFile.open("$directory/skip_mount").exists() &&
                                    !SuFile.open("$directory/disable").exists() &&
                                    !SuFile.open("$directory/remove").exists()
                        }
                    }.awaitAll().any { it }
                }
                mutableInstalledModules.value = InstalledModulesState(
                    modules = modules,
                    refreshing = false,
                    hasModuleRequireMount = requiresMount,
                    hasMagisk = ksuCliRepository.hasMagisk(),
                    metaModuleStatus = getMetaModuleStatus(),
                )

                if (checkUpdates && networkStatusRepository.isAvailable()) {
                    val withUpdates = coroutineScope {
                        modules.map { module ->
                            async(Dispatchers.IO) {
                                val shouldCheck =
                                    module.id + module.versionCode !in previousVersionKeys ||
                                            module.updateJson.isEmpty() ||
                                            module.remove || module.update || !module.enabled
                                module.copy(
                                    moduleUpdate = if (shouldCheck) {
                                        checkUpdate(
                                            module.updateJson,
                                            module.versionCode
                                        ).getOrNull()
                                    } else {
                                        null
                                    }
                                )
                            }
                        }.awaitAll()
                    }
                    mutableInstalledModules.update { current -> current.copy(modules = withUpdates) }
                }
            }.onFailure {
                mutableInstalledModules.update { current -> current.copy(refreshing = false) }
            }
        }
    }

    suspend fun calculateInstalledModuleSize(moduleId: String): Long =
        withContext(Dispatchers.IO) {
            runCatching {
                val result = ksuCliRepository.getRootShell().newJob()
                    .add("/data/adb/ksu/bin/busybox du -sb /data/adb/modules/$moduleId")
                    .to(ArrayList(), null)
                    .exec()
                if (result.isSuccess) {
                    result.out.firstOrNull()?.split('\t')?.firstOrNull()?.toLongOrNull() ?: 0L
                } else {
                    0L
                }
            }.getOrDefault(0L)
        }

    fun updateCachedEnabled(moduleId: String, enabled: Boolean) {
        mutableInstalledModules.update { current ->
            current.copy(
                modules = current.modules.map { module ->
                    if (module.dirId == moduleId) module.copy(enabled = enabled) else module
                }
            )
        }
    }

    suspend fun setModuleEnabled(moduleId: String, enabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { check(ksuCliRepository.toggleModule(moduleId, enabled)) }
                .onSuccess { updateCachedEnabled(moduleId, enabled) }
        }

    suspend fun setModuleRemoved(moduleId: String, removed: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(
                    if (removed) ksuCliRepository.uninstallModule(moduleId)
                    else ksuCliRepository.undoUninstallModule(moduleId)
                )
            }
        }

    suspend fun checkUpdate(
        url: String,
        currentVersionCode: Int,
    ): Result<ModuleUpdateMetadata?> {
        if (url.isBlank()) return Result.success(null)
        return networkRequestRepository.fetch(url).mapCatching { body ->
            val json = JSONObject(body)
            val onlineVersionCode = json.optInt("versionCode")
            val zipUrl = json.optString("zipUrl")
            if (onlineVersionCode <= currentVersionCode || zipUrl.isBlank()) {
                null
            } else {
                ModuleUpdateMetadata(
                    zipUrl = zipUrl,
                    version = json.optString("version").replace(
                        Regex("[^a-zA-Z0-9.\\-_]"),
                        "_",
                    ),
                    changelog = json.optString("changelog"),
                )
            }
        }
    }

    private fun parseModules(raw: String): List<InstalledModule> {
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            val id = value.getString("id")
            InstalledModule(
                id = id,
                name = value.optString("name"),
                author = value.optString("author", "Unknown"),
                version = value.optString("version", "Unknown"),
                versionCode = value.getIntCompat("versionCode"),
                description = value.optString("description"),
                enabled = value.getBooleanCompat("enabled"),
                update = value.getBooleanCompat("update"),
                remove = value.getBooleanCompat("remove"),
                updateJson = value.optString("updateJson"),
                hasWebUi = value.getBooleanCompat("web"),
                hasActionScript = value.getBooleanCompat("action"),
                metamodule = value.getBooleanCompat("metamodule"),
                actionIconPath = value.optString("actionIcon").takeIf(String::isNotBlank),
                webUiIconPath = value.optString("webuiIcon").takeIf(String::isNotBlank),
                dirId = value.optString("dir_id", id),
                moduleUpdate = null,
            )
        }
    }

    private fun getMetaModuleStatus(): MetaModuleStatus {
        val directory = "/data/adb/metamodule"
        return when {
            !SuFile.open("$directory/module.prop").exists() -> MetaModuleStatus.MISSING
            SuFile.open("$directory/remove").exists() -> MetaModuleStatus.REMOVED
            SuFile.open("$directory/disable").exists() -> MetaModuleStatus.DISABLED
            else -> MetaModuleStatus.ACTIVE
        }
    }
}

private fun JSONObject.getBooleanCompat(key: String, default: Boolean = false): Boolean =
    if (!has(key)) {
        default
    } else {
        when (val value = opt(key)) {
            null -> default
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            is Number -> value.toInt() != 0
            else -> default
        }
    }

private fun JSONObject.getIntCompat(key: String, default: Int = 0): Int =
    if (!has(key)) {
        default
    } else {
        when (val value = opt(key)) {
            null -> default
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }
    }
