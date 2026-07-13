package com.resukisu.resukisu.ui.screen.about

import androidx.compose.runtime.Immutable

@Immutable
data class AboutScreenActions(
    val onBack: () -> Unit,
    val onOpenLink: (String) -> Unit,
    val onOpenLicense: () -> Unit = {},
)
