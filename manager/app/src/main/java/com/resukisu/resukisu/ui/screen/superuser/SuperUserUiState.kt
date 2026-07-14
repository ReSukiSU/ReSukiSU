package com.resukisu.resukisu.ui.screen.superuser

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel.AppInfo

@Immutable
data class GroupedApps(
    val uid: Int,
    val apps: List<AppInfo>,
    val primary: AppInfo,
    val anyAllowSu: Boolean,
    val anyCustom: Boolean,
    val shouldUmount: Boolean,
    val ownerName: String? = null,
    val matchedPackageNames: Set<String> = emptySet(),
)

@Immutable
data class StatusMeta(
    val label: String,
    val bg: Color,
    val fg: Color
)
