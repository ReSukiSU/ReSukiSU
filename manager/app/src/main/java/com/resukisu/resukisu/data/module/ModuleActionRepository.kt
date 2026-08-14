package com.resukisu.resukisu.data.module

import android.os.Environment
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.domain.model.ModuleActionUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ModuleActionRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    fun execute(moduleId: String): Flow<ModuleActionUpdate> = callbackFlow {
        val task = launch(Dispatchers.IO) {
            val successful = runCatching {
                ksuCliRepository.runModuleAction(
                    moduleId = moduleId,
                    onStdout = { trySend(ModuleActionUpdate.Output(it)) },
                    onStderr = { trySend(ModuleActionUpdate.Output(it, isError = true)) },
                )
            }.getOrDefault(false)
            trySend(ModuleActionUpdate.Completed(successful))
            close()
        }
        awaitClose { task.cancel() }
    }

    suspend fun saveLog(content: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val date = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(Date())
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "KernelSU_module_action_log_${date}.log",
            )
            file.writeText(content)
            file.absolutePath
        }
    }
}
