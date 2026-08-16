package com.resukisu.resukisu.data.susfs

import android.app.Application
import com.resukisu.resukisu.domain.model.OpenRedirectItem as DomainOpenRedirectItem
import com.resukisu.resukisu.domain.model.SuSFSConfig as DomainSuSFSConfig
import com.resukisu.resukisu.domain.model.SuSFSSlotInfo as DomainSuSFSSlotInfo
import com.resukisu.resukisu.domain.model.UidScheme as DomainUidScheme
import com.resukisu.resukisu.domain.model.SuSFSStatus
import com.resukisu.resukisu.domain.model.SuSFSStatusInfo as DomainSuSFSStatusInfo
import com.resukisu.resukisu.domain.model.SusKstatItem as DomainSusKstatItem
import com.resukisu.resukisu.domain.model.SusKstatStatically as DomainSusKstatStatically
import com.resukisu.resukisu.domain.model.SusKstatType as DomainSusKstatType
import com.resukisu.resukisu.domain.model.SusPathItem as DomainSusPathItem
import com.resukisu.resukisu.domain.model.UnameConfig as DomainUnameConfig
import androidx.core.net.toUri

class SuSFSRepository(
    private val application: Application,
    private val helper: SuSFSConfigHelper,
) {
    suspend fun getStatus(): SuSFSStatus {
        val version = runCatching { helper.showVersion() }.getOrDefault("")
        return SuSFSStatus(
            enabled = version.isNotEmpty(),
            version = version,
            enabledFeatures = if (version.isEmpty()) "" else
                runCatching { helper.showEnabledFeatures() }.getOrDefault(""),
        )
    }

    suspend fun loadConfig(): DomainSuSFSConfig = helper.loadConfig().toDomain()
    suspend fun refreshConfig(): DomainSuSFSConfig = helper.refreshConfig().toDomain()
    suspend fun restoreDefaultConfig() = helper.restoreDefaultConfig()
    suspend fun setConfigEnabled(enabled: Boolean) = helper.setConfigEnabled(enabled)
    suspend fun loadStatusInfo(forceRefresh: Boolean = false): DomainSuSFSStatusInfo =
        helper.loadStatusInfo(forceRefresh).toDomain()

    suspend fun addSusPath(path: String) = helper.addSusPath(path)
    suspend fun addSusPathLoop(path: String) = helper.addSusPathLoop(path)
    suspend fun removeSusPath(path: String) = helper.removeSusPath(path)
    suspend fun addSusKstat(path: String) = helper.addSusKstat(path)
    suspend fun addSusKstatFullClone(path: String) = helper.addSusKstatFullClone(path)
    suspend fun addSusKstatStatically(path: String, values: DomainSusKstatStatically) =
        helper.addSusKstatStatically(
            path,
            values.ino,
            values.dev,
            values.nlink,
            values.size,
            values.atime,
            values.atime_nsec,
            values.mtime,
            values.mtime_nsec,
            values.ctime,
            values.ctime_nsec,
            values.blocks,
            values.blksize,
        )

    suspend fun removeSusKstat(path: String) = helper.removeSusKstat(path)
    suspend fun setUname(release: String, version: String) = helper.setUname(release, version)
    suspend fun loadSlotInfo(): List<DomainSuSFSSlotInfo>? =
        helper.loadSlotInfo()?.map { it.toDomain() }

    suspend fun enableLog(enabled: Boolean) = helper.enableLog(enabled)
    suspend fun hideSusMntsForNonSuProcs(enabled: Boolean) =
        helper.hideSusMntsForNonSuProcs(enabled)

    suspend fun enableAvcLogSpoofing(enabled: Boolean) = helper.enableAvcLogSpoofing(enabled)
    suspend fun setCmdlineOrBootconfig(path: String) = helper.setCmdlineOrBootconfig(path)
    suspend fun addOpenRedirect(
        targetPath: String,
        redirectedPath: String,
        uidScheme: DomainUidScheme,
    ) =
        helper.addOpenRedirect(targetPath, redirectedPath, uidScheme.toData())

    suspend fun removeOpenRedirect(targetPath: String) = helper.removeOpenRedirect(targetPath)
    suspend fun addSusMap(path: String) = helper.addSusMap(path)
    suspend fun removeSusMap(path: String) = helper.removeSusMap(path)
    suspend fun exportConfig(uri: String) = helper.exportConfigToUri(application, uri.toUri())
    suspend fun importConfig(uri: String) = helper.importConfigFromUri(application, uri.toUri())

    private fun SuSFSConfig.toDomain(): DomainSuSFSConfig = DomainSuSFSConfig(
        version = version,
        enabled = enabled,
        cmdline_or_bootconfig = cmdline_or_bootconfig,
        avc_log_spoofing = avc_log_spoofing,
        logging = logging,
        hide_sus_mnts_for_non_su_procs = hide_sus_mnts_for_non_su_procs,
        uname = DomainUnameConfig(uname.version, uname.release),
        sus_path = sus_path.mapTo(linkedSetOf()) {
            DomainSusPathItem(it.path, it.is_loop)
        },
        sus_kstat = sus_kstat.mapTo(linkedSetOf()) {
            DomainSusKstatItem(
                path = it.path,
                spoof_type = it.spoof_type.toDomain(),
                statically = it.statically?.toDomain(),
            )
        },
        open_redirect = open_redirect.mapTo(linkedSetOf()) {
            DomainOpenRedirectItem(it.target_path, it.redirected_path, it.uid_scheme.toDomain())
        },
        sus_map = sus_map,
    )

    private fun SuSFSStatusInfo.toDomain(): DomainSuSFSStatusInfo =
        DomainSuSFSStatusInfo(version, enabledFeatures, variant)

    private fun SuSFSSlotInfo.toDomain(): DomainSuSFSSlotInfo =
        DomainSuSFSSlotInfo(slotName, uname, buildTime)

    private fun SusKstatType.toDomain(): DomainSusKstatType = when (this) {
        SusKstatType.Normal -> DomainSusKstatType.Normal
        SusKstatType.FullClone -> DomainSusKstatType.FullClone
        SusKstatType.Statically -> DomainSusKstatType.Statically
    }

    private fun SusKstatStatically.toDomain(): DomainSusKstatStatically =
        DomainSusKstatStatically(
            ino, dev, nlink, size, atime, atime_nsec, mtime, mtime_nsec,
            ctime, ctime_nsec, blocks, blksize,
        )

    private fun DomainUidScheme.toData(): UidScheme =
        UidScheme.entries.first { it.value == value }

    private fun UidScheme.toDomain(): DomainUidScheme =
        DomainUidScheme.entries.first { it.value == value }
}
