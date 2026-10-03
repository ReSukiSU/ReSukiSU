package com.resukisu.resukisu.ui.wear.component

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.ManagerUpdateChannel
import com.resukisu.resukisu.domain.model.ManagerUpdateInfo
import com.resukisu.resukisu.domain.usecase.EnqueueManagerUpdateUseCase
import com.resukisu.resukisu.ui.component.rememberCustomDialog
import org.koin.compose.koinInject

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * Asks once per version whether to update the manager, when an update check finds one. It shows the
 * phone's update texts in a Wear alert dialog (the message, version details and changelog scroll
 * inside it) and confirming starts the same download as the phone's update card.
 */
@Composable
fun WearManagerUpdateDialog(update: ManagerUpdateInfo?) {
    val context = LocalContext.current
    val enqueue = koinInject<EnqueueManagerUpdateUseCase>()
    val current by rememberUpdatedState(update)
    var promptedVersion by rememberSaveable { mutableIntStateOf(0) }
    val deniedText = stringResource(R.string.notification_permission_denied)
    // As on the phone, the download notification needs the notification permission on Android 13+.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) current?.let(enqueue::invoke)
        else Toast.makeText(context, deniedText, Toast.LENGTH_SHORT).show()
    }
    fun download(info: ManagerUpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else enqueue(info)
    }
    val dialog = rememberCustomDialog { dismiss ->
        val info = current ?: return@rememberCustomDialog
        val stable = info.channel == ManagerUpdateChannel.STABLE
        val details = stringResource(R.string.manager_update_details, info.versionName, info.versionCode, info.abi)
        AlertDialog(
            visible = true,
            onDismissRequest = dismiss,
            icon = { Icon(Icons.TwoTone.SystemUpdate, contentDescription = null) },
            title = { Text(stringResource(if (stable) R.string.manager_update_stable else R.string.manager_update_beta)) },
            text = {
                Text(stringResource(if (stable) R.string.new_version_available else R.string.beta_version_available,
                    info.versionCode))
            },
            confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = { dismiss(); download(info) }) },
            dismissButton = { AlertDialogDefaults.DismissButton(onClick = dismiss) },
        ) {
            item { Text(details, style = MaterialTheme.typography.bodySmall) }
            info.changelog.takeIf { it.isNotBlank() }?.chunked(350)?.forEach { chunk ->
                item { Text(chunk, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    LaunchedEffect(update?.versionCode) {
        if (update != null && update.versionCode != promptedVersion) {
            promptedVersion = update.versionCode
            dialog.show()
        }
    }
}
