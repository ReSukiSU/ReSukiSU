package com.resukisu.resukisu.ui.wear.component

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** A page nested inside one route; Back and swipe-to-dismiss return to that route instead of leaving it. */
@Composable
fun WearSubPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    WearSwipeToDismissBox(onDismissed = onBack) { isBackground ->
        if (!isBackground) content()
    }
}
