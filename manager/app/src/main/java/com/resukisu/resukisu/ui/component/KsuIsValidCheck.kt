package com.resukisu.resukisu.ui.component

import androidx.compose.runtime.Composable
import com.resukisu.resukisu.domain.model.KernelStatus

@Composable
inline fun KsuIsValid(
    status: KernelStatus,
    content: @Composable () -> Unit
) {
    if (status.isValid)
        content()
}
