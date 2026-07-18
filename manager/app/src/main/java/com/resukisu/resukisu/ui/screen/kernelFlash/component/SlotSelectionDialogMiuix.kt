package com.resukisu.resukisu.ui.screen.kernelFlash.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resukisu.resukisu.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Miuix rendering of the kernel-flash slot picker. The slot state and [onConfirm] callback are
 * owned by [SlotSelectionDialog]; this is chrome only, so the flash logic stays identical.
 */
@Composable
fun SlotSelectionDialogMiuix(
    show: Boolean,
    currentSlot: String?,
    errorMessage: String?,
    selectedSlot: String?,
    onSelectSlot: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(id = R.string.select_slot_title),
        onDismissRequest = onDismiss,
        content = {
            Column {
                if (errorMessage != null) {
                    Text(
                        text = "Error: $errorMessage",
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariantSummary
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.current_slot, currentSlot ?: "Unknown"),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariantSummary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.select_slot_description),
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariantSummary
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SlotOption(
                        label = stringResource(id = R.string.slot_a),
                        selected = selectedSlot == "a",
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectSlot("a") }
                    )
                    SlotOption(
                        label = stringResource(id = R.string.slot_b),
                        selected = selectedSlot == "b",
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectSlot("b") }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(android.R.string.ok),
                        enabled = selectedSlot != null,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}

@Composable
private fun SlotOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(
            color = if (selected) colorScheme.primary else colorScheme.secondaryContainer
        ),
        onClick = onClick,
        insideMargin = PaddingValues(vertical = 16.dp, horizontal = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight(550),
            color = if (selected) colorScheme.onPrimary else colorScheme.onSecondaryContainer
        )
    }
}
