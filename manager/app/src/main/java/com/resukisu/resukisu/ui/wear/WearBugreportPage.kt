package com.resukisu.resukisu.ui.wear

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.confirmationDialogCurvedText
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.usecase.GenerateBugreportUseCase
import com.resukisu.resukisu.ui.wear.component.WearActionButton
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Saving or sharing the bugreport, as the phone's log sheet does. */
@Composable
internal fun WearBugreportPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val generateBugreport = koinInject<GenerateBugreportUseCase>()
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val sendLog = stringResource(R.string.send_log)
    fun run(block: suspend () -> Unit) {
        busy = true
        failed = false
        scope.launch {
            runCatching { block() }.onFailure { failed = true }
            busy = false
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri: Uri? ->
        if (uri != null) run {
            withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
                    generateBugreport().inputStream().use { it.copyTo(output) }
                }
            }
            saved = true
        }
    }
    WearList(isLoading = busy, onBack = onBack) { spec ->
        item { WearPageHeader(spec, sendLog) }
        item {
            WearActionButton(spec, Icons.TwoTone.Save, stringResource(R.string.save_log), {
                val current = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm"))
                runCatching { exportLauncher.launch("KernelSU_bugreport_${current}.tar.gz") }.onFailure { failed = true }
            })
        }
        item {
            WearActionButton(spec, Icons.TwoTone.Share, sendLog, {
                run {
                    val bugreport = withContext(Dispatchers.IO) { generateBugreport() }
                    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", bugreport)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_STREAM, uri).setDataAndType(uri, "application/gzip")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), sendLog))
                }
            })
        }
    }
    val savedText = stringResource(R.string.log_saved)
    val failedText = stringResource(R.string.operation_failed)
    val style = ConfirmationDialogDefaults.curvedTextStyle
    SuccessConfirmationDialog(visible = saved, onDismissRequest = { saved = false },
        curvedText = { confirmationDialogCurvedText(savedText, style) })
    FailureConfirmationDialog(visible = failed, onDismissRequest = { failed = false },
        curvedText = { confirmationDialogCurvedText(failedText, style) })
}
