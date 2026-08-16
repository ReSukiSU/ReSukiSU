package com.resukisu.resukisu.ui.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation keys for Navigation3.
 * Each destination is a NavKey (data object/data class) and can be saved/restored in the back stack.
 */
sealed interface Route : NavKey, Parcelable {
    @Parcelize
    @Serializable
    data object About : Route

    @Parcelize
    @Serializable
    data object OpenSourceLicense : Route

    @Parcelize
    @Serializable
    data object Sulog : Route

    @Parcelize
    @Serializable
    data object Main : Route

    @Parcelize
    @Serializable
    data object Home : Route

    @Parcelize
    @Serializable
    data object SuperUser : Route

    @Parcelize
    @Serializable
    data object Module : Route

    @Parcelize
    @Serializable
    data object Settings : Route

    @Parcelize
    @Serializable
    data object AppProfileTemplate : Route

    @Parcelize
    @Serializable
    data class TemplateEditor(
        val templateId: String,
        val readOnly: Boolean,
        val isCreation: Boolean = false,
    ) : Route

    @Parcelize
    @Serializable
    data class AppProfile(val uid: Int, val packageName: String) : Route

    @Parcelize
    @Serializable
    data class Install(val preselectedKernelUri: String?) : Route

    @Parcelize
    @Serializable
    data class ModuleRepoDetail(val moduleId: String) : Route

    @Parcelize
    @Serializable
    data object ModuleRepo : Route

    @Parcelize
    @Serializable
    data class Flash(
        val type: String,
        val uris: List<String> = emptyList(),
        val currentIndex: Int = 0,
        val bootUri: String? = null,
        val lkmUri: String? = null,
        val kmi: String? = null,
        val ota: Boolean = false,
        val partition: String? = null,
    ) : Route {
        companion object {
            const val TYPE_BOOT = "boot"
            const val TYPE_MODULE = "module"
            const val TYPE_MODULES = "modules"
            const val TYPE_MODULE_UPDATE = "module_update"
            const val TYPE_RESTORE = "restore"
            const val TYPE_UNINSTALL = "uninstall"

            fun boot(
                bootUri: String?,
                lkmUri: String?,
                kmi: String?,
                ota: Boolean,
                partition: String?,
            ) = Flash(
                type = TYPE_BOOT,
                bootUri = bootUri,
                lkmUri = lkmUri,
                kmi = kmi,
                ota = ota,
                partition = partition,
            )

            fun module(uri: String) = Flash(TYPE_MODULE, uris = listOf(uri))
            fun modules(uris: List<String>, currentIndex: Int = 0) =
                Flash(TYPE_MODULES, uris = uris, currentIndex = currentIndex)

            fun moduleUpdate(uri: String) = Flash(TYPE_MODULE_UPDATE, uris = listOf(uri))
            fun restore() = Flash(TYPE_RESTORE)
            fun uninstall() = Flash(TYPE_UNINSTALL)
        }
    }

    @Parcelize
    @Serializable
    data class ExecuteModuleAction(val moduleId: String) : Route

    @Parcelize
    @Serializable
    data object SuSFSConfig : Route

    @Parcelize
    @Serializable
    data object ThemeSettings : Route

    @Parcelize
    @Serializable
    data object UmountManager : Route

    @Parcelize
    @Serializable
    data object DynamicManager : Route

    @Parcelize
    @Serializable
    data class KernelFlash(
        val kernelUri: String,
        val selectedSlot: String?
    ) : Route
}
