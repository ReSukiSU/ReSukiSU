package com.resukisu.resukisu.ui.screen.kernelFlash.state

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.util.flashAnyKernel
import com.resukisu.resukisu.ui.util.install
import com.resukisu.resukisu.ui.util.rootAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */
data class FlashState(
    val isFlashing: Boolean = false,
    val isCompleted: Boolean = false,
    val progress: Float = 0f,
    val currentStep: String = "",
    val logs: List<String> = emptyList(),
    val error: String = ""
)

class HorizonKernelState {
    private val _state = MutableStateFlow(FlashState())
    private val fullLogs = ConcurrentLinkedQueue<String>()
    val state: StateFlow<FlashState> = _state.asStateFlow()

    fun updateProgress(progress: Float) {
        _state.update { it.copy(progress = progress) }
    }

    fun updateStep(step: String) {
        _state.update { it.copy(currentStep = step) }
    }

    fun addLog(log: String) {
        fullLogs.add(log)
        _state.update {
            it.copy(logs = it.logs + log)
        }
    }

    fun addConsoleLog(log: String) {
        fullLogs.add(log)
    }

    fun getFullLog(): String = fullLogs.joinToString("\n")

    fun setError(error: String) {
        _state.update { it.copy(isFlashing = false, error = error) }
    }

    fun startFlashing() {
        fullLogs.clear()
        _state.update {
            it.copy(
                isFlashing = true,
                isCompleted = false,
                progress = 0f,
                currentStep = "",
                logs = emptyList(),
                error = ""
            )
        }
    }

    fun completeFlashing() {
        _state.update {
            it.copy(
                isFlashing = false,
                isCompleted = true,
                progress = 1f
            )
        }
    }

    fun reset() {
        fullLogs.clear()
        _state.value = FlashState()
    }
}

class HorizonKernelWorker(
    private val context: Context,
    private val state: HorizonKernelState,
    private val slot: String? = null
) : Thread() {
    var uri: Uri? = null

    private var onFlashComplete: (() -> Unit)? = null

    fun setOnFlashCompleteListener(listener: () -> Unit) {
        onFlashComplete = listener
    }

    override fun run() {
        state.startFlashing()
        state.updateStep(context.getString(R.string.horizon_preparing))

        val zipFile = File(context.cacheDir, "anykernel3.zip")
        try {
            if (!rootAvailable()) {
                state.setError(context.getString(R.string.root_required))
                return
            }

            state.updateStep(context.getString(R.string.horizon_copying_files))
            state.updateProgress(0.2f)
            copyToCache(zipFile)

            state.updateStep(context.getString(R.string.horizon_flashing))
            state.updateProgress(0.7f)
            val succeeded = flashAnyKernel(
                zipFile = zipFile,
                slot = slot,
                onStdout = ::handleOutput,
                onStderr = ::handleConsoleOutput
            )
            if (!succeeded) {
                state.setError(context.getString(R.string.flash_failed_message))
                return
            }

            runCatching { install() }.onFailure { error ->
                Log.w(TAG, "Failed to refresh ksud after a successful kernel flash", error)
            }
            state.updateStep(context.getString(R.string.horizon_flash_complete_status))
            state.completeFlashing()

            Handler(Looper.getMainLooper()).post {
                onFlashComplete?.invoke()
            }
        } catch (error: Exception) {
            state.setError(
                error.message ?: context.getString(R.string.horizon_unknown_error)
            )
        } finally {
            if (zipFile.exists()) {
                zipFile.delete()
            }
        }
    }

    private fun copyToCache(zipFile: File) {
        zipFile.delete()
        val source = uri
            ?: throw IOException(context.getString(R.string.horizon_copy_failed))
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException(context.getString(R.string.horizon_copy_failed))
        input.use {
            zipFile.outputStream().use { output ->
                it.copyTo(output)
            }
        }
        if (!zipFile.isFile) {
            throw IOException(context.getString(R.string.horizon_copy_failed))
        }
    }

    private fun handleOutput(line: String) {
        Log.i(TAG, line)
        state.addLog(line)

        when {
            line.contains("extracting", ignoreCase = true) -> {
                state.updateProgress(0.75f)
            }

            line.contains("installing", ignoreCase = true) -> {
                state.updateProgress(0.85f)
            }

            line.contains("complete", ignoreCase = true) -> {
                state.updateProgress(0.95f)
            }
        }
    }

    private fun handleConsoleOutput(line: String) {
        Log.i(TAG, line)
        state.addConsoleLog(line)
    }

    private companion object {
        const val TAG = "HorizonKernelWorker"
    }
}
