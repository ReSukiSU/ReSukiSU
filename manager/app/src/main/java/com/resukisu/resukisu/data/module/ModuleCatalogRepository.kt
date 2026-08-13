package com.resukisu.resukisu.data.module

import com.resukisu.resukisu.data.network.NetworkRequestRepository
import com.resukisu.resukisu.data.network.NetworkStatusRepository
import com.resukisu.resukisu.domain.model.CatalogAuthor
import com.resukisu.resukisu.domain.model.CatalogModule
import com.resukisu.resukisu.domain.model.ModuleCatalogFailure
import com.resukisu.resukisu.domain.model.ModuleCatalogResult
import com.resukisu.resukisu.domain.model.ModuleRelease
import com.resukisu.resukisu.domain.model.ModuleReleaseAsset
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// For modules repo
class ModuleCatalogRepository(
    private val networkStatusRepository: NetworkStatusRepository,
    private val networkRequestRepository: NetworkRequestRepository,
) {
    private companion object {
        const val MODULES_URL = "https://modules.kernelsu.org/modules.json"
    }

    private val refreshMutex = Mutex()
    private val mutableModules = MutableStateFlow<List<CatalogModule>>(emptyList())
    private val mutableRefreshing = MutableStateFlow(false)
    private val mutableOffline = MutableStateFlow(false)

    val modules: StateFlow<List<CatalogModule>> = mutableModules.asStateFlow()
    val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()
    val offline: StateFlow<Boolean> = mutableOffline.asStateFlow()

    suspend fun refresh(): ModuleCatalogResult<List<CatalogModule>> = refreshMutex.withLock {
        if (!networkStatusRepository.isAvailable()) {
            mutableOffline.value = true
            return@withLock ModuleCatalogResult.Failure(ModuleCatalogFailure.Offline)
        }
        mutableOffline.value = false
        mutableRefreshing.value = true
        try {
            withContext(Dispatchers.IO) {
                runCatching { fetchModules() }
                    .fold(
                        onSuccess = {
                            mutableModules.value = it
                            ModuleCatalogResult.Success(it)
                        },
                        onFailure = {
                            ModuleCatalogResult.Failure(
                                ModuleCatalogFailure.Network(it.message.orEmpty())
                            )
                        },
                    )
            }
        } finally {
            mutableRefreshing.value = false
        }
    }

    suspend fun get(moduleId: String): ModuleCatalogResult<CatalogModule> {
        mutableModules.value.firstOrNull { it.moduleId == moduleId }?.let {
            return ModuleCatalogResult.Success(it)
        }
        return when (val refreshed = refresh()) {
            is ModuleCatalogResult.Failure -> refreshed
            is ModuleCatalogResult.Success -> refreshed.value.firstOrNull { it.moduleId == moduleId }
                ?.let { ModuleCatalogResult.Success(it) }
                ?: ModuleCatalogResult.Failure(ModuleCatalogFailure.NotFound)
        }
    }

    private suspend fun fetchModules(): List<CatalogModule> {
        val body = networkRequestRepository.fetch(MODULES_URL).getOrThrow()
        val json = JSONArray(body)
        return coroutineScope {
            (0 until json.length()).map { index ->
                async(Dispatchers.IO) { json.optJSONObject(index)?.let { parseModule(it) } }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun parseModule(item: JSONObject): CatalogModule? {
        val moduleId = item.optString("moduleId").takeIf(String::isNotEmpty) ?: return null
        val authorList = item.optJSONArray("authors")?.let { authors ->
            (0 until authors.length()).mapNotNull { index ->
                authors.optJSONObject(index)?.let { author ->
                    val name = author.optString("name").trim()
                    name.takeIf(String::isNotEmpty)?.let {
                        CatalogAuthor(it, stripTicks(author.optString("link")))
                    }
                }
            }
        }.orEmpty()
        val latestReleaseObject = item.optJSONObject("latestRelease")
        val latestRelease = latestReleaseObject?.optString(
            "name",
            latestReleaseObject.optString("version"),
        ).orEmpty()
        val detail = fetchModuleDetail(moduleId)
        val releases = detail?.releases.orEmpty()

        return CatalogModule(
            moduleId = moduleId,
            moduleName = item.optString("moduleName"),
            authors = authorList.takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { it.name }
                ?: item.optString("authors"),
            authorList = authorList,
            summary = item.optString("summary"),
            metamodule = item.optBoolean("metamodule"),
            stargazerCount = item.optInt("stargazerCount"),
            updatedAt = item.optString("updatedAt"),
            createdAt = item.optString("createdAt"),
            latestRelease = latestRelease,
            latestReleaseTime = latestReleaseObject?.optString("time").orEmpty(),
            latestVersionCode = latestReleaseObject?.opt("versionCode").toIntCompat(),
            latestAsset = releases.firstOrNull { it.name == latestRelease },
            installed = SuFile.open("/data/adb/modules/$moduleId/module.prop").exists(),
            readme = detail?.readme.orEmpty(),
            sourceUrl = detail?.sourceUrl.orEmpty(),
            releases = releases,
        )
    }

    private suspend fun fetchModuleDetail(moduleId: String): Detail? {
        return networkRequestRepository
            .fetch("https://modules.kernelsu.org/module/$moduleId.json")
            .getOrNull()
            ?.let { body ->
                val json = JSONObject(body)
                val releases = json.optJSONArray("releases")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.toRelease()
                    }
                }.orEmpty()
                Detail(
                    readme = json.optString("readmeHTML"),
                    sourceUrl = stripTicks(json.optString("sourceUrl")),
                    releases = releases,
                )
            }
    }

    private fun JSONObject.toRelease(): ModuleRelease {
        val releaseName = optString("name", optString("tagName", optString("version")))
        val assets = optJSONArray("releaseAssets")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { asset ->
                    val name = asset.optString("name")
                    val url = stripTicks(asset.optString("downloadUrl"))
                    if (name.isBlank() || url.isBlank()) null else ModuleReleaseAsset(
                        name = name,
                        downloadUrl = url,
                        size = asset.optLong("size"),
                        downloadCount = asset.opt("downloadCount").toIntCompat(),
                    )
                }
            }
        }.orEmpty()
        return ModuleRelease(
            name = releaseName,
            tagName = optString("tagName", releaseName),
            publishedAt = optString("publishedAt"),
            descriptionHTML = optString("descriptionHTML"),
            assets = assets,
        )
    }

    private fun stripTicks(value: String): String = value.trim().let {
        if (it.startsWith('`') && it.endsWith('`') && it.length >= 2) {
            it.substring(1, it.length - 1)
        } else {
            it
        }
    }

    private fun Any?.toIntCompat(): Int = when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: 0
        else -> 0
    }

    private data class Detail(
        val readme: String,
        val sourceUrl: String,
        val releases: List<ModuleRelease>,
    )
}
