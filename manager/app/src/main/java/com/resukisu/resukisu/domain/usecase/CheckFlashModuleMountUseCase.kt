package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.flash.FlashRepository

class CheckFlashModuleMountUseCase(private val repository: FlashRepository) {
    suspend operator fun invoke(uri: String) = repository.moduleNeedsMount(uri)
}
