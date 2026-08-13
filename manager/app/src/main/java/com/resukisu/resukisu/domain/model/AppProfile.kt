package com.resukisu.resukisu.domain.model

import com.resukisu.resukisu.Natives

data class AppProfile(
    val name: String,
    val currentUid: Int = 0,
    val allowSu: Boolean = false,
    val rootUseDefault: Boolean = true,
    val rootTemplate: String? = null,
    val uid: Int = Natives.ROOT_UID,
    val gid: Int = Natives.ROOT_GID,
    val groups: List<Int> = emptyList(),
    val capabilities: List<Int> = emptyList(),
    val context: String = Natives.KERNEL_SU_DOMAIN,
    val namespace: Int = Natives.Profile.Namespace.INHERITED.ordinal,
    val nonRootUseDefault: Boolean = true,
    val umountModules: Boolean = true,
    val rules: String = "",
    val flags: Long = Natives.FLAG_KSU_NO_NEW_PRIVS,
) : java.io.Serializable
