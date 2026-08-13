package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.update.ManagerUpdateRepository
import com.resukisu.resukisu.domain.model.ManagerUpdateChannel
import com.resukisu.resukisu.domain.model.ManagerUpdateInfo

class CheckManagerUpdateUseCase(
    private val repository: ManagerUpdateRepository,
) {
    suspend operator fun invoke(channel: ManagerUpdateChannel): ManagerUpdateInfo? =
        when (channel) {
            ManagerUpdateChannel.STABLE -> repository.checkStableUpdate()
            ManagerUpdateChannel.BETA -> repository.checkBetaUpdate()
        }
}
