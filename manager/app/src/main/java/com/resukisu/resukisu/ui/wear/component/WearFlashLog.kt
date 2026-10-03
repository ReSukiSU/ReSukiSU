package com.resukisu.resukisu.ui.wear.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.HourglassTop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import com.resukisu.resukisu.ui.theme.MonospaceFontFamily

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */
enum class WearFlashStatus { RUNNING, SUCCESS, FAILED }

/**
 * The state of a flash or script run as a status chip: running (neutral, with the current step),
 * success (the screen's high-emphasis chip) or failure (red).
 */
@Composable
fun TransformingLazyColumnItemScope.WearFlashStatusChip(
    transformationSpec: TransformationSpec,
    status: WearFlashStatus,
    label: String,
    detail: String? = null,
) {
    WearChip(
        transformationSpec, label, secondaryLabel = detail?.ifBlank { null },
        icon = when (status) {
            WearFlashStatus.RUNNING -> Icons.TwoTone.HourglassTop
            WearFlashStatus.SUCCESS -> Icons.TwoTone.CheckCircle
            WearFlashStatus.FAILED -> Icons.TwoTone.Error
        },
        emphasis = when (status) {
            WearFlashStatus.RUNNING -> WearChipEmphasis.MEDIUM
            WearFlashStatus.SUCCESS -> WearChipEmphasis.HIGH
            WearFlashStatus.FAILED -> WearChipEmphasis.ERROR
        },
    )
}

/** A centered progress indicator; determinate when [progress] is known. */
@Composable
fun TransformingLazyColumnItemScope.WearFlashProgress(transformationSpec: TransformationSpec, progress: Float? = null) {
    WearScaledItem(transformationSpec) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (progress == null) CircularProgressIndicator(modifier = Modifier.size(32.dp))
            else CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * Output as a Wear text list: one monospace line per item, so long logs scale at the round edges
 * and scroll line by line. Blank lines are kept to preserve the script's layout.
 */
fun TransformingLazyColumnScope.wearLogLines(transformationSpec: TransformationSpec, lines: List<String>) {
    items(count = lines.size, contentType = { "wear-log-line" }) { index ->
        WearScaledItem(transformationSpec) {
            Text(
                lines[index],
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = MonospaceFontFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Splits output into lines, without the trailing empty line a final newline produces. */
fun String.toLogLines(): List<String> = if (isEmpty()) emptyList() else trimEnd('\n').split('\n')

/** While [following], keeps the newest line in view unless the user is scrolling. */
@Composable
fun WearFollowLog(listState: TransformingLazyColumnState, lineCount: Int, following: Boolean) {
    LaunchedEffect(lineCount, following) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (following && last > 0 && !listState.isScrollInProgress) listState.scrollToItem(last)
    }
}
