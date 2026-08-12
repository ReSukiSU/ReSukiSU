package com.resukisu.resukisu.data.application

import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApplicationControlRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    suspend fun ensureManagerInstalled(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (Natives.isManager && !Natives.requireNewKernel()) ksuCliRepository.install()
        }
    }

    suspend fun reboot(reason: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { ksuCliRepository.reboot(reason) }
    }
}
