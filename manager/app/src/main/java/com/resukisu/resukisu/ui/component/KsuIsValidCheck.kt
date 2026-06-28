package com.resukisu.resukisu.ui.component

import androidx.compose.runtime.Composable
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.ui.util.rootAvailable

@Composable
inline fun KsuIsValid(
    content: @Composable () -> Unit
) {
    if (ksuIsValid())
        content()
}

private var tested = false
private var ksuIsValid = false

/**
 * Check the manager is valid or not
 *
 * true = ksu valid
 * false = ksu invalid
 *
 * invalid = not is manager
 */
fun ksuIsValid() : Boolean {
    if (tested) return ksuIsValid

    val isManager = Natives.isManager
    ksuIsValid = isManager && !Natives.requireNewKernel() && rootAvailable()
    tested = true

    return ksuIsValid
}