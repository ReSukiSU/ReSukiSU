package com.resukisu.resukisu.domain.model

import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.Natives.Profile.Namespace
import com.resukisu.resukisu.Natives.Profile.RootProfileFlag

data class ProfileTemplate(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val author: String = "",
    val local: Boolean = true,
    val namespace: Int = Namespace.INHERITED.ordinal,
    val uid: Int = Natives.ROOT_UID,
    val gid: Int = Natives.ROOT_GID,
    val groups: List<Int> = emptyList(),
    val capabilities: List<Int> = emptyList(),
    val context: String = Natives.KERNEL_SU_DOMAIN,
    val rules: List<String> = emptyList(),
    val flags: List<Int> = listOf(RootProfileFlag.NO_NEW_PRIVS.ordinal),
)

sealed interface ProfileTemplateFailure {
    data object Empty : ProfileTemplateFailure
    data object NotFound : ProfileTemplateFailure
    data object Invalid : ProfileTemplateFailure
    data object Conflict : ProfileTemplateFailure
    data class Command(val message: String) : ProfileTemplateFailure
}

class ProfileTemplateException(val reason: ProfileTemplateFailure) : Exception()
