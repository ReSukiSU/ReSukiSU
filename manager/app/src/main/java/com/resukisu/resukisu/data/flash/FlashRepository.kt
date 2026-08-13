package com.resukisu.resukisu.data.flash

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import com.resukisu.resukisu.data.file.ModuleFileRepository
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.domain.model.FlashOperation
import com.resukisu.resukisu.domain.model.FlashOperationUpdate
import com.resukisu.resukisu.domain.model.FlashProgress
import com.resukisu.resukisu.domain.model.InstallEnvironment
import com.resukisu.resukisu.domain.model.KernelFlashSession
import com.resukisu.resukisu.getKernelVersion
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FlashRepository(
    private val application: Application,
    private val applicationScope: CoroutineScope,
    private val moduleFileRepository: ModuleFileRepository,
    private val ksuCliRepository: KsuCliRepository,
) {
    private val workerState = HorizonKernelState()
    private val mutableSession = MutableStateFlow(KernelFlashSession())
    val kernelFlashSession: StateFlow<KernelFlashSession> = mutableSession.asStateFlow()
    private val mutableInstallEnvironment = MutableStateFlow<InstallEnvironment?>(null)
    val installEnvironment: StateFlow<InstallEnvironment?> =
        mutableInstallEnvironment.asStateFlow()
    private var observationJob: Job? = null
    private var worker: HorizonKernelWorker? = null
    private val installEnvironmentMutex = Mutex()

    fun startKernelFlash(uri: String, selectedSlot: String?) {
        val current = mutableSession.value
        if (current.requestUri == uri && current.selectedSlot == selectedSlot && worker != null) return
        workerState.reset()
        mutableSession.value = KernelFlashSession(
            requestUri = uri,
            selectedSlot = selectedSlot,
            progress = FlashProgress(),
        )
        observationJob?.cancel()
        observationJob = applicationScope.launch {
            workerState.state.collectLatest { progress ->
                mutableSession.update {
                    it.copy(progress = progress, fullLog = workerState.getFullLog())
                }
            }
        }
        worker = HorizonKernelWorker(
            context = application,
            state = workerState,
            ksuCliRepository = ksuCliRepository,
            slot = selectedSlot,
        ).also {
            it.uri = uri.toUri()
            it.start()
        }
    }

    suspend fun getInstallEnvironment(forceRefresh: Boolean = false): InstallEnvironment {
        if (!forceRefresh) mutableInstallEnvironment.value?.let { return it }
        return installEnvironmentMutex.withLock {
            if (!forceRefresh) mutableInstallEnvironment.value?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val abDevice = runCatching { ksuCliRepository.isAbDevice() }.getOrDefault(false)
                InstallEnvironment(
                    rootAvailable = runCatching { ksuCliRepository.rootAvailable() }.getOrDefault(
                        false
                    ),
                    isGki = runCatching { getKernelVersion().isGKI() }.getOrDefault(false),
                    isAbDevice = abDevice,
                    currentKmi = runCatching { ksuCliRepository.getCurrentKmi() }.getOrDefault(""),
                    defaultPartition = runCatching { ksuCliRepository.getDefaultPartition() }.getOrDefault(
                        "boot"
                    ),
                    availablePartitions = runCatching { ksuCliRepository.getAvailablePartitions() }.getOrDefault(
                        emptyList()
                    ),
                    activeSlotSuffix = runCatching { ksuCliRepository.getSlotSuffix(false) }.getOrDefault(
                        ""
                    ),
                    inactiveSlotSuffix = if (abDevice) {
                        runCatching { ksuCliRepository.getSlotSuffix(true) }.getOrDefault("")
                    } else {
                        ""
                    },
                    supportedKmis = runCatching { ksuCliRepository.getSupportedKmis() }.getOrDefault(
                        emptyList()
                    ),
                )
            }.also { mutableInstallEnvironment.value = it }
        }
    }

    fun execute(operation: FlashOperation): Flow<FlashOperationUpdate> = callbackFlow {
        val onStdout: (String) -> Unit = { trySend(FlashOperationUpdate.Output(it)) }
        val onStderr: (String) -> Unit = { trySend(FlashOperationUpdate.ErrorOutput(it)) }
        val onFinish: (Boolean, Int) -> Unit = { showReboot, code ->
            trySend(FlashOperationUpdate.Completed(showReboot, code))
        }

        try {
            withContext(Dispatchers.IO) {
                when (operation) {
                    is FlashOperation.Boot -> ksuCliRepository.installBoot(
                        application,
                        operation.bootUri?.let(Uri::parse),
                        operation.lkm,
                        operation.ota,
                        operation.partition,
                        onFinish,
                        onStdout,
                        onStderr,
                    )

                    is FlashOperation.Module -> ksuCliRepository.flashModule(
                        application,
                        operation.uri.toUri(),
                        onFinish,
                        onStdout,
                        onStderr,
                    )

                    FlashOperation.Restore -> ksuCliRepository.restoreBoot(
                        onFinish,
                        onStdout,
                        onStderr
                    )

                    FlashOperation.Uninstall -> ksuCliRepository.uninstallPermanently(
                        onFinish,
                        onStdout,
                        onStderr
                    )
                }
            }
        } catch (error: Throwable) {
            trySend(FlashOperationUpdate.ErrorOutput(error.message.orEmpty()))
            trySend(FlashOperationUpdate.Completed(showReboot = false, code = -1))
        } finally {
            close()
        }
        awaitClose()
    }

    suspend fun moduleNeedsMount(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val parsedUri = uri.toUri()
            val moduleId = moduleFileRepository.extractModuleId(parsedUri.toString())
                ?: return@runCatching false
            val oldSystem = SuFile.open("/data/adb/modules/$moduleId/system")
            val updatedSystem = SuFile.open("/data/adb/modules_update/$moduleId/system")
            oldSystem.isDirectory || updatedSystem.isDirectory
        }.getOrDefault(false)
    }
}
