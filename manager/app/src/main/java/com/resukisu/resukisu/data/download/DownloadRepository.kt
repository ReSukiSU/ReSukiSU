package com.resukisu.resukisu.data.download

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.resukisu.resukisu.domain.model.DownloadState
import com.resukisu.resukisu.domain.model.DownloadStatus
import com.resukisu.resukisu.domain.model.ManagerApkSource
import com.resukisu.resukisu.domain.model.ManagerUpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

class DownloadRepository(
    private val application: Application,
) {

    private val idCounter = AtomicInteger(0)
    private val enqueueLock = Any()
    private val _downloads = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<Int, DownloadState>> = _downloads.asStateFlow()

    fun enqueue(url: String, fileName: String): Int {
        synchronized(enqueueLock) {
            val existing = _downloads.value.values.find {
                it.url == url && (it.status == DownloadStatus.PENDING || it.status == DownloadStatus.DOWNLOADING)
            }
            if (existing != null) return existing.id

            val id = idCounter.incrementAndGet()
            _downloads.update {
                it + (id to DownloadState(
                    id = id,
                    fileName = fileName,
                    url = url
                ))
            }
            val intent = Intent(application, DownloadService::class.java).apply {
                action = DownloadService.ACTION_DOWNLOAD
                putExtra(DownloadService.EXTRA_DOWNLOAD_ID, id)
                putExtra(DownloadService.EXTRA_URL, url)
                putExtra(DownloadService.EXTRA_FILE_NAME, fileName)
            }
            ContextCompat.startForegroundService(application, intent)
            return id
        }
    }

    fun enqueueManagerUpdate(update: ManagerUpdateInfo): Int {
        synchronized(enqueueLock) {
            val url = update.source.url
            val existing = _downloads.value.values.find {
                it.url == url && (it.status == DownloadStatus.PENDING || it.status == DownloadStatus.DOWNLOADING)
            }
            if (existing != null) return existing.id

            val id = idCounter.incrementAndGet()
            _downloads.update {
                it + (id to DownloadState(id = id, fileName = update.fileName, url = url))
            }
            val intent = Intent(application, DownloadService::class.java).apply {
                action = DownloadService.ACTION_DOWNLOAD_MANAGER_APK
                putExtra(DownloadService.EXTRA_DOWNLOAD_ID, id)
                putExtra(DownloadService.EXTRA_URL, url)
                putExtra(DownloadService.EXTRA_FILE_NAME, update.fileName)
                when (val source = update.source) {
                    is ManagerApkSource.DirectApk -> {
                        putExtra(
                            DownloadService.EXTRA_MANAGER_SOURCE,
                            DownloadService.SOURCE_DIRECT_APK
                        )
                    }

                    is ManagerApkSource.NightlyArtifact -> {
                        putExtra(
                            DownloadService.EXTRA_MANAGER_SOURCE,
                            DownloadService.SOURCE_NIGHTLY_ARTIFACT
                        )
                        putExtra(DownloadService.EXTRA_MANAGER_ABI, source.preferredAbi)
                        putExtra(
                            DownloadService.EXTRA_MANAGER_EXPECTED_VERSION_CODE,
                            source.expectedVersionCode,
                        )
                    }
                }
            }
            ContextCompat.startForegroundService(application, intent)
            return id
        }
    }

    fun updateProgress(id: Int, progress: Int) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            map + (id to state.copy(progress = progress, status = DownloadStatus.DOWNLOADING))
        }
    }

    fun markCompleted(id: Int, uri: String) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            map + (id to state.copy(
                status = DownloadStatus.COMPLETED,
                progress = 100,
                resultUri = uri
            ))
        }
    }

    fun markFailed(id: Int, error: String) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            map + (id to state.copy(status = DownloadStatus.FAILED, error = error))
        }
    }
}
