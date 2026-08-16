package com.resukisu.resukisu.domain.usecase

import android.content.Context
import com.resukisu.resukisu.data.settings.LocaleRepository

class ApplyLanguageUseCase(private val repository: LocaleRepository) {
    operator fun invoke(context: Context): Context = repository.applyLanguage(context)
}

class IsSystemLanguageSettingsUseCase(private val repository: LocaleRepository) {
    operator fun invoke(): Boolean = repository.isSystemLanguageSettings()
}

class LaunchSystemLanguageSettingsUseCase(private val repository: LocaleRepository) {
    operator fun invoke(context: Context) = repository.launchSystemLanguageSettings(context)
}
