package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.kernel.KernelRepository

class GetKernelStatusUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke() = repository.getStatus()
}

class GetManagerRuntimeInfoUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke() = repository.getManagerRuntimeInfo()
}

class GetKernelFeatureSettingsUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke() = repository.getFeatureSettings()
}

class SetSuEnabledUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setSuEnabled(enabled)
}

class SetKernelUmountEnabledUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setKernelUmountEnabled(enabled)
}

class ConfigureSuLogUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setSuLogEnabled(enabled)
}

class SetSelinuxHideEnabledUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setSelinuxHideEnabled(enabled)
}

class SetDefaultUmountModulesUseCase(private val repository: KernelRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setDefaultUmountModules(enabled)
}

class IsLateLoadModeUseCase(private val repository: KernelRepository) {
    operator fun invoke() = repository.isLateLoadMode()
}
