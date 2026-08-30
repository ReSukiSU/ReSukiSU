package com.resukisu.resukisu.domain.model

import com.resukisu.resukisu.KernelVersion
import com.resukisu.resukisu.Natives.KernelPatchImplementation

data class ManagerRecord(
    val uid: Int,
    val signatureIndex: Int,
)

data class ManagerRuntimeInfo(
    val managers: List<ManagerRecord> = emptyList(),
    val dynamicSignatureEnabled: Boolean = false,
)

data class KernelStatus(
    val isManager: Boolean = false,
    val ksuVersion: Int? = null,
    val managerUAPIVersion: Int = 1,
    val kernelUAPIVersion: Int? = 1,
    val ksuFullVersion: String? = null,
    val lkmMode: Boolean? = null,
    val kernelVersion: KernelVersion,
    val isRootAvailable: Boolean = false,
    val requireNewKernel: Boolean = false,
    val uapiMismatch: Boolean = false,
    val isSELinuxPermissive: Boolean = false,
    val isOfficialSignature: Boolean = true,
    val kernelPatchImplementation: KernelPatchImplementation = KernelPatchImplementation.NONE,
    val hookType: String = "",
    val isSafeMode: Boolean = false,
    val isLateLoadMode: Boolean = false,
    val isPrBuild: Boolean = false,
) {
    val isValid: Boolean
        get() = isManager && !requireNewKernel && isRootAvailable
}

data class KernelFeatureSettings(
    val suEnabled: Boolean,
    val kernelUmountEnabled: Boolean,
    val suLogEnabled: Boolean,
    val selinuxHideEnabled: Boolean,
    val defaultUmountModules: Boolean,
)
