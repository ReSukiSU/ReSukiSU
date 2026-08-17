package com.resukisu.resukisu.domain.model

data class InstalledModule(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val versionCode: Int,
    val description: String,
    val enabled: Boolean,
    val update: Boolean,
    val remove: Boolean,
    val updateJson: String,
    val hasWebUi: Boolean,
    val hasActionScript: Boolean,
    val metamodule: Boolean,
    val actionIconPath: String?,
    val webUiIconPath: String?,
    val dirId: String,
    val moduleUpdate: ModuleUpdateMetadata?,
)

data class InstalledModulesState(
    val modules: List<InstalledModule> = emptyList(),
    val refreshing: Boolean = false,
    val hasModuleRequireMount: Boolean = false,
    val hasMagisk: Boolean = false,
    val metaModuleStatus: MetaModuleStatus = MetaModuleStatus.MISSING,
)

data class ModulePreferences(
    val sortEnabledFirst: Boolean = false,
    val sortActionFirst: Boolean = false,
    val showMoreModuleInfo: Boolean = false,
)

enum class MetaModuleStatus {
    ACTIVE,
    MISSING,
    REMOVED,
    DISABLED,
}
