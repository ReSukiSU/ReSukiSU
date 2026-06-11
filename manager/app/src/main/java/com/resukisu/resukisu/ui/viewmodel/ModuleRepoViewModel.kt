package com.resukisu.resukisu.ui.viewmodel

import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.activity.util.isNetworkAvailable
import com.resukisu.resukisu.ui.util.HanziToPinyin
import com.resukisu.resukisu.ui.util.module.ReleaseInfo
import com.resukisu.resukisu.ui.util.module.fetchModuleDetail
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class ModuleRepoUiState(
    val modules: List<ModuleRepoViewModel.RepoModule> = emptyList(),
    val sortStargazerCountFirst: Boolean = false,
    val isRefreshing: Boolean = false,
    val search: String = "",
)

class ModuleRepoViewModel : ViewModel() {
    companion object {
        private const val TAG = "ModuleRepoViewModel"
        private const val MODULES_URL = "https://modules.kernelsu.org/modules.json"
    }

    private var modulesCache: List<RepoModule> = emptyList()
    private val _uiState = MutableStateFlow(ModuleRepoUiState())
    val uiState: StateFlow<ModuleRepoUiState> = _uiState.asStateFlow()

    @Immutable
    @Parcelize
    data class Author(
        val name: String,
        val link: String,
    ) : Parcelable

    @Immutable
    @Parcelize
    data class RepoModule(
        val moduleId: String,
        val moduleName: String,
        val authors: String,
        val authorList: List<Author>,
        val summary: String,
        val metamodule: Boolean,
        val stargazerCount: Int,
        val updatedAt: String,
        val createdAt: String,
        val latestRelease: String,
        val latestReleaseTime: String,
        val latestVersionCode: Int,
        val latestAsset: ReleaseInfo?,
        val installed: Boolean,
        val readme: String,
        val sourceUrl: String,
        val releases: List<ReleaseInfo>
    ) : Parcelable

    fun updateSearch(search: String) {
        _uiState.update { state ->
            state.copy(
                search = search,
                modules = buildModuleList(search, state.sortStargazerCountFirst),
            )
        }
    }

    fun setSortStargazerCountFirst(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                sortStargazerCountFirst = enabled,
                modules = buildModuleList(state.search, enabled),
            )
        }
    }

    fun refresh(
        onFailure: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val netAvailable = isNetworkAvailable(ksuApp)
            _uiState.update { it.copy(isRefreshing = true) }
            val parsed = withContext(Dispatchers.IO) {
                if (!netAvailable) null else fetchModulesInternal(onFailure)
            }
            if (parsed != null) {
                modulesCache = parsed
                _uiState.update { state ->
                    state.copy(
                        modules = buildModuleList(state.search, state.sortStargazerCountFirst),
                        isRefreshing = false,
                    )
                }
            } else {
                Toast.makeText(
                    ksuApp,
                    ksuApp.getString(R.string.network_offline),
                    Toast.LENGTH_SHORT
                ).show()
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun buildModuleList(
        search: String,
        sortStargazerCountFirst: Boolean
    ): List<RepoModule> {
        return modulesCache
            .filter { module ->
                module.moduleId.contains(search, true) ||
                        module.moduleName.contains(search, true) ||
                        HanziToPinyin.getInstance().toPinyinString(module.moduleName)
                            ?.contains(search, true) == true
            }
            .sortedWith(compareByDescending<RepoModule> { module ->
                SuFile.open("/data/adb/modules/${module.moduleId}/module.prop").exists()
            }.thenByDescending { module ->
                if (sortStargazerCountFirst) module.stargazerCount else 0
            })
    }

    private suspend fun fetchModulesInternal(
        onFailure: (() -> Unit)? = null
    ): List<RepoModule> {
        return runCatching {
            val request = Request.Builder().url(MODULES_URL).build()
            ksuApp.okhttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val json = JSONArray(body)
                coroutineScope {
                    val jobs = (0 until json.length()).map { idx ->
                        async {
                            val item = json.optJSONObject(idx)
                            if (item != null) parseRepoModule(item) else null
                        }
                    }

                    jobs.awaitAll().filterNotNull()
                }
            }
        }.onFailure {
            onFailure?.invoke()
        }.getOrElse {
            Log.e(TAG, "fetch modules failed", it)
            emptyList()
        }
    }

    private suspend fun parseRepoModule(item: JSONObject): RepoModule? {
        val moduleId = item.optString("moduleId", "")
        if (moduleId.isEmpty()) return null
        val moduleName = item.optString("moduleName", "")
        val authorsArray = item.optJSONArray("authors")
        val authorList = if (authorsArray != null) {
            (0 until authorsArray.length())
                .mapNotNull { idx ->
                    val authorObj = authorsArray.optJSONObject(idx) ?: return@mapNotNull null
                    val name = authorObj.optString("name", "").trim()
                    var link = authorObj.optString("link", "").trim()
                    if (link.startsWith("`") && link.endsWith("`") && link.length >= 2) {
                        link = link.substring(1, link.length - 1)
                    }
                    if (name.isEmpty()) null else Author(name = name, link = link)
                }
        } else {
            emptyList()
        }
        val authors = if (authorList.isNotEmpty()) {
            authorList.joinToString(", ") { it.name }
        } else {
            item.optString("authors", "")
        }
        val summary = item.optString("summary", "")
        val metamodule = item.optBoolean("metamodule", false)
        val stargazerCount = item.optInt("stargazerCount", 0)
        val updatedAt = item.optString("updatedAt", "")
        val createdAt = item.optString("createdAt", "")

        var latestRelease = ""
        var latestReleaseTime = ""
        var latestVersionCode = 0
        var latestAsset: ReleaseInfo? = null
        val releases: MutableList<ReleaseInfo> = ArrayList()
        val latestReleaseObject = item.optJSONObject("latestRelease")
        if (latestReleaseObject != null) {
            latestRelease = latestReleaseObject.optString(
                "name",
                latestReleaseObject.optString("version", "")
            )
            latestReleaseTime = latestReleaseObject.optString("time", "")
            latestVersionCode = when (val value = latestReleaseObject.opt("versionCode")) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: 0
                else -> 0
            }
        }
        val detail = withContext(Dispatchers.IO) {
            fetchModuleDetail(moduleId)
        }

        detail?.releases?.forEach {
            releases.add(it)
            if (it.name == latestRelease) {
                latestAsset = it
            }
        }

        return RepoModule(
            moduleId = moduleId,
            moduleName = moduleName,
            authors = authors,
            authorList = authorList,
            summary = summary,
            metamodule = metamodule,
            stargazerCount = stargazerCount,
            updatedAt = updatedAt,
            createdAt = createdAt,
            latestRelease = latestRelease,
            latestReleaseTime = latestReleaseTime,
            latestVersionCode = latestVersionCode,
            latestAsset = latestAsset,
            installed = SuFile.open("/data/adb/modules/${moduleId}/module.prop").exists(),
            readme = detail?.readme.orEmpty(),
            sourceUrl = detail?.sourceUrl.orEmpty(),
            releases = releases
        )
    }
}
