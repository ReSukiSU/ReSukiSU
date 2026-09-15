package com.resukisu.resukisu.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Input
import androidx.compose.material.icons.twotone.AutoFixHigh
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.LkmSelection
import com.resukisu.resukisu.ui.component.DialogHandle
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.rememberCustomDialog
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsChooseDialog
import com.resukisu.resukisu.ui.component.settings.SettingsChooseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.screen.kernelFlash.component.SlotSelectionDialog
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.adaptiveScaffoldWindowInsets
import com.resukisu.resukisu.ui.viewmodel.InstallUiEvent
import com.resukisu.resukisu.ui.viewmodel.InstallViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallScreen(
    preselectedKernelUri: String? = null
) {
    val viewModel = koinViewModel<InstallViewModel>()
    val installState by viewModel.state.collectAsStateWithLifecycle()
    val environment = installState.environment
    val context = LocalContext.current
    var installMethod by remember { mutableStateOf<InstallMethod?>(null) }
    var lkmSelection by remember { mutableStateOf<LkmSelection>(LkmSelection.KmiNone) }
    var showSlotSelectionDialog by remember { mutableStateOf(false) }
    var tempKernelUri by remember { mutableStateOf<Uri?>(null) }

    var advancedOptionsShown by remember { mutableStateOf(false) }
    var allowShell by remember { mutableStateOf(false) }
    var enableAdb by remember { mutableStateOf(false) }
    var forceBackup by remember { mutableStateOf(false) }

    val isGKI = environment.isGki
    val isAbDevice = environment.isAbDevice
    val summary = stringResource(R.string.horizon_kernel_summary)
    val failedReboot = stringResource(R.string.failed_reboot)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is InstallUiEvent.Error -> {
                    val message = event.message.ifBlank { failedReboot }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(preselectedKernelUri, installState.loading, isAbDevice) {
        if (installState.loading) return@LaunchedEffect
        preselectedKernelUri?.let { uriString ->
            try {
                val preselectedUri = uriString.toUri()
                val horizonMethod = InstallMethod.HorizonKernel(
                    uri = preselectedUri,
                    summary = summary
                )
                installMethod = horizonMethod
                tempKernelUri = preselectedUri

                if (isAbDevice) {
                    showSlotSelectionDialog = true
                }
            } catch (_: Exception) {
            }
        }
    }

    var partitionSelectionIndex by remember { mutableIntStateOf(0) }
    var partitionsState by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasCustomSelected by remember { mutableStateOf(false) }
    val navigator = LocalNavigator.current

    val onInstall = {
        installMethod?.let { method ->
            when (method) {
                is InstallMethod.HorizonKernel -> {
                    method.uri?.let { uri ->
                        navigator.push(
                            Route.KernelFlash(
                                kernelUri = uri.toString(),
                                selectedSlot = method.slot
                            )
                        )
                    }
                }

                else -> {
                    val isOta = method is InstallMethod.DirectInstallToInactiveSlot
                    val partitionSelection = partitionsState.getOrNull(partitionSelectionIndex)
                    navigator.push(
                        Route.Flash.boot(
                            bootUri = if (method is InstallMethod.SelectFile) {
                                method.uri?.toString()
                            } else {
                                null
                            },
                            lkmUri = (lkmSelection as? LkmSelection.LkmUri)?.uri,
                            kmi = (lkmSelection as? LkmSelection.KmiString)?.value,
                            ota = isOta,
                            partition = partitionSelection,
                            allowShell = allowShell,
                            enableAdb = enableAdb,
                            forceBackup = forceBackup,
                        )
                    )
                }
            }
        }
        Unit
    }

    SlotSelectionDialog(
        show = showSlotSelectionDialog && isAbDevice,
        currentSlot = environment.activeSlotSuffix.removePrefix("_")
            .takeIf { it == "a" || it == "b" },
        onDismiss = { showSlotSelectionDialog = false },
        onSlotSelected = { slot ->
            showSlotSelectionDialog = false
            val horizonMethod = InstallMethod.HorizonKernel(
                uri = tempKernelUri,
                slot = slot,
                summary = summary
            )
            installMethod = horizonMethod
        }
    )

    val currentKmi = environment.currentKmi

    val selectKmiDialog = rememberSelectKmiDialog(environment.supportedKmis) { kmi ->
        kmi?.let {
            lkmSelection = LkmSelection.KmiString(it)
            onInstall()
        }
    }

    val onClickNext = {
        if (isGKI && lkmSelection == LkmSelection.KmiNone && currentKmi.isBlank() && installMethod !is InstallMethod.HorizonKernel) {
            selectKmiDialog.show()
        } else {
            onInstall()
        }
    }

    val installOnlySupportKoFile = stringResource(R.string.install_only_support_ko_file)
    val selectLkmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val isKo = isKoFile(context, uri)
                if (isKo) {
                    lkmSelection = LkmSelection.LkmUri(uri.toString())
                } else {
                    lkmSelection = LkmSelection.KmiNone
                    Toast.makeText(
                        context,
                        installOnlySupportKoFile,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val onLkmUpload = {
        selectLkmLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/octet-stream"
        })
    }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            TopBar(
                onBack = { navigator.pop() },
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource()
                .padding(top = 12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            }

            item {
                if (installState.loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                } else {
                    SelectInstallMethod(
                        isGKI = isGKI,
                        rootAvailable = environment.rootAvailable,
                        isAbDevice = environment.isAbDevice,
                        defaultPartitionName = environment.defaultPartition,
                        onSelected = { method ->
                            if (method is InstallMethod.HorizonKernel && method.uri != null) {
                                if (isAbDevice) {
                                    tempKernelUri = method.uri
                                    showSlotSelectionDialog = true
                                } else {
                                    installMethod = method
                                }
                            } else {
                                installMethod = method
                            }
                        },
                        selectedMethod = installMethod,
                    )
                }
            }

            if (!installState.loading) item {
                AnimatedVisibility(
                    visible = installMethod is InstallMethod.DirectInstall || installMethod is InstallMethod.DirectInstallToInactiveSlot,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val isOta = installMethod is InstallMethod.DirectInstallToInactiveSlot
                    val suffix = if (isOta) {
                        environment.inactiveSlotSuffix
                    } else {
                        environment.activeSlotSuffix
                    }
                    val partitions = environment.availablePartitions
                    val defaultPartition = environment.defaultPartition

                    partitionsState = partitions
                    val displayPartitions = partitions.map { name ->
                        if (defaultPartition == name) "$name (default)" else name
                    }

                    val defaultIndex =
                        partitions.indexOf(defaultPartition).takeIf { it >= 0 } ?: 0
                    if (!hasCustomSelected) partitionSelectionIndex = defaultIndex

                    if (displayPartitions.isNotEmpty()) {
                        SegmentedColumn {
                            item {
                                SettingsChooseWidget(
                                    icon = Icons.TwoTone.AutoFixHigh,
                                    items = displayPartitions,
                                    selectedIndex = partitionSelectionIndex,
                                    title = "${stringResource(R.string.install_select_partition)} (${suffix})",
                                    onSelectedIndexChange = { index ->
                                        hasCustomSelected = true
                                        partitionSelectionIndex = index
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (!installState.loading) item {
                SegmentedColumn {
                    val isGki = isGKI
                    val canForceBackup = installMethod is InstallMethod.SelectFile

                    if (isGki) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.AutoMirrored.TwoTone.Input,
                                title = stringResource(id = R.string.install_upload_lkm_file),
                                description = (lkmSelection as? LkmSelection.LkmUri)?.let {
                                    stringResource(
                                        id = R.string.selected_lkm,
                                        it.uri.toUri().lastPathSegment ?: "(file)"
                                    )
                                },
                                onClick = { onLkmUpload() },
                            )
                        }
                    }

                    if (canForceBackup) {
                        item {
                            SettingsSwitchWidget(
                                title = stringResource(id = R.string.install_force_backup),
                                description = stringResource(id = R.string.install_force_backup_summary),
                                checked = forceBackup,
                                onCheckedChange = { forceBackup = it },
                            )
                        }
                    }

                    (installMethod as? InstallMethod.HorizonKernel)?.slot?.let { slot ->
                        item {
                            SettingsBaseWidget(
                                title = stringResource(
                                    id = R.string.selected_slot,
                                    if (slot == "a") stringResource(id = R.string.slot_a)
                                    else stringResource(id = R.string.slot_b)
                                ),
                                onClick = null,
                            )
                        }
                    }
                }
            }

            if (!installState.loading) item {
                SegmentedColumn {
                    expandableItem(
                        expanded = advancedOptionsShown,
                        topContent = {
                            val rotation by animateFloatAsState(
                                targetValue = if (advancedOptionsShown) 180f else 0f,
                                label = "ExpandRotation"
                            )
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Settings,
                                title = stringResource(R.string.advanced_options),
                                onClick = { advancedOptionsShown = !advancedOptionsShown },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.TwoTone.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                                    )
                                },
                            )
                        },
                        bottomContent = {
                            item {
                                SettingsSwitchWidget(
                                    title = stringResource(id = R.string.allow_shell),
                                    description = stringResource(id = R.string.allow_shell_summary),
                                    checked = allowShell,
                                    onCheckedChange = { allowShell = it },
                                )
                            }
                            item {
                                SettingsSwitchWidget(
                                    title = stringResource(id = R.string.enable_adb),
                                    description = stringResource(id = R.string.enable_adb_summary),
                                    checked = enableAdb,
                                    onCheckedChange = { enableAdb = it },
                                )
                            }
                        }
                    )
                }
            }

            if (!installState.loading) item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = installMethod != null,
                        onClick = onClickNext,
                    ) {
                        Text(
                            stringResource(id = R.string.install_next),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
            }
        }
    }
}

sealed class InstallMethod {
    data class SelectFile(
        val uri: Uri? = null,
        @param:StringRes override val label: Int = R.string.select_file,
        override val summary: String?
    ) : InstallMethod()

    data object DirectInstall : InstallMethod() {
        override val label: Int
            get() = R.string.direct_install
    }

    data object DirectInstallToInactiveSlot : InstallMethod() {
        override val label: Int
            get() = R.string.install_inactive_slot
    }

    data class HorizonKernel(
        val uri: Uri? = null,
        val slot: String? = null,
        @param:StringRes override val label: Int = R.string.horizon_kernel,
        override val summary: String? = null
    ) : InstallMethod()

    abstract val label: Int
    open val summary: String? = null
}

@Composable
private fun SelectInstallMethod(
    isGKI: Boolean = false,
    rootAvailable: Boolean,
    isAbDevice: Boolean,
    defaultPartitionName: String,
    onSelected: (InstallMethod) -> Unit = {},
    selectedMethod: InstallMethod? = null
) {
    val horizonKernelSummary = stringResource(R.string.horizon_kernel_summary)
    val selectFileTip = stringResource(
        id = R.string.select_file_tip, defaultPartitionName
    )

    val radioOptions = remember(rootAvailable, isAbDevice, selectFileTip, horizonKernelSummary) {
        buildList {
            add(InstallMethod.SelectFile(summary = selectFileTip))
            if (rootAvailable) {
                add(InstallMethod.DirectInstall)
                if (isAbDevice) {
                    add(InstallMethod.DirectInstallToInactiveSlot)
                }
                add(InstallMethod.HorizonKernel(summary = horizonKernelSummary))
            }
        }
    }

    var currentSelectingMethod by remember { mutableStateOf<InstallMethod?>(null) }

    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val option = when (currentSelectingMethod) {
                    is InstallMethod.SelectFile -> InstallMethod.SelectFile(
                        uri,
                        summary = selectFileTip
                    )

                    is InstallMethod.HorizonKernel -> InstallMethod.HorizonKernel(
                        uri,
                        summary = horizonKernelSummary
                    )

                    else -> null
                }
                option?.let { opt ->
                    onSelected(opt)
                }
            }
        }
    }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            onSelected(InstallMethod.DirectInstallToInactiveSlot)
        },
        onDismiss = null
    )

    val dialogTitle = stringResource(id = android.R.string.dialog_alert_title)
    val dialogContent = stringResource(id = R.string.install_inactive_slot_warning)

    val onClick = { option: InstallMethod ->
        currentSelectingMethod = option
        when (option) {
            is InstallMethod.SelectFile, is InstallMethod.HorizonKernel -> {
                selectImageLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf("application/octet-stream", "application/zip")
                    )
                })
            }

            is InstallMethod.DirectInstall -> {
                onSelected(option)
            }

            is InstallMethod.DirectInstallToInactiveSlot -> {
                confirmDialog.showConfirm(dialogTitle, dialogContent)
            }
        }
    }

    SegmentedColumn {
        radioOptions.forEach { option ->
            item(key = option.javaClass) {
                val selected = option.javaClass == selectedMethod?.javaClass
                SettingsBaseWidget(
                    title = stringResource(id = option.label),
                    description = option.summary,
                    selected = selected,
                    onClick = { onClick(option) },
                    leadingContent = {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSelectKmiDialog(
    supportedKmi: List<String>,
    onSelected: (String?) -> Unit,
): DialogHandle {
    return rememberCustomDialog { dismiss ->
        SettingsChooseDialog(
            show = true,
            title = stringResource(R.string.select_kmi),
            items = supportedKmi,
            selectedIndex = -1,
            onDismiss = dismiss,
            onSelectedIndexChange = { index ->
                onSelected(supportedKmi.getOrNull(index))
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior,
) {
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(),
        title = {
            Text(
                stringResource(R.string.install)
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        navigationIcon = {
            AppBackButton(
                onClick = onBack
            )
        },
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}

private fun isKoFile(context: Context, uri: Uri): Boolean {
    val seg = uri.lastPathSegment ?: ""
    if (seg.endsWith(".ko", ignoreCase = true)) return true

    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                name?.endsWith(".ko", ignoreCase = true) == true
            } else {
                false
            }
        } ?: false
    } catch (_: Throwable) {
        false
    }
}

@Preview
@Composable
fun SelectInstallPreview() {
    InstallScreen()
}
