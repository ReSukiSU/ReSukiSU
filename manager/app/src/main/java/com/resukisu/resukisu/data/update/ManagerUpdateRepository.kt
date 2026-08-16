package com.resukisu.resukisu.data.update

import android.os.Build
import android.util.Log
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.data.network.NetworkRequestRepository
import com.resukisu.resukisu.domain.model.ManagerApkSource
import com.resukisu.resukisu.domain.model.ManagerUpdateChannel
import com.resukisu.resukisu.domain.model.ManagerUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ManagerUpdateRepository(
    private val networkRequestRepository: NetworkRequestRepository,
) {
    private companion object {
        const val UPDATE_CALL_TIMEOUT_SECONDS = 8L
        const val TAG = "ManagerUpdateRepository"
        const val REPOSITORY = "ReSukiSU/ReSukiSU"
        const val WORKFLOW_FILE = "build-manager.yml"
        const val BRANCH = "main"
        const val RELEASE_ARTIFACT = "Manager-release"
        val GITHUB_HEADERS = mapOf("Accept" to "application/vnd.github+json")

        // sync with build.gradle.kts
        const val CI_MANAGER_VERSION_CODE_OFFSET = 30000 + 700
        const val SHORT_SHA_LENGTH = 7
        const val UNIVERSAL_ABI = "universal"
    }

    private val managerApkPattern = Regex(
        "^ReSukiSU_(.+)_(\\d+)-(arm64-v8a|armeabi-v7a|x86_64|universal)-release\\.apk$"
    )
    private val commitCountLinkPattern = Regex("""[?&]page=(\d+)>; rel="last"""")

    suspend fun checkStableUpdate(): ManagerUpdateInfo? = withContext(Dispatchers.IO) {
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        val release = requestJson("https://api.github.com/repos/$REPOSITORY/releases/latest")
            ?: return@withContext null
        val changelog = release.optString("body")
        val assets = release.optJSONArray("assets") ?: return@withContext null
        val candidates = mutableListOf<ManagerApkCandidate>()

        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val parsed = parseApkName(asset.optString("name")) ?: continue
            val downloadUrl = asset.optString("browser_download_url")
            if (downloadUrl.isBlank()) continue

            candidates += ManagerApkCandidate(
                versionName = parsed.versionName,
                versionCode = parsed.versionCode,
                abi = parsed.abi,
                fileName = parsed.fileName,
                source = ManagerApkSource.DirectApk(downloadUrl),
            )
        }

        return@withContext selectCandidate(candidates, supportedAbis)
            ?.toUpdateInfo(ManagerUpdateChannel.STABLE, changelog)
    }

    suspend fun checkBetaUpdate(): ManagerUpdateInfo? = withContext(Dispatchers.IO) {
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        val currentVersionCode = BuildConfig.VERSION_CODE
        val workflowRuns = requestJson(
            "https://api.github.com/repos/$REPOSITORY/actions/workflows/$WORKFLOW_FILE/runs" +
                    "?branch=$BRANCH&status=success&per_page=1&event=push"
        )?.optJSONArray("workflow_runs") ?: return@withContext null
        val run = workflowRuns.optJSONObject(0) ?: return@withContext null
        val runId = run.optLong("id", -1L)
        val headSha = run.optString("head_sha")
        if (runId <= 0L || headSha.isBlank()) return@withContext null

        val commitCount = requestCommitCount(headSha) ?: return@withContext null
        val versionCode = CI_MANAGER_VERSION_CODE_OFFSET + commitCount
        if (versionCode <= currentVersionCode) return@withContext null

        val preferredAbi = supportedAbis.firstOrNull() ?: UNIVERSAL_ABI
        return@withContext ManagerUpdateInfo(
            channel = ManagerUpdateChannel.BETA,
            versionCode = versionCode,
            versionName = headSha.take(SHORT_SHA_LENGTH),
            abi = preferredAbi,
            fileName = "ReSukiSU_${headSha.take(SHORT_SHA_LENGTH)}_" +
                    "$versionCode-$preferredAbi-release.apk",
            source = ManagerApkSource.NightlyArtifact(
                url = "https://nightly.link/$REPOSITORY/actions/runs/$runId/$RELEASE_ARTIFACT.zip",
                preferredAbi = preferredAbi,
                expectedVersionCode = versionCode,
            ),
            changelog = run.optJSONObject("head_commit")?.optString("message").orEmpty(),
        )
    }

    fun findNightlyApkEntry(
        entries: List<ZipEntryMetadata>,
        expectedVersionCode: Int,
        preferredAbi: String,
    ): ZipEntryMetadata? {
        val candidates = entries.mapNotNull { entry ->
            val parsed = parseApkName(entry.name.substringAfterLast('/')) ?: return@mapNotNull null
            if (parsed.versionCode == expectedVersionCode) entry to parsed else null
        }

        candidates.firstOrNull { (_, parsed) -> parsed.abi == preferredAbi }
            ?.first
            ?.let { return it }
        return candidates.firstOrNull { (_, parsed) -> parsed.abi == UNIVERSAL_ABI }?.first
    }

    private suspend fun requestJson(url: String): JSONObject? = networkRequestRepository
        .fetch(
            url = url,
            headers = GITHUB_HEADERS,
            forceNetwork = true,
            callTimeoutSeconds = UPDATE_CALL_TIMEOUT_SECONDS,
        )
        .onFailure { Log.w(TAG, "GitHub update request failed", it) }
        .mapCatching { JSONObject(it) }
        .getOrNull()

    private suspend fun requestCommitCount(commitSha: String): Int? {
        val url = "https://api.github.com/repos/$REPOSITORY/commits?sha=$commitSha&per_page=1"
        val response = networkRequestRepository.request(
            url = url,
            headers = GITHUB_HEADERS,
            forceNetwork = true,
            callTimeoutSeconds = UPDATE_CALL_TIMEOUT_SECONDS,
        ).getOrElse {
            Log.w(TAG, "GitHub commit history request failed", it)
            return null
        }
        return parseCommitCount(response.header("Link")) ?: 1
    }

    internal fun parseCommitCount(linkHeader: String?): Int? =
        commitCountLinkPattern.find(linkHeader.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    private fun parseApkName(fileName: String): ParsedApkName? {
        val match = managerApkPattern.matchEntire(fileName) ?: return null
        val versionCode = match.groupValues[2].toIntOrNull() ?: return null
        return ParsedApkName(
            versionName = match.groupValues[1],
            versionCode = versionCode,
            abi = match.groupValues[3],
            fileName = fileName,
        )
    }

    private fun selectCandidate(
        candidates: List<ManagerApkCandidate>,
        supportedAbis: List<String>,
    ): ManagerApkCandidate? {
        val latestVersionCode = candidates.maxOfOrNull { it.versionCode } ?: return null
        if (latestVersionCode <= BuildConfig.VERSION_CODE) return null

        val latestCandidates = candidates.filter { it.versionCode == latestVersionCode }
        supportedAbis.forEach { abi ->
            latestCandidates.firstOrNull { it.abi == abi }?.let { return it }
        }
        return latestCandidates.firstOrNull { it.abi == "universal" }
    }

    private data class ParsedApkName(
        val versionName: String,
        val versionCode: Int,
        val abi: String,
        val fileName: String,
    )

    private data class ManagerApkCandidate(
        val versionName: String,
        val versionCode: Int,
        val abi: String,
        val fileName: String,
        val source: ManagerApkSource,
    ) {
        fun toUpdateInfo(
            channel: ManagerUpdateChannel,
            changelog: String = "",
        ) = ManagerUpdateInfo(
            channel = channel,
            versionCode = versionCode,
            versionName = versionName,
            abi = abi,
            fileName = fileName,
            source = source,
            changelog = changelog,
        )
    }
}
