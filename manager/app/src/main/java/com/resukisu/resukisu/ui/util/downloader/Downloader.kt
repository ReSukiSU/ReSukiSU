package com.resukisu.resukisu.ui.util.downloader

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.update.ManagerUpdateInfo
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.activity.PermissionRequestInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    requestDownloadPermissions(context, permissionRequestInterface) {
        startDownloadFile(
            url = url,
            fileName = fileName,
            onDownloaded = onDownloaded,
            onDownloading = onDownloading,
            onProgress = onProgress
        )
    }
}

fun downloadManagerUpdate(
    context: Context,
    permissionRequestInterface: PermissionRequestInterface,
    update: ManagerUpdateInfo,
) {
    requestDownloadPermissions(context, permissionRequestInterface) {
        DownloadManager.enqueueManagerUpdate(context, update)
    }
}

private fun requestDownloadPermissions(
    context: Context,
    permissionRequestInterface: PermissionRequestInterface,
    onGranted: () -> Unit,
) {
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

                onGranted()
            },
            requestDescription = context.getString(R.string.notification_permission_description)
        )
    } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) {
        // sdk 32, no need any permission
        onGranted()
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
                onGranted()
            },
            requestDescription = mapOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE to context.getString(R.string.storage_permission_description),
            )
        )
    }
}
