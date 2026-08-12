package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.AppSettingsRepository

class GetBooleanPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, defaultValue: Boolean = false) =
        repository.getBoolean(key, defaultValue)
}

class SetBooleanPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, value: Boolean) = repository.putBoolean(key, value)
}

class GetStringPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, defaultValue: String? = null) =
        repository.getString(key, defaultValue)
}

class SetStringPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, value: String?) = repository.putString(key, value)
}

class GetStringSetPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, defaultValue: Set<String> = emptySet()) =
        repository.getStringSet(key, defaultValue)
}

class SetStringSetPreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String, value: Set<String>) = repository.putStringSet(key, value)
}

class RemovePreferenceUseCase(private val repository: AppSettingsRepository) {
    operator fun invoke(key: String) = repository.remove(key)
}
