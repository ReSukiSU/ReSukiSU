package com.resukisu.resukisu.ui.navigation

import android.net.Uri
import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import com.resukisu.resukisu.ui.screen.FlashIt
import com.resukisu.resukisu.ui.viewmodel.ModuleRepoViewModel
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import com.resukisu.resukisu.ui.viewmodel.TemplateViewModel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Contextual
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
        val template: @Contextual TemplateViewModel.TemplateInfo,
        val readOnly: Boolean
    ) : Route

    @Parcelize
    @Serializable
    data class AppProfile(val appGroup: @Contextual SuperUserViewModel.AppGroup) : Route

    @Parcelize
    @Serializable
    data class Install(val preselectedKernelUri: String?) : Route

    @Parcelize
    @Serializable
    data class ModuleRepoDetail(val module: @Contextual ModuleRepoViewModel.RepoModule) : Route

    @Parcelize
    @Serializable
    data object ModuleRepo : Route

    @Parcelize
    @Serializable
    data class Flash(val flashIt: @Contextual FlashIt) : Route

    @Parcelize
    @Serializable
    data class ExecuteModuleAction(val moduleId: String) : Route

    @Parcelize
    @Serializable
    data object SuSFSConfig : Route

    @Parcelize
    @Serializable
    data object MoreSettings : Route

    @Parcelize
    @Serializable
    data object UmountManager : Route

    @Parcelize
    @Serializable
    data class KernelFlash(
        val kernelUri: @Contextual Uri,
        val selectedSlot: String?,
        val kpmPatchEnabled: Boolean,
        val kpmUndoPatch: Boolean
    ) : Route
}