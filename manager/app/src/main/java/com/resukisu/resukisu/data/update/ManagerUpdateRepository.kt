package com.resukisu.resukisu.data.update

import android.os.Build
import android.util.Log
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.ksuApp
import okhttp3.CacheControl
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ManagerUpdateChannel {
    STABLE,
    BETA,
}

sealed interface ManagerApkSource {
    val url: String

    data class DirectApk(
        override val url: String,
    ) : ManagerApkSource

    data class NightlyArtifact(
        override val url: String,
        val preferredAbi: String,
        val expectedVersionCode: Int,
    ) : ManagerApkSource
}

data class ManagerUpdateInfo(
    val channel: ManagerUpdateChannel,
    val versionCode: Int,
    val versionName: String,
    val abi: String,
    val fileName: String,
    val source: ManagerApkSource,
    val changelog: String = "",
)

object ManagerUpdateRepository {

    private const val TAG = "ManagerUpdateRepository"
    private const val REPOSITORY = "ReSukiSU/ReSukiSU"
    private const val WORKFLOW_FILE = "build-manager.yml"
    private const val BRANCH = "main"
    private const val RELEASE_ARTIFACT = "Manager-release"

    // sync with build.gradle.kts
    private const val CI_MANAGER_VERSION_CODE_OFFSET = 30000 + 700
    private const val SHORT_SHA_LENGTH = 7
    private const val UNIVERSAL_ABI = "universal"

    private val updateClient by lazy {
        ksuApp.okhttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val managerApkPattern = Regex(
        "^ReSukiSU_(.+)_(\\d+)-(arm64-v8a|armeabi-v7a|x86_64|universal)-release\\.apk$"
    )
    private val commitCountLinkPattern = Regex("""[?&]page=(\d+)>; rel="last"""")

    fun checkStableUpdate(
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
    ): ManagerUpdateInfo? {
        val release = requestJson("https://api.github.com/repos/$REPOSITORY/releases/latest")
            ?: return null
        val changelog = release.optString("body")
        val assets = release.optJSONArray("assets") ?: return null
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

        return selectCandidate(candidates, supportedAbis, currentVersionCode)
            ?.toUpdateInfo(ManagerUpdateChannel.STABLE, changelog)
    }

    fun checkBetaUpdate(
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
    ): ManagerUpdateInfo? {
        val workflowRuns = requestJson(
            "https://api.github.com/repos/$REPOSITORY/actions/workflows/$WORKFLOW_FILE/runs" +
                    "?branch=$BRANCH&status=success&per_page=1"
        )?.optJSONArray("workflow_runs") ?: return null
        val run = workflowRuns.optJSONObject(0) ?: return null
        val runId = run.optLong("id", -1L)
        val headSha = run.optString("head_sha")
        if (runId <= 0L || headSha.isBlank()) return null

        val commitCount = requestCommitCount(headSha) ?: return null
        val versionCode = CI_MANAGER_VERSION_CODE_OFFSET + commitCount
        if (versionCode <= currentVersionCode) return null

        val preferredAbi = supportedAbis.firstOrNull() ?: UNIVERSAL_ABI
        return ManagerUpdateInfo(
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

    private fun requestJson(url: String): JSONObject? {
        return updateClient.newCall(
            newGithubRequest(url).build()
        ).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub update request failed with HTTP ${response.code}")
                return null
            }
            val body = response.body?.string() ?: return null
            JSONObject(body)
        }
    }

    private fun requestCommitCount(commitSha: String): Int? {
        val url = "https://api.github.com/repos/$REPOSITORY/commits?sha=$commitSha&per_page=1"
        return updateClient.newCall(newGithubRequest(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub commit history request failed with HTTP ${response.code}")
                return null
            }
            parseCommitCount(response.header("Link")) ?: 1
        }
    }

    private fun newGithubRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .cacheControl(CacheControl.FORCE_NETWORK)

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
        currentVersionCode: Int,
    ): ManagerApkCandidate? {
        val latestVersionCode = candidates.maxOfOrNull { it.versionCode } ?: return null
        if (latestVersionCode <= currentVersionCode) return null

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
