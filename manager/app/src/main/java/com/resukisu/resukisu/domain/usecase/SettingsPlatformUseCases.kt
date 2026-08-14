package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.settings.SettingsPlatformRepository
import com.resukisu.resukisu.domain.model.AppearanceSetting
import com.resukisu.resukisu.domain.model.PlatformSetting

class LoadSettingsPlatformUseCase(private val repository: SettingsPlatformRepository) {
    operator fun invoke() = repository.load()
}

class UpdateAppearanceUseCase(private val repository: SettingsPlatformRepository) {
    suspend operator fun invoke(setting: AppearanceSetting) = repository.updateAppearance(setting)
}

class UpdatePlatformSettingUseCase(private val repository: SettingsPlatformRepository) {
    operator fun invoke(setting: PlatformSetting) = repository.updatePlatform(setting)
}

class GetPlatformFeatureStatusUseCase(private val repository: SettingsPlatformRepository) {
    suspend operator fun invoke() = repository.getFeatureStatus()
}
