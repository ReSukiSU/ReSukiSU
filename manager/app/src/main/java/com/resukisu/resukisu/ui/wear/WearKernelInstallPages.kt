package com.resukisu.resukisu.ui.wear

import android.net.Uri
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowForward
import androidx.compose.material.icons.twotone.AutoFixHigh
import androidx.compose.material.icons.twotone.FileOpen
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.InstallEnvironment
import com.resukisu.resukisu.ui.wear.component.settings.WearChoicePage
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.wear.component.WearActionButton
import com.resukisu.resukisu.ui.wear.component.WearFlashProgress
import com.resukisu.resukisu.ui.wear.component.WearFlashStatus
import com.resukisu.resukisu.ui.wear.component.WearFlashStatusChip
import com.resukisu.resukisu.ui.wear.component.WearFollowLog
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearSectionHeader
import com.resukisu.resukisu.ui.wear.component.WearStatusItem
import com.resukisu.resukisu.ui.wear.component.WearStatusTone
import com.resukisu.resukisu.ui.wear.component.WearSubPage
import com.resukisu.resukisu.ui.wear.component.rememberWearConfirmDialog
import com.resukisu.resukisu.ui.wear.component.wearGroupGap
import com.resukisu.resukisu.ui.wear.component.wearLogLines
import com.resukisu.resukisu.ui.viewmodel.InstallViewModel
import com.resukisu.resukisu.ui.viewmodel.KernelFlashUiAction
import com.resukisu.resukisu.ui.viewmodel.KernelFlashUiEvent
import com.resukisu.resukisu.ui.viewmodel.KernelFlashViewModel
import org.koin.compose.viewmodel.koinViewModel

/** The phone's LKM install methods: patch a selected image, patch the current slot, or the inactive slot after OTA. */
private enum class LkmMethod { SELECT_FILE, DIRECT, INACTIVE_SLOT }

/** The phone Install screen's two tabs become two entries: LKM patching and AnyKernel3 flashing. */
@Composable
internal fun WearInstallPage(onBack: () -> Unit, onOpen: (WearRoute) -> Unit) {
    val installState by koinViewModel<InstallViewModel>().state.collectAsStateWithLifecycle()
    val environment = installState.environment
    WearList(isLoading = installState.loading, onBack = onBack, snap = true) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.install)) }
        item {
            WearSettingsJumpPageWidget(spec, stringResource(R.string.Lkm_install_methods), { onOpen(WearRoute.LkmInstall) },
                icon = Icons.TwoTone.Memory,
                description = stringResource(R.string.select_file_tip, environment.defaultPartition))
        }
        // AnyKernel3 flashing needs root, as on the phone.
        item {
            WearSettingsJumpPageWidget(spec, stringResource(R.string.GKI_install_methods), { onOpen(WearRoute.Ak3Install) },
                icon = Icons.TwoTone.FileUpload, enabled = environment.rootAvailable,
                description = stringResource(if (environment.rootAvailable) R.string.ak3_select_zip else R.string.root_required))
        }
    }
}

/**
 * LKM install: the method as radio buttons, then the phone's advanced options, then "Next" as the
 * screen's single high-emphasis button. A GKI kernel without a known KMI asks for one first.
 */
