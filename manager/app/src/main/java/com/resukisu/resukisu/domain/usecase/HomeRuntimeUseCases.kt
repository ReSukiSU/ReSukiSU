package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.network.NetworkStatusRepository
import com.resukisu.resukisu.data.system.HomeRuntimeRepository

class GetHomeBasicInfoUseCase(private val repository: HomeRuntimeRepository) {
    suspend operator fun invoke(managerUapiVersion: Int) =
        repository.getBasicInfo(managerUapiVersion)
}

class GetHomeModuleOverviewUseCase(private val repository: HomeRuntimeRepository) {
    suspend operator fun invoke() = repository.getModuleOverview()
}

class GetHomeSuperuserCountUseCase(private val repository: HomeRuntimeRepository) {
    suspend operator fun invoke() = repository.getSuperuserCount()
}

class IsNetworkAvailableUseCase(private val repository: NetworkStatusRepository) {
    operator fun invoke() = repository.isAvailable()
}
