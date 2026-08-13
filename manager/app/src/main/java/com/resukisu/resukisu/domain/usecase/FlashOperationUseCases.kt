package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.flash.FlashRepository
import com.resukisu.resukisu.domain.model.FlashOperation

class ExecuteFlashOperationUseCase(private val repository: FlashRepository) {
    operator fun invoke(operation: FlashOperation) = repository.execute(operation)
}
