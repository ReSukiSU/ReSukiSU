package com.resukisu.resukisu.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resukisu.resukisu.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun InstallConfirmationDialogMiuix(
    show: Boolean,
    zipFiles: List<ZipFileInfo>,
    onConfirm: (List<ZipFileInfo>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    WindowDialog(
        show = show && zipFiles.isNotEmpty(),
        title = if (zipFiles.size == 1) {
            stringResource(R.string.confirm_installation)
        } else {
            stringResource(R.string.confirm_multiple_installation, zipFiles.size)
        },
        onDismissRequest = onDismiss,
        content = {
            Column {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    items(zipFiles) { zipFile ->
                        InstallItemCardMiuix(zipFile)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.install_confirm),
                        onClick = { onConfirm(zipFiles) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}

@Composable
private fun InstallItemCardMiuix(zipFile: ZipFileInfo) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        insideMargin = PaddingValues(14.dp)
    ) {
        Column {
            Text(
                text = zipFile.name.ifEmpty {
                    when (zipFile.type) {
                        ZipType.MODULE -> context.getString(R.string.unknown_module)
                        ZipType.KERNEL -> context.getString(R.string.unknown_kernel)
                        else -> context.getString(R.string.unknown_file)
                    }
                },
                fontSize = 16.sp,
                fontWeight = FontWeight(550),
                color = colorScheme.onSurface
            )
            Text(
                text = when (zipFile.type) {
                    ZipType.MODULE -> context.getString(R.string.module_package)
                    ZipType.KERNEL -> context.getString(R.string.kernel_package)
                    else -> context.getString(R.string.unknown_package)
                },
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary
            )
            if (zipFile.version.isNotEmpty()) {
                InstallInfoRowMiuix(
                    stringResource(R.string.version),
                    zipFile.version + if (zipFile.versionCode.isNotEmpty()) " (${zipFile.versionCode})" else ""
                )
            }
            if (zipFile.author.isNotEmpty()) {
                InstallInfoRowMiuix(stringResource(R.string.author), zipFile.author)
            }
            if (zipFile.description.isNotEmpty() && zipFile.type == ZipType.MODULE) {
                InstallInfoRowMiuix(stringResource(R.string.description), zipFile.description)
            }
            if (zipFile.supported.isNotEmpty() && zipFile.type == ZipType.KERNEL) {
                InstallInfoRowMiuix(stringResource(R.string.supported_devices), zipFile.supported)
            }
        }
    }
}

@Composable
private fun InstallInfoRowMiuix(label: String, value: String) {
    Row(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "$label: ",
            fontSize = 13.sp,
            color = colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = colorScheme.onSurface
        )
    }
}
