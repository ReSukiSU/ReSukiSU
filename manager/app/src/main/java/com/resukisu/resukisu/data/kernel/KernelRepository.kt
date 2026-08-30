package com.resukisu.resukisu.data.kernel

import android.app.Application
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.Natives.KernelPatchImplementation
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.data.system.isSELinuxPermissive
import com.resukisu.resukisu.domain.model.KernelFeatureSettings
import com.resukisu.resukisu.domain.model.KernelStatus
import com.resukisu.resukisu.domain.model.ManagerRecord
import com.resukisu.resukisu.domain.model.ManagerRuntimeInfo
import com.resukisu.resukisu.getKernelVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KernelRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    suspend fun getStatus(): KernelStatus = withContext(Dispatchers.IO) {
        val kernelVersion = getKernelVersion()
        val isManager = runCatching { Natives.isManager }.getOrDefault(false)
        val ksuVersion = if (isManager) Natives.version else null
        val kernelUapi = if (isManager) Natives.kernelUAPIVersion else null
        val managerUapi = runCatching { Natives.managerUAPIVersion }.getOrDefault(1)
        val fullVersion = runCatching { Natives.getFullVersion() }.getOrDefault("Unknown")
        KernelStatus(
            isManager = isManager,
            ksuVersion = ksuVersion,
            managerUAPIVersion = managerUapi,
            kernelUAPIVersion = kernelUapi,
            ksuFullVersion = "$fullVersion (${Natives.version}/$kernelUapi)",
            lkmMode = ksuVersion?.let { if (kernelVersion.isGKI()) Natives.isLkmMode else null },
            kernelVersion = kernelVersion,
            isRootAvailable = runCatching { ksuCliRepository.rootAvailable() }.getOrDefault(false),
            requireNewKernel = runCatching { isManager && Natives.requireNewKernel() }.getOrDefault(
                false
            ),
            uapiMismatch = runCatching { isManager && Natives.checkUAPIMismatch() }.getOrDefault(
                false
            ),
            isSELinuxPermissive = runCatching { isSELinuxPermissive() }.getOrDefault(false),
            isOfficialSignature = runCatching {
                ksuCliRepository.isOfficialSignature(application.packageResourcePath)
            }.getOrDefault(false),
            kernelPatchImplementation = runCatching {
                Natives.getKernelPatchImplementation()
            }.getOrDefault(KernelPatchImplementation.NONE),
            hookType = runCatching { Natives.getHookType() }.getOrDefault(""),
            isSafeMode = runCatching { Natives.isSafeMode }.getOrDefault(false),
            isLateLoadMode = runCatching { Natives.isLateLoadMode }.getOrDefault(false),
            isPrBuild = runCatching { Natives.isPrBuild }.getOrDefault(false),
        )
    }

    suspend fun getManagerRuntimeInfo(): ManagerRuntimeInfo = withContext(Dispatchers.IO) {
        ManagerRuntimeInfo(
            managers = runCatching { Natives.getManagersList()?.managers.orEmpty() }
                .getOrDefault(emptyList())
                .map { ManagerRecord(it.uid, it.signatureIndex) },
            dynamicSignatureEnabled = runCatching {
                Natives.getDynamicManager()?.isValid() == true
            }.getOrDefault(false),
        )
    }

    suspend fun getFeatureSettings(): KernelFeatureSettings = withContext(Dispatchers.IO) {
        KernelFeatureSettings(
            suEnabled = runCatching { Natives.isSuEnabled() }.getOrDefault(false),
            kernelUmountEnabled = runCatching { Natives.isKernelUmountEnabled() }.getOrDefault(false),
            suLogEnabled = runCatching { Natives.isSuLogEnabled() }.getOrDefault(false),
            selinuxHideEnabled = runCatching { Natives.isSelinuxHideEnabled() }.getOrDefault(false),
            defaultUmountModules = runCatching { Natives.isDefaultUmountModules() }.getOrDefault(
                false
            ),
        )
    }

    suspend fun setSuEnabled(enabled: Boolean): Boolean = saveFeature {
        Natives.setSuEnabled(enabled)
    }

    suspend fun setKernelUmountEnabled(enabled: Boolean): Boolean = saveFeature {
        Natives.setKernelUmountEnabled(enabled)
    }

    suspend fun setSuLogEnabled(enabled: Boolean): Boolean = saveFeature {
        Natives.setSuLogEnabled(enabled)
    }

    suspend fun setSelinuxHideEnabled(enabled: Boolean): Int = withContext(Dispatchers.IO) {
        Natives.setSelinuxHideEnabled(enabled).also {
            ksuCliRepository.execKsud("feature save", true)
        }
    }

    suspend fun setDefaultUmountModules(enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) { Natives.setDefaultUmountModules(enabled) }

    fun isLateLoadMode(): Boolean = runCatching { Natives.isLateLoadMode }.getOrDefault(false)

    private suspend fun saveFeature(block: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        block().also { success ->
            if (success) ksuCliRepository.execKsud("feature save", true)
        }
    }
}
