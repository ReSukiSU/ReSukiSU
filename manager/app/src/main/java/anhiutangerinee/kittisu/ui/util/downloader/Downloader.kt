package anhiutangerinee.kittisu.ui.util.downloader

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.activity.PermissionRequestInterface
import anhiutangerinee.kittisu.ui.activity.util.isNetworkAvailable
import anhiutangerinee.kittisu.ui.util.module.LatestVersionInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * @author weishu
 * @date 2023/6/22.
 */
fun download(
    context: Context,
    permissionRequestInterface: PermissionRequestInterface,
    url: String,
    fileName: String,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    onProgress: (Int) -> Unit = {}
) {
    fun startDownloadFile(
        url: String,
        fileName: String,
        onDownloaded: (Uri) -> Unit,
        onDownloading: () -> Unit,
        onProgress: (Int) -> Unit,
    ) {
        onDownloading()

        val downloadId = DownloadManager.enqueue(
            context = ksuApp,
            url = url,
            fileName = fileName,
            onCompleted = onDownloaded,
        )

        CoroutineScope(Dispatchers.Main).launch {
            DownloadManager.downloads.collect { map ->
                val state = map[downloadId] ?: return@collect
                onProgress(state.progress)
                if (state.status == DownloadManager.Status.COMPLETED ||
                    state.status == DownloadManager.Status.FAILED
                ) {
                    cancel()
                }
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // sdk 32+, require post_notifications permission
        permissionRequestInterface.requestPermission(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            callback = { success ->
                if (!success) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.notification_permission_denied),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@requestPermission
                }

                startDownloadFile(
                    url = url,
                    fileName = fileName,
                    onDownloaded = onDownloaded,
                    onDownloading = onDownloading,
                    onProgress = onProgress
                )
            },
            requestDescription = context.getString(R.string.notification_permission_description)
        )
    } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) {
        // sdk 32, no need any permission
        startDownloadFile(
            url = url,
            fileName = fileName,
            onDownloaded = onDownloaded,
            onDownloading = onDownloading,
            onProgress = onProgress,
        )
    } else {
        // sdk 32-, require write external storage
        permissionRequestInterface.requestPermissions(
            permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            callback = { result ->
                val success = result.all { it.value }
                if (!success) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.storage_permission_denied),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@requestPermissions
                }
                startDownloadFile(url, fileName, onDownloaded, onDownloading, onProgress)
            },
            requestDescription = mapOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE to context.getString(R.string.storage_permission_description),
            )
        )
    }
}

fun checkNewVersion(): LatestVersionInfo {
    if (!isNetworkAvailable(ksuApp)) return LatestVersionInfo()
    val url = "https://api.github.com/repos/anotheranhiutangerine/KittiSU/releases/latest"
    // default null value if failed
    val defaultValue = LatestVersionInfo()
    runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute()
            .use { response ->
                if (!response.isSuccessful) {
                    return defaultValue
                }
                val body = response.body?.string() ?: throw IOException("Empty body")
                val json = JSONObject(body)
                val changelog = json.optString("body")

                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (!name.endsWith(".apk")) {
                        continue
                    }

                    val regex = Regex("v(.+?)_(\\d+)-")
                    val matchResult = regex.find(name) ?: continue
                    matchResult.groupValues[1]
                    val versionCode = matchResult.groupValues[2].toInt()
                    val downloadUrl = asset.getString("browser_download_url")

                    return LatestVersionInfo(
                        versionCode,
                        downloadUrl,
                        changelog
                    )
                }

            }
    }
    return defaultValue
}