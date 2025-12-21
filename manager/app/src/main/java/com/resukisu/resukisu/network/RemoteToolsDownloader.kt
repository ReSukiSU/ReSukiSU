package com.resukisu.resukisu.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit

class RemoteToolsDownloader(
    private val context: Context,
    private val workDir: String
) {
    companion object {
        private const val TAG = "RemoteToolsDownloader"

        // 远程下载URL配置
        private const val KPTOOLS_REMOTE_URL = "https://raw.githubusercontent.com/ShirkNeko/SukiSU_patch/refs/heads/main/kpm/kptools"
        private const val KPIMG_REMOTE_URL = "https://raw.githubusercontent.com/ShirkNeko/SukiSU_patch/refs/heads/main/kpm/kpimg"

        // 网络超时配置（毫秒）
        private const val CONNECTION_TIMEOUT = 15000  // 15秒连接超时
        private const val READ_TIMEOUT = 30000        // 30秒读取超时

        // 最大重试次数
        private const val MAX_RETRY_COUNT = 3

        // 文件校验相关
        private const val MIN_FILE_SIZE = 1024
    }

    interface DownloadProgressListener {
        fun onProgress(fileName: String, progress: Int, total: Int)
        fun onLog(message: String)
        fun onError(fileName: String, error: String)
        fun onSuccess(fileName: String, isRemote: Boolean)
    }

    data class DownloadResult(
        val success: Boolean,
        val isRemoteSource: Boolean,
        val errorMessage: String? = null
    )


    suspend fun downloadToolsAsync(listener: DownloadProgressListener?): Map<String, DownloadResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, DownloadResult>()

        listener?.onLog("Starting to prepare KPM tool files...")

        try {
            // 确保工作目录存在
            File(workDir).mkdirs()

            // 并行下载两个工具文件
            val kptoolsDeferred = async { downloadSingleTool("kptools", KPTOOLS_REMOTE_URL, listener) }
            val kpimgDeferred = async { downloadSingleTool("kpimg", KPIMG_REMOTE_URL, listener) }

            // 等待所有下载完成
            results["kptools"] = kptoolsDeferred.await()
            results["kpimg"] = kpimgDeferred.await()

            // 检查kptools执行权限
            val kptoolsFile = File(workDir, "kptools")
            if (kptoolsFile.exists()) {
                setExecutablePermission(kptoolsFile.absolutePath)
                listener?.onLog("Set kptools execution permission")
            }

            val successCount = results.values.count { it.success }
            val remoteCount = results.values.count { it.success && it.isRemoteSource }

            listener?.onLog("KPM tools preparation completed: Success $successCount/2, Remote downloaded $remoteCount")

        } catch (e: Exception) {
            Log.e(TAG, "Exception occurred while downloading tools", e)
            listener?.onLog("Exception occurred during tool download: ${e.message}")

            if (!results.containsKey("kptools")) {
                results["kptools"] = downloadSingleTool("kptools", null, listener)
            }
            if (!results.containsKey("kpimg")) {
                results["kpimg"] = downloadSingleTool("kpimg", null, listener)
            }
        }

        results.toMap()
    }

    private suspend fun downloadSingleTool(
        fileName: String,
        remoteUrl: String?,
        listener: DownloadProgressListener?
    ): DownloadResult = withContext(Dispatchers.IO) {

        val targetFile = File(workDir, fileName)
        
        if (remoteUrl == null) {
            return@withContext useLocalVersion(fileName, targetFile, listener)
        }

        // 尝试从远程下载
        listener?.onLog("Downloading $fileName from remote repository...")

        var lastError = ""

        // 重试机制
        repeat(MAX_RETRY_COUNT) { attempt ->
            try {
                val result = downloadFromRemote(fileName, remoteUrl, targetFile, listener)
                if (result.success) {
                    listener?.onSuccess(fileName, true)
                    return@withContext result
                }
                lastError = result.errorMessage ?: "Unknown error"

            } catch (e: Exception) {
                lastError = e.message ?: "Network exception"
                Log.w(TAG, "$fileName download attempt ${attempt + 1} failed", e)

                if (attempt < MAX_RETRY_COUNT - 1) {
                    listener?.onLog("$fileName download failed, retrying in ${(attempt + 1) * 2} seconds...")
                    delay(TimeUnit.SECONDS.toMillis((attempt + 1) * 2L))
                }
            }
        }

        // 所有重试都失败，回退到本地版本
        listener?.onError(fileName, "Remote download failed: $lastError")
        listener?.onLog("$fileName remote download failed, falling back to local version...")

        useLocalVersion(fileName, targetFile, listener)
    }

    private suspend fun downloadFromRemote(
        fileName: String,
        remoteUrl: String,
        targetFile: File,
        listener: DownloadProgressListener?
    ): DownloadResult = withContext(Dispatchers.IO) {

        var connection: HttpURLConnection? = null

        try {
            val url = URL(remoteUrl)
            connection = url.openConnection() as HttpURLConnection

            // 设置连接参数
            connection.apply {
                connectTimeout = CONNECTION_TIMEOUT
                readTimeout = READ_TIMEOUT
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SukiSU-KPM-Downloader/1.0")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "close")
            }

            // 建立连接
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext DownloadResult(
                    false,
                    isRemoteSource = false,
                    errorMessage = "HTTP error code: $responseCode"
                )
            }

            val fileLength = connection.contentLength
            Log.d(TAG, "$fileName remote file size: $fileLength bytes")

            // 创建临时文件
            val tempFile = File(targetFile.absolutePath + ".tmp")

            // 下载文件
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalBytes = 0
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // 检查协程是否被取消
                        ensureActive()

                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead

                        // 更新下载进度
                        if (fileLength > 0) {
                            listener?.onProgress(fileName, totalBytes, fileLength)
                        }
                    }

                    output.flush()
                }
            }

            // 验证下载的文件
            if (!validateDownloadedFile(tempFile, fileName)) {
                tempFile.delete()
                return@withContext DownloadResult(
                    success = false,
                    isRemoteSource = false,
                    errorMessage = "File verification failed"
                )
            }

            // 移动临时文件到目标位置
            if (targetFile.exists()) {
                targetFile.delete()
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                return@withContext DownloadResult(
                    false,
                    isRemoteSource = false,
                    errorMessage = "Failed to move file"
                )
            }

            Log.i(TAG, "$fileName remote download successful, file size: ${targetFile.length()} bytes")
            listener?.onLog("$fileName remote download successful")

            DownloadResult(true, isRemoteSource = true)

        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "$fileName download timeout", e)
            DownloadResult(false, isRemoteSource = false, errorMessage = "Connection timeout")
        } catch (e: IOException) {
            Log.w(TAG, "$fileName network IO exception", e)
            DownloadResult(false,
                isRemoteSource = false,
                errorMessage = "Network connection exception: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "$fileName exception occurred during download", e)
            DownloadResult(false,
                isRemoteSource = false,
                errorMessage = "Download exception: ${e.message}"
            )
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun useLocalVersion(
        fileName: String,
        targetFile: File,
        listener: DownloadProgressListener?
    ): DownloadResult = withContext(Dispatchers.IO) {

        try {
            com.resukisu.resukisu.utils.AssetsUtil.exportFiles(context, fileName, targetFile.absolutePath)

            if (!targetFile.exists()) {
                val errorMsg = "Local $fileName file extraction failed"
                listener?.onError(fileName, errorMsg)
                return@withContext DownloadResult(false,
                    isRemoteSource = false,
                    errorMessage = errorMsg
                )
            }

            if (!validateDownloadedFile(targetFile, fileName)) {
                val errorMsg = "Local $fileName file verification failed"
                listener?.onError(fileName, errorMsg)
                return@withContext DownloadResult(
                    success = false,
                    isRemoteSource = false,
                    errorMessage = errorMsg
                )
            }

            Log.i(TAG, "$fileName local version loaded successfully, file size: ${targetFile.length()} bytes")
            listener?.onLog("$fileName local version loaded successfully")
            listener?.onSuccess(fileName, false)

            DownloadResult(true, isRemoteSource = false)

        } catch (e: Exception) {
            Log.e(TAG, "$fileName local version loading failed", e)
            val errorMsg = "Local version loading failed: ${e.message}"
            listener?.onError(fileName, errorMsg)
            DownloadResult(success = false, isRemoteSource = false, errorMessage = errorMsg)
        }
    }

    private fun validateDownloadedFile(file: File, fileName: String): Boolean {
        if (!file.exists()) {
            Log.w(TAG, "$fileName file does not exist")
            return false
        }

        val fileSize = file.length()
        if (fileSize < MIN_FILE_SIZE) {
            Log.w(TAG, "$fileName file is too small: $fileSize bytes")
            return false
        }

        try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                val bytesRead = input.read(header)

                if (bytesRead < 4) {
                    Log.w(TAG, "$fileName file header read incomplete")
                    return false
                }

                val isELF = header[0] == 0x7F.toByte() &&
                        header[1] == 'E'.code.toByte() &&
                        header[2] == 'L'.code.toByte() &&
                        header[3] == 'F'.code.toByte()

                if (fileName == "kptools" && !isELF) {
                    Log.w(TAG, "kptools file format is invalid, not ELF format")
                    return false
                }

                Log.d(TAG, "$fileName file verification passed, size: $fileSize bytes, ELF: $isELF")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "$fileName file verification exception", e)
            return false
        }
    }

    private fun setExecutablePermission(filePath: String) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod a+rx $filePath"))
            process.waitFor()
            Log.d(TAG, "Set execution permission for $filePath")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set execution permission: $filePath", e)
            try {
                File(filePath).setExecutable(true, false)
            } catch (ex: Exception) {
                Log.w(TAG, "Java method to set permissions also failed", ex)
            }
        }
    }


    fun cleanup() {
        try {
            File(workDir).listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp")) {
                    file.delete()
                    Log.d(TAG, "Cleaned temporary file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean temporary files", e)
        }
    }
}
