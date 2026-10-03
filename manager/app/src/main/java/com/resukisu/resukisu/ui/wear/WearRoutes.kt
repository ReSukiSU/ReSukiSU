package com.resukisu.resukisu.ui.wear

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The settings pages that open from the Wear settings root. */
@Serializable
enum class WearSettingsCategory { General, Security, Advanced, Display }

/** The settings that are chosen from a single list of options. */
@Serializable
enum class WearSelection { PickerMode, LinkMode, ScreenShape, Language, SuCompat }

/** Navigation destinations of [WearManagerScreen]; [Main] is the pager with the four top-level pages. */
@Serializable
sealed interface WearRoute : NavKey {
    @Serializable
    data object Main : WearRoute

    @Serializable
    data class Module(val id: String) : WearRoute

    @Serializable
    data class ModuleAction(val id: String) : WearRoute

    @Serializable
    data class ModuleUpdate(val id: String) : WearRoute

    @Serializable
    data class ModuleInstall(val uri: String) : WearRoute

    @Serializable
    data object ModuleSort : WearRoute

    @Serializable
    data object ModuleSearch : WearRoute

    @Serializable
    data object ModuleSearchResults : WearRoute

    @Serializable
    data class App(val uid: Int, val packageName: String) : WearRoute

    @Serializable
    data object AppFilter : WearRoute

    @Serializable
    data object AppSearch : WearRoute

    @Serializable
    data object AppSearchResults : WearRoute

    @Serializable
    data object Logs : WearRoute

    @Serializable
    data object LogOptions : WearRoute

    @Serializable
    data class LogEntry(val key: String) : WearRoute

    @Serializable
    data object LogSearch : WearRoute

    @Serializable
    data class Settings(val category: WearSettingsCategory) : WearRoute

    @Serializable
    data class Selection(val selection: WearSelection) : WearRoute

    @Serializable
    data object Color : WearRoute

    @Serializable
    data object Dpi : WearRoute

    @Serializable
    data object Templates : WearRoute

    @Serializable
    data class TemplateEditor(val id: String, val readOnly: Boolean, val creation: Boolean) : WearRoute

    @Serializable
    data object SuSFS : WearRoute

    @Serializable
    data object DynamicManager : WearRoute

    @Serializable
    data object Umount : WearRoute

    @Serializable
    data object Uninstall : WearRoute

    /** Restoring the stock image or uninstalling permanently. */
    @Serializable
    data class UninstallFlash(val restore: Boolean) : WearRoute

    @Serializable
    data object KernelInstall : WearRoute

    @Serializable
    data object LkmInstall : WearRoute

    @Serializable
    data object Ak3Install : WearRoute

    /** Patching the boot image with the choices of [LkmInstall], as the phone's Flash screen does. */
    @Serializable
    data class BootFlash(
        val bootUri: String?,
        val lkmUri: String?,
        val kmi: String?,
        val ota: Boolean,
        val partition: String?,
        val allowShell: Boolean,
        val enableAdb: Boolean,
        val forceBackup: Boolean,
    ) : WearRoute

    @Serializable
    data class KernelFlash(val uri: String, val slot: String?, val skipKsud: Boolean) : WearRoute

    @Serializable
    data object Reboot : WearRoute

    @Serializable
    data object Bugreport : WearRoute

    @Serializable
    data object About : WearRoute

    @Serializable
    data object Licenses : WearRoute

    @Serializable
    data class Browser(val url: String) : WearRoute

    /** A link that could not be opened, with the reason as a string resource. */
    @Serializable
    data class LinkResult(val message: Int) : WearRoute
}