@Composable
internal fun WearLkmInstallPage(onBack: () -> Unit, onOpen: (WearRoute) -> Unit) {
    val installState by koinViewModel<InstallViewModel>().state.collectAsStateWithLifecycle()
    val environment = installState.environment
    var method by rememberSaveable { mutableStateOf<LkmMethod?>(null) }
    var bootUri by rememberSaveable { mutableStateOf<String?>(null) }
    var lkmUri by rememberSaveable { mutableStateOf<String?>(null) }
    var lkmRejected by rememberSaveable { mutableStateOf(false) }
    var partition by rememberSaveable { mutableStateOf<String?>(null) }
    var allowShell by rememberSaveable { mutableStateOf(false) }
    var enableAdb by rememberSaveable { mutableStateOf(false) }
    var forceBackup by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf<String?>(null) }

    val selectBoot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            method = LkmMethod.SELECT_FILE
            bootUri = uri.toString()
        }
    }
    // Like the phone, only a `.ko` file is accepted as a local LKM.
    val selectLkm = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val accepted = uri.fileName().endsWith(".ko", ignoreCase = true)
            lkmUri = uri.toString().takeIf { accepted }
            lkmRejected = !accepted
        }
    }
    val methods = buildList {
        add(LkmMethod.SELECT_FILE)
        if (environment.isGki && environment.rootAvailable) {
            add(LkmMethod.DIRECT)
            if (environment.isAbDevice) add(LkmMethod.INACTIVE_SLOT)
        }
    }
    val inactiveSlotDialog = rememberWearConfirmDialog(stringResource(android.R.string.dialog_alert_title),
        stringResource(R.string.install_inactive_slot_warning)) { method = LkmMethod.INACTIVE_SLOT }
    val canSelectPartition = method == LkmMethod.DIRECT || method == LkmMethod.INACTIVE_SLOT
    val suffix = if (method == LkmMethod.INACTIVE_SLOT) environment.inactiveSlotSuffix else environment.activeSlotSuffix
    val shownPartition = partition ?: defaultPartition(environment)
    val ready = method != null && (method != LkmMethod.SELECT_FILE || bootUri != null)
    fun flash(kmi: String?) = onOpen(WearRoute.BootFlash(
        bootUri = if (method == LkmMethod.SELECT_FILE) bootUri else null,
        lkmUri = lkmUri,
        kmi = kmi,
        ota = method == LkmMethod.INACTIVE_SLOT,
        partition = shownPartition,
        allowShell = allowShell,
        enableAdb = enableAdb,
        forceBackup = method == LkmMethod.SELECT_FILE && forceBackup,
    ))

    when (choosing) {
        "partition" -> WearSubPage({ choosing = null }) {
            WearChoicePage(stringResource(R.string.install_select_partition),
                environment.availablePartitions.map { it to it }, shownPartition.orEmpty(), { choosing = null }) {
                partition = it
                choosing = null
            }
        }
        "kmi" -> WearSubPage({ choosing = null }) {
            WearChoicePage(stringResource(R.string.select_kmi), environment.supportedKmis.map { it to it }, "",
                { choosing = null }) {
                choosing = null
                flash(it)
            }
        }
        else -> WearList(onBack = onBack, snap = true) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.Lkm_install_methods)) }
            methods.forEach { option ->
                item(key = option) {
                    RadioButton(
                        selected = method == option,
                        onSelect = {
                            when (option) {
                                LkmMethod.SELECT_FILE -> selectBoot.launch(arrayOf("application/octet-stream"))
                                LkmMethod.DIRECT -> method = option
                                LkmMethod.INACTIVE_SLOT -> inactiveSlotDialog.show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                        transformation = SurfaceTransformation(spec),
                        secondaryLabel = if (option == LkmMethod.SELECT_FILE) {
                            {
                                Text(bootUri?.toUri()?.fileName()
                                    ?: stringResource(R.string.select_file_tip, environment.defaultPartition),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        } else null,
                        label = {
                            Text(stringResource(when (option) {
                                LkmMethod.SELECT_FILE -> R.string.select_file
                                LkmMethod.DIRECT -> R.string.direct_install
                                LkmMethod.INACTIVE_SLOT -> R.string.install_inactive_slot
                            }), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        },
                    )
                }
            }
            item { WearSectionHeader(spec, stringResource(R.string.advanced_options)) }
            if (canSelectPartition && environment.availablePartitions.isNotEmpty()) item {
                WearSettingsJumpPageWidget(spec, stringResource(R.string.install_select_partition), { choosing = "partition" },
                    icon = Icons.TwoTone.AutoFixHigh, description = "$shownPartition ($suffix)")
            }
            item {
                WearSettingsSwitchWidget(spec, stringResource(R.string.install_upload_lkm_file), lkmUri != null,
                    { if (it) selectLkm.launch(arrayOf("application/octet-stream")) else lkmUri = null },
                    icon = Icons.TwoTone.FileOpen,
                    secondaryLabel = lkmUri?.toUri()?.fileName()
                        ?: stringResource(R.string.install_upload_lkm_file_summary))
            }
            if (lkmRejected) item {
                WearStatusItem(spec, Icons.TwoTone.Warning, stringResource(R.string.install_only_support_ko_file),
                    tone = WearStatusTone.ERROR)
            }
            item {
                WearSettingsSwitchWidget(spec, stringResource(R.string.allow_shell), allowShell, { allowShell = it },
                    secondaryLabel = stringResource(R.string.allow_shell_summary))
            }
            item {
                WearSettingsSwitchWidget(spec, stringResource(R.string.enable_adb), enableAdb, { enableAdb = it },
                    secondaryLabel = stringResource(R.string.enable_adb_summary))
            }
            if (method == LkmMethod.SELECT_FILE) item {
                WearSettingsSwitchWidget(spec, stringResource(R.string.install_force_backup), forceBackup, { forceBackup = it },
                    secondaryLabel = stringResource(R.string.install_force_backup_summary))
            }
            wearGroupGap("next-gap")
            item {
                WearActionButton(spec, Icons.AutoMirrored.TwoTone.ArrowForward, stringResource(R.string.install_next), {
                    if (environment.isGki && lkmUri == null && environment.currentKmi.isBlank()) choosing = "kmi"
                    else flash(null)
                }, enabled = ready, colors = ButtonDefaults.buttonColors())
            }
        }
    }
}

/** The phone preselects the default partition, or the first available one. */
private fun defaultPartition(environment: InstallEnvironment): String? =
    environment.availablePartitions.let { it.getOrNull(it.indexOf(environment.defaultPartition).coerceAtLeast(0)) }

/** The file name of a picked document, as far as its URI shows it. */
private fun Uri.fileName(): String = lastPathSegment?.substringAfterLast('/') ?: toString()

/** AnyKernel3: pick the ZIP, choose the slot on A/B devices, then flash. */
@Composable
internal fun WearAk3InstallPage(onBack: () -> Unit, onOpen: (WearRoute) -> Unit) {
    val installState by koinViewModel<InstallViewModel>().state.collectAsStateWithLifecycle()
    val environment = installState.environment
    var zipUri by rememberSaveable { mutableStateOf<String?>(null) }
    var slot by rememberSaveable { mutableStateOf<String?>(null) }
    var skipKsud by rememberSaveable { mutableStateOf(false) }
    val selectZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) zipUri = uri.toString()
    }
    val activeSlot = environment.activeSlotSuffix.removePrefix("_").takeIf { it == "a" || it == "b" }
    LaunchedEffect(environment.isAbDevice, activeSlot) {
        if (environment.isAbDevice && slot == null) slot = activeSlot
    }
    val ready = zipUri != null && (!environment.isAbDevice || slot != null)
    WearList(onBack = onBack, snap = true) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.GKI_install_methods)) }
        item {
            WearSettingsJumpPageWidget(spec, stringResource(R.string.horizon_kernel),
                { selectZip.launch(arrayOf("application/zip", "application/octet-stream")) },
                icon = Icons.TwoTone.FileUpload,
                description = zipUri?.toUri()?.fileName() ?: stringResource(R.string.ak3_select_zip))
        }
        if (environment.isAbDevice) {
            item {
                WearSectionHeader(spec, stringResource(R.string.selected_slot,
                    stringResource(if (slot == "b") R.string.slot_b else R.string.slot_a)))
            }
            listOf("a" to R.string.slot_a, "b" to R.string.slot_b).forEach { (option, label) ->
                item(key = "slot-$option") {
                    RadioButton(
                        selected = slot == option,
                        onSelect = { slot = option },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                        transformation = SurfaceTransformation(spec),
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
        item { WearSectionHeader(spec, stringResource(R.string.advanced_options)) }
        item {
            WearSettingsSwitchWidget(spec, stringResource(R.string.skip_ksud), skipKsud, { skipKsud = it },
                secondaryLabel = stringResource(R.string.skip_ksud_summary))
        }
        wearGroupGap("next-gap")
        item {
            WearActionButton(spec, Icons.AutoMirrored.TwoTone.ArrowForward, stringResource(R.string.install_next), {
                zipUri?.let { onOpen(WearRoute.KernelFlash(it, slot, skipKsud)) }
            }, enabled = ready, colors = ButtonDefaults.buttonColors())
        }
    }
}

/**
 * AnyKernel3 flashing with the phone's KernelFlashViewModel: status, step progress, the log as
 * monospace lines following the newest output, and reboot once complete.
 */
@Composable
internal fun WearKernelFlashPage(uri: String, slot: String?, skipKsud: Boolean, onBack: () -> Unit) {
    val viewModel = koinViewModel<KernelFlashViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val flash = state.flash
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // Starting again would restart the flash, so the page starts it once, as the phone does.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            viewModel.dispatch(KernelFlashUiAction.Start(uri, slot, skipKsud))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> if (event is KernelFlashUiEvent.Error) error = event.message }
    }
    LaunchedEffect(flash.isCompleted, state.autoExit) {
        if (state.requestUri == uri && state.selectedSlot == slot && flash.isCompleted && state.autoExit) {
            viewModel.dispatch(KernelFlashUiAction.ConsumeAutoExit)
            onBack()
        }
    }
    val status = when {
        flash.error.isNotEmpty() -> WearFlashStatus.FAILED
        flash.isCompleted -> WearFlashStatus.SUCCESS
        else -> WearFlashStatus.RUNNING
    }
    val listState = rememberTransformingLazyColumnState()
    WearFollowLog(listState, flash.logs.size, following = status == WearFlashStatus.RUNNING)
    WearList(onBack = onBack, listState = listState) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.horizon_kernel)) }
        item {
            WearFlashStatusChip(spec, status, stringResource(when (status) {
                WearFlashStatus.RUNNING -> R.string.flashing
                WearFlashStatus.SUCCESS -> R.string.horizon_flash_complete
                WearFlashStatus.FAILED -> R.string.flash_failed
            }), detail = if (status == WearFlashStatus.FAILED) flash.error else flash.currentStep)
        }
        if (status == WearFlashStatus.RUNNING) item { WearFlashProgress(spec, flash.progress.takeIf { it > 0f }) }
        if (status == WearFlashStatus.SUCCESS) item {
            WearActionButton(spec, Icons.TwoTone.PowerSettingsNew, stringResource(R.string.reboot),
                { viewModel.dispatch(KernelFlashUiAction.Reboot) }, colors = ButtonDefaults.buttonColors())
        }
        error?.let { message ->
            item {
                WearStatusItem(spec, Icons.TwoTone.Warning, message.ifBlank { stringResource(R.string.failed_reboot) },
                    tone = WearStatusTone.ERROR)
            }
        }
        // The watch shows only the latest lines; the full log stays in the shared flash state.
        wearLogLines(spec, flash.logs.takeLast(WEAR_FLASH_LOG_LINES))
        if (flash.logs.size > WEAR_FLASH_LOG_LINES) item { WearSectionHeader(spec, stringResource(R.string.wear_log_tail)) }
    }
}

private const val WEAR_FLASH_LOG_LINES = 256
