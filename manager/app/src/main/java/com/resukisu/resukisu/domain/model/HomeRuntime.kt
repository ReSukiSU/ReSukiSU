package com.resukisu.resukisu.domain.model

import com.resukisu.resukisu.KernelVersion

data class HomeBasicInfo(
    val kernelRelease: String = "",
    val androidVersion: String = "",
    val deviceModel: String = "",
    val managerVersion: Triple<String, Int, Int> = Triple("", 0, 0),
    val selinuxStatus: String = "",
    val seccompStatus: Int = -1,
)

data class HomeModuleOverview(
    val count: Int = 0,
    val zygiskImplementation: String = "",
    val metaModuleImplementation: String = "",
)

data class HomeSystemInfo(
    val kernelRelease: String = "",
    val androidVersion: String = "",
    val deviceModel: String = "",
    val managerVersion: Triple<String, Int, Int> = Triple("", 0, 0),
    val selinuxStatus: String = "",
    val susfsEnabled: Boolean = false,
    val susfsVersionSupported: Boolean = false,
    val susfsVersion: String = "",
    val susfsFeatures: String = "",
    val superuserCount: Int = 0,
    val moduleCount: Int = 0,
    val managersList: ManagerRuntimeInfo? = null,
    val isDynamicSignEnabled: Boolean = false,
    val zygiskImplement: String = "",
    val metaModuleImplement: String = "",
    val seccompStatus: Int = -1,
)

data class HomeDashboardState(
    val systemStatus: KernelStatus = KernelStatus(kernelVersion = KernelVersion(0, 0, 0)),
    val systemInfo: HomeSystemInfo = HomeSystemInfo(),
    val stableManagerUpdate: ManagerUpdateInfo? = null,
    val betaManagerUpdate: ManagerUpdateInfo? = null,
    val isBetaManagerUpdateCheckFailed: Boolean = false,
    val isSimpleMode: Boolean = false,
    val isInitialDataLoaded: Boolean = false,
    val isCoreDataLoaded: Boolean = false,
    val isExtendedDataLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
)
