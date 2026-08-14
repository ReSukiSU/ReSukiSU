package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.domain.model.SusKstatStatically
import com.resukisu.resukisu.domain.model.UidScheme
import com.resukisu.resukisu.data.susfs.SuSFSRepository

class SuSFSConfigUseCase(private val repository: SuSFSRepository) {
    suspend fun loadConfig() = repository.loadConfig()
    suspend fun refreshConfig() = repository.refreshConfig()
    suspend fun restoreDefaultConfig() = repository.restoreDefaultConfig()
    suspend fun setConfigEnabled(enabled: Boolean) = repository.setConfigEnabled(enabled)
    suspend fun loadStatusInfo(forceRefresh: Boolean = false) =
        repository.loadStatusInfo(forceRefresh)

    suspend fun addSusPath(path: String) = repository.addSusPath(path)
    suspend fun addSusPathLoop(path: String) = repository.addSusPathLoop(path)
    suspend fun removeSusPath(path: String) = repository.removeSusPath(path)
    suspend fun addSusKstat(path: String) = repository.addSusKstat(path)
    suspend fun addSusKstatFullClone(path: String) = repository.addSusKstatFullClone(path)
    suspend fun addSusKstatStatically(path: String, values: SusKstatStatically) =
        repository.addSusKstatStatically(path, values)

    suspend fun removeSusKstat(path: String) = repository.removeSusKstat(path)
    suspend fun setUname(release: String, version: String) = repository.setUname(release, version)
    suspend fun loadSlotInfo() = repository.loadSlotInfo()
    suspend fun enableLog(enabled: Boolean) = repository.enableLog(enabled)
    suspend fun hideSusMntsForNonSuProcs(enabled: Boolean) =
        repository.hideSusMntsForNonSuProcs(enabled)

    suspend fun enableAvcLogSpoofing(enabled: Boolean) = repository.enableAvcLogSpoofing(enabled)
    suspend fun setCmdlineOrBootconfig(path: String) = repository.setCmdlineOrBootconfig(path)
    suspend fun addOpenRedirect(targetPath: String, redirectedPath: String, uidScheme: UidScheme) =
        repository.addOpenRedirect(targetPath, redirectedPath, uidScheme)

    suspend fun removeOpenRedirect(targetPath: String) = repository.removeOpenRedirect(targetPath)
    suspend fun addSusMap(path: String) = repository.addSusMap(path)
    suspend fun removeSusMap(path: String) = repository.removeSusMap(path)
    suspend fun exportConfig(uri: String) = repository.exportConfig(uri)
    suspend fun importConfig(uri: String) = repository.importConfig(uri)
}
