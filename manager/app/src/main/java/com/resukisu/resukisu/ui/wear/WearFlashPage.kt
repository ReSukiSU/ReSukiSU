package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.FlashOperation
import com.resukisu.resukisu.domain.usecase.IsModuleUriAccessibleUseCase
import com.resukisu.resukisu.domain.usecase.TakeModuleUriPermissionUseCase
import com.resukisu.resukisu.ui.wear.component.WearActionButton
import com.resukisu.resukisu.ui.wear.component.WearFlashProgress
import com.resukisu.resukisu.ui.wear.component.WearFlashStatus
import com.resukisu.resukisu.ui.wear.component.WearFlashStatusChip
import com.resukisu.resukisu.ui.wear.component.WearFollowLog
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.toLogLines
import com.resukisu.resukisu.ui.wear.component.wearLogLines
import com.resukisu.resukisu.ui.viewmodel.FlashUiAction
import com.resukisu.resukisu.ui.viewmodel.FlashViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Runs a [FlashOperation] with the phone's FlashViewModel: a module ZIP, a boot image patch, or
 * uninstalling. [onInstalled] runs once the operation succeeds.
 */
@Composable
internal fun WearFlashPage(operation: FlashOperation, onBack: () -> Unit, onInstalled: () -> Unit) {
    val viewModel = koinViewModel<FlashViewModel>()
    val isUriAccessible = koinInject<IsModuleUriAccessibleUseCase>()
    val takeUriPermission = koinInject<TakeModuleUriPermissionUseCase>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fileError by rememberSaveable { mutableStateOf(false) }
    // FlashUiAction.Start restarts a running operation, so the page sends it once, as the phone does.
    var started by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (started) return@LaunchedEffect
        started = true
        val uri = (operation as? FlashOperation.Module)?.uri
        val accessible = uri == null || withContext(Dispatchers.IO) {
            runCatching {
                isUriAccessible(uri).also { if (it) takeUriPermission(uri) }
            }.getOrDefault(false)
        }
        if (accessible) viewModel.dispatch(FlashUiAction.Start(operation))
        else fileError = true
    }
    LaunchedEffect(state.exitCode) { if (state.exitCode == 0) onInstalled() }

    val failed = fileError || state.exitCode.let { it != null && it != 0 }
    val status = when {
        failed -> WearFlashStatus.FAILED
        state.exitCode == 0 -> WearFlashStatus.SUCCESS
        else -> WearFlashStatus.RUNNING
    }
    val lines = remember(state.output) { state.output.toLogLines() }
    val listState = rememberTransformingLazyColumnState()
    WearFollowLog(listState, lines.size, following = status == WearFlashStatus.RUNNING)

    WearList(onBack = onBack, listState = listState) { spec ->
        item {
            WearPageHeader(spec, stringResource(when (operation) {
                FlashOperation.Uninstall -> R.string.settings_uninstall_permanent
                FlashOperation.Restore -> R.string.settings_restore_stock_image
                is FlashOperation.Boot, is FlashOperation.Module -> R.string.install
            }))
        }
        item {
            WearFlashStatusChip(spec, status, stringResource(when {
                fileError -> R.string.wear_module_file_unreadable
                status == WearFlashStatus.FAILED -> R.string.flash_failed
                status == WearFlashStatus.SUCCESS -> R.string.flash_success
                else -> R.string.flashing
            }))
        }
        if (status == WearFlashStatus.RUNNING) item { WearFlashProgress(spec) }
        if (status == WearFlashStatus.SUCCESS && state.showReboot) item {
            WearActionButton(spec, Icons.TwoTone.PowerSettingsNew, stringResource(R.string.reboot), {
                viewModel.dispatch(FlashUiAction.Reboot(allowSoftReboot = operation is FlashOperation.Module))
            }, colors = ButtonDefaults.buttonColors())
        }
        if (!fileError) wearLogLines(spec, lines)
    }
}
