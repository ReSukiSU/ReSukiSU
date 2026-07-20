package com.resukisu.resukisu.ui.util.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.update.ManagerUpdateRepository
import com.resukisu.resukisu.data.update.ZipRangeArchive
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val ACTION_DOWNLOAD = "com.resukisu.resukisu.action.DOWNLOAD"
        const val ACTION_DOWNLOAD_MANAGER_APK = "com.resukisu.resukisu.action.DOWNLOAD_MANAGER_APK"
        const val ACTION_CANCEL = "com.resukisu.resukisu.action.CANCEL_DOWNLOAD"
        const val ACTION_DISMISS_DOWNLOAD = "com.resukisu.resukisu.action.DISMISS_DOWNLOAD"
        const val ACTION_INSTALL_MODULE = "com.resukisu.resukisu.action.INSTALL_MODULE"
        const val EXTRA_URL = "url"
        const val EXTRA_FILE_NAME = "fileName"
        const val EXTRA_DOWNLOAD_ID = "downloadId"
        const val EXTRA_MODULE_URI = "moduleUri"
        const val EXTRA_FILE_PATH = "filePath"
        const val EXTRA_MANAGER_SOURCE = "managerSource"
        const val EXTRA_MANAGER_ABI = "managerAbi"
        const val EXTRA_MANAGER_EXPECTED_VERSION_CODE = "managerExpectedVersionCode"

        const val SOURCE_DIRECT_APK = "direct_apk"
        const val SOURCE_NIGHTLY_ARTIFACT = "nightly_artifact"

        private const val COMPLETION_NOTIFICATION_ID_BASE = 100000
    }

    private val activeJobs = ConcurrentHashMap<Int, Job>()
    private val lastNotifiedProgress = ConcurrentHashMap<Int, Int>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: return START_NOT_STICKY
                val downloadId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == -1) return START_NOT_STICKY

                startForegroundDownload(downloadId, fileName)
                startDownload(downloadId, url, fileName)
            }

            ACTION_DOWNLOAD_MANAGER_APK -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: return START_NOT_STICKY
                val source = intent.getStringExtra(EXTRA_MANAGER_SOURCE) ?: return START_NOT_STICKY
                val preferredAbi = intent.getStringExtra(EXTRA_MANAGER_ABI)
                val expectedVersionCode = intent.getIntExtra(
                    EXTRA_MANAGER_EXPECTED_VERSION_CODE,
                    -1,
                )
                val downloadId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == -1) return START_NOT_STICKY
                if (source == SOURCE_NIGHTLY_ARTIFACT &&
                    (preferredAbi.isNullOrBlank() || expectedVersionCode <= 0)
                ) {
                    return START_NOT_STICKY
                }

                startForegroundDownload(downloadId, fileName)
                startManagerApkDownload(
                    downloadId,
                    url,
                    fileName,
                    source,
                    preferredAbi,
                    expectedVersionCode,
                )
            }

            ACTION_CANCEL -> {
                val downloadId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
                if (downloadId != -1) {
                    activeJobs[downloadId]?.cancel()
                    activeJobs.remove(downloadId)
                    lastNotifiedProgress.remove(downloadId)
                    notificationManager.cancel(downloadId)
                    DownloadManager.markFailed(downloadId, "Cancelled")
                    stopForegroundIfIdle()
                }
            }

            ACTION_DISMISS_DOWNLOAD -> {
                val downloadId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                if (downloadId != -1) {
                    notificationManager.cancel(COMPLETION_NOTIFICATION_ID_BASE + downloadId)
                }
                if (!filePath.isNullOrEmpty()) {
                    File(filePath).delete()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(id: Int, url: String, fileName: String) {
        val job = serviceScope.launch {
            val target = resolveAvailableTarget(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            try {
                downloadUrlToTarget(id, url, target)

                val uri = Uri.fromFile(target)
                DownloadManager.markCompleted(id, uri)

                notificationManager.cancel(id)
                notificationManager.notify(
                    COMPLETION_NOTIFICATION_ID_BASE + id,
                    buildModuleCompletionNotification(id, target.name, uri)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DownloadManager.markFailed(id, e.message ?: "Unknown error")

                notificationManager.cancel(id)
                notificationManager.notify(
                    COMPLETION_NOTIFICATION_ID_BASE + id,
                    buildFailureNotification(target.name)
                )
            } finally {
                activeJobs.remove(id)
                lastNotifiedProgress.remove(id)
                stopForegroundIfIdle()
            }
        }
        activeJobs[id] = job
    }

    private fun startManagerApkDownload(
        id: Int,
        url: String,
        fileName: String,
        source: String,
        preferredAbi: String?,
        expectedVersionCode: Int,
    ) {
        val job = serviceScope.launch {
            val target = resolveAvailableTarget(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            try {
                when (source) {
                    SOURCE_DIRECT_APK -> downloadUrlToTarget(id, url, target)
                    SOURCE_NIGHTLY_ARTIFACT -> {
                        val archive = ZipRangeArchive(ksuApp.okhttpClient)
                        val entry = ManagerUpdateRepository.findNightlyApkEntry(
                            entries = archive.listEntries(url),
                            expectedVersionCode = expectedVersionCode,
                            preferredAbi = preferredAbi ?: throw IOException(),
                        ) ?: throw IOException()
                        archive.extractEntry(url, entry, target) { progress ->
                            reportProgress(id, target.name, progress)
                        }
                    }

                    else -> throw IOException("Unknown manager APK source")
                }

                val uri = Uri.fromFile(target)
                DownloadManager.markCompleted(id, uri)

                notificationManager.cancel(id)
                notificationManager.notify(
                    COMPLETION_NOTIFICATION_ID_BASE + id,
                    buildApkCompletionNotification(id, target)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DownloadManager.markFailed(id, e.message ?: "Unknown error")

                notificationManager.cancel(id)
                notificationManager.notify(
                    COMPLETION_NOTIFICATION_ID_BASE + id,
                    buildFailureNotification(target.name)
                )
            } finally {
                activeJobs.remove(id)
                lastNotifiedProgress.remove(id)
                stopForegroundIfIdle()
            }
        }
        activeJobs[id] = job
    }

    private fun startForegroundDownload(downloadId: Int, fileName: String) {
        val notification = buildProgressNotification(downloadId, fileName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                downloadId, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(downloadId, notification)
        }
    }

    private fun downloadUrlToTarget(id: Int, url: String, target: File) {
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            val total = body.contentLength()

            body.byteStream().use { source ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0L) {
                            reportProgress(
                                id,
                                target.name,
                                ((copied * 100L) / total).toInt().coerceIn(0, 100)
                            )
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    private fun reportProgress(id: Int, fileName: String, progress: Int) {
        DownloadManager.updateProgress(id, progress)
        val previous = lastNotifiedProgress[id] ?: -1
        if (progress - previous >= 2 || progress == 100) {
            notificationManager.notify(id, buildProgressNotification(id, fileName, progress))
            lastNotifiedProgress[id] = progress
        }
    }

    private fun resolveAvailableTarget(
        directory: File,
        fileName: String
    ): File {
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""

        var index = 0
        while (true) {
            val candidateName = if (index == 0) {
                fileName
            } else {
                "$baseName ($index)$extension"
            }
            val candidate = File(directory, candidateName)
            if (!candidate.exists()) {
                return candidate
            }
            index++
        }
    }

    private fun buildProgressNotification(
        id: Int,
        fileName: String,
        progress: Int
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.download_progress_title, fileName))
        .setContentText("$progress%")
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setProgress(100, progress, progress == 0)
        .setOngoing(true)
        .setSilent(true)
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.download_cancel),
            createCancelPendingIntent(id)
        )
        .build()

    private fun buildModuleCompletionNotification(
        id: Int,
        fileName: String,
        uri: Uri
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.download_complete_title))
            .setContentText(getString(R.string.download_complete_content, fileName))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)

        // Add "Install" action button
        val installIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_INSTALL_MODULE
            putExtra(EXTRA_MODULE_URI, uri.toString())
            putExtra(EXTRA_DOWNLOAD_ID, id)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val installPendingIntent = PendingIntent.getActivity(
            this,
            id,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.ic_menu_save,
            getString(R.string.download_install),
            installPendingIntent
        )
        builder.setContentIntent(installPendingIntent)

        // Add "Cancel" action button
        val dismissIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_DISMISS_DOWNLOAD
            putExtra(EXTRA_DOWNLOAD_ID, id)
            putExtra(EXTRA_FILE_PATH, uri.path)
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            COMPLETION_NOTIFICATION_ID_BASE + id,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.download_cancel),
            dismissPendingIntent
        )

        return builder.build()
    }

    private fun buildApkCompletionNotification(
        id: Int,
        target: File,
    ): android.app.Notification {
        val installUri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            target
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(installUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val installPendingIntent = PendingIntent.getActivity(
            this,
            COMPLETION_NOTIFICATION_ID_BASE + id,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_DISMISS_DOWNLOAD
            putExtra(EXTRA_DOWNLOAD_ID, id)
            putExtra(EXTRA_FILE_PATH, target.absolutePath)
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            (COMPLETION_NOTIFICATION_ID_BASE * 2) + id,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.download_complete_title))
            .setContentText(getString(R.string.download_complete_content, target.name))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(installPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_save,
                getString(R.string.download_install),
                installPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.download_cancel),
                dismissPendingIntent
            )
            .build()
    }

    private fun buildFailureNotification(fileName: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.download_failed_title))
            .setContentText(getString(R.string.download_failed_content, fileName))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

    private fun createCancelPendingIntent(downloadId: Int): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        return PendingIntent.getService(
            this,
            downloadId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun stopForegroundIfIdle() {
        if (activeJobs.isEmpty() || activeJobs.values.none { it.isActive }) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
