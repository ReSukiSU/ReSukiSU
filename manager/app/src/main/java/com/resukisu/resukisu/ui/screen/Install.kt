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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.twotone.AutoFixHigh
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.FileOpen
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.screen.kernelFlash.component.SlotSelectionDialog
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.adaptiveScaffoldWindowInsets
import com.resukisu.resukisu.ui.viewmodel.InstallUiEvent
import com.resukisu.resukisu.ui.viewmodel.InstallViewModel
import kotlinx.coroutines.launch
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
    var lkmInstallMethod by remember { mutableStateOf<InstallMethod?>(null) }
    var ak3InstallMethod by remember { mutableStateOf<InstallMethod?>(null) }
    var lkmSelection by remember { mutableStateOf<LkmSelection>(LkmSelection.KmiNone) }
    var lkmFileName by remember { mutableStateOf<String?>(null) }
    var showSlotSelectionDialog by remember { mutableStateOf(false) }
    var tempKernelUri by remember { mutableStateOf<Uri?>(null) }

    var advancedOptionsShown by remember { mutableStateOf(false) }
    var allowShell by remember { mutableStateOf(false) }
    var enableAdb by remember { mutableStateOf(false) }
    var forceBackup by remember { mutableStateOf(false) }

    var ak3AdvancedOptionsShown by remember { mutableStateOf(false) }
    var skipKsud by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    val isGKI = environment.isGki
    val isAbDevice = environment.isAbDevice
    val rootAvailable = environment.rootAvailable
    val summary = stringResource(R.string.horizon_kernel_summary)
    val failedReboot = stringResource(R.string.failed_reboot)
    val selectFileTip = stringResource(id = R.string.select_file_tip, environment.defaultPartition)

    LaunchedEffect(isGKI) {
        if (!isGKI) pagerState.scrollToPage(0)
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
                        summary = summary
                    )

                    else -> null
                }
                option?.let { opt ->
                    if (opt is InstallMethod.HorizonKernel && opt.uri != null) {
                        if (isAbDevice) {
                            tempKernelUri = opt.uri
                            showSlotSelectionDialog = true
                        } else {
                            ak3InstallMethod = opt
                        }
                    } else {
                        lkmInstallMethod = opt
                    }
                }
            }
        }
    }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            lkmInstallMethod = InstallMethod.DirectInstallToInactiveSlot
        },
        onDismiss = null
    )

    val dialogTitle = stringResource(id = android.R.string.dialog_alert_title)
    val dialogContent = stringResource(id = R.string.install_inactive_slot_warning)

    val onMethodClick = { option: InstallMethod ->
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
                lkmInstallMethod = option
            }

            is InstallMethod.DirectInstallToInactiveSlot -> {
                confirmDialog.showConfirm(dialogTitle, dialogContent)
            }
        }
    }

    val lkmMethods = remember(rootAvailable, isAbDevice, isGKI, selectFileTip) {
        buildList {
            add(InstallMethod.SelectFile(summary = selectFileTip))
            if (isGKI && rootAvailable) {
                add(InstallMethod.DirectInstall)
                if (isAbDevice) {
                    add(InstallMethod.DirectInstallToInactiveSlot)
                }
            }
        }
    }

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
                ak3InstallMethod = horizonMethod
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
        val current = if (pagerState.currentPage == 0) lkmInstallMethod else ak3InstallMethod
        current?.let { method ->
            when (method) {
                is InstallMethod.HorizonKernel -> {
                    method.uri?.let { uri ->
                        navigator.push(
                            Route.KernelFlash(
                                kernelUri = uri.toString(),
                                selectedSlot = method.slot,
                                skipKsud = skipKsud,
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
            ak3InstallMethod = horizonMethod
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
        val current = if (pagerState.currentPage == 0) lkmInstallMethod else ak3InstallMethod
        if (isGKI && pagerState.currentPage == 0 && lkmSelection == LkmSelection.KmiNone && currentKmi.isBlank() && current !is InstallMethod.HorizonKernel) {
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
                    lkmFileName = uri.toString()
                } else {
                    lkmSelection = LkmSelection.KmiNone
                    lkmFileName = null
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

    val advRotation by animateFloatAsState(
        targetValue = if (advancedOptionsShown) 180f else 0f,
        label = "AdvRotation"
    )
    val ak3AdvRotation by animateFloatAsState(
        targetValue = if (ak3AdvancedOptionsShown) 180f else 0f,
        label = "Ak3AdvRotation"
    )

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
                scrollBehavior = scrollBehavior,
                selectedTab = pagerState.currentPage,
                onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } }
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .blurSource()
        ) { page ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(top = 12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
                }

            if (installState.loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            } else {
                item {
                    if (page == 0) {
                        val isOta = lkmInstallMethod is InstallMethod.DirectInstallToInactiveSlot
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

                        val canSelectPartition =
                            lkmInstallMethod is InstallMethod.DirectInstall || lkmInstallMethod is InstallMethod.DirectInstallToInactiveSlot

                        SegmentedColumn {
                            lkmMethods.forEach { method ->
                                val selected = method.javaClass == lkmInstallMethod?.javaClass
                                item(key = method.javaClass) {
                                    SettingsBaseWidget(
                                        title = stringResource(id = method.label),
                                        description = method.summary,
                                        selected = selected,
                                        onClick = { onMethodClick(method) },
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

                        SegmentedColumn {
                            expandableItem(
                                expanded = advancedOptionsShown,
                                topContent = {
                                    SettingsBaseWidget(
                                        icon = Icons.TwoTone.Settings,
                                        title = stringResource(R.string.advanced_options),
                                        onClick = { advancedOptionsShown = !advancedOptionsShown },
                                        trailingContent = {
                                            Icon(
                                                imageVector = Icons.TwoTone.ExpandMore,
                                                contentDescription = null,
                                                modifier = Modifier.graphicsLayer { rotationZ = advRotation }
                                            )
                                        },
                                    )
                                },
                                bottomContent = {
                                    item {
                                        val hasLkmUri = lkmSelection is LkmSelection.LkmUri
                                        if (canSelectPartition && displayPartitions.isNotEmpty()) {
                                            SettingsChooseWidget(
                                                icon = Icons.TwoTone.AutoFixHigh,
                                                items = displayPartitions,
                                                selectedIndex = partitionSelectionIndex,
                                                title = "${stringResource(R.string.install_select_partition)} ($suffix)",
                                                onSelectedIndexChange = { index ->
                                                    hasCustomSelected = true
                                                    partitionSelectionIndex = index
                                                },
                                            )
                                        }

                                        SettingsBaseWidget(
                                            icon = Icons.TwoTone.FileOpen,
                                            title = stringResource(id = R.string.install_upload_lkm_file),
                                            description = if (!hasLkmUri) stringResource(id = R.string.install_upload_lkm_file_summary) else null,
                                            selected = hasLkmUri,
                                            onClick = {
                                                if (hasLkmUri) {
                                                    lkmSelection = LkmSelection.KmiNone
                                                    lkmFileName = null
                                                } else {
                                                    onLkmUpload()
                                                }
                                            },
                                            descriptionColumnContent = if (hasLkmUri) {
                                                {
                                                    Text(
                                                        text = lkmFileName ?: "",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                }
                                            } else null,
                                        )
                                    }
                                    item {
                                        SettingsBaseWidget(
                                            iconPlaceholder = false,
                                            title = stringResource(id = R.string.allow_shell),
                                            description = stringResource(id = R.string.allow_shell_summary),
                                            onClick = { allowShell = !allowShell },
                                            leadingContent = {
                                                Checkbox(
                                                    checked = allowShell,
                                                    onCheckedChange = null,
                                                )
                                            },
                                        )
                                    }
                                    item {
                                        SettingsBaseWidget(
                                            iconPlaceholder = false,
                                            title = stringResource(id = R.string.enable_adb),
                                            description = stringResource(id = R.string.enable_adb_summary),
                                            onClick = { enableAdb = !enableAdb },
                                            leadingContent = {
                                                Checkbox(
                                                    checked = enableAdb,
                                                    onCheckedChange = null,
                                                )
                                            },
                                        )
                                    }
                                    if (lkmInstallMethod is InstallMethod.SelectFile) {
                                        item {
                                            SettingsBaseWidget(
                                                iconPlaceholder = false,
                                                title = stringResource(id = R.string.install_force_backup),
                                                description = stringResource(id = R.string.install_force_backup_summary),
                                                onClick = { forceBackup = !forceBackup },
                                                leadingContent = {
                                                    Checkbox(
                                                        checked = forceBackup,
                                                        onCheckedChange = null,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    } else {
                        if (rootAvailable) {
                            val horizonSelected =
                                ak3InstallMethod is InstallMethod.HorizonKernel
                            SegmentedColumn {
                                item {
                                    SettingsBaseWidget(
                                        title = stringResource(R.string.GKI_install_methods),
                                        description = stringResource(R.string.ak3_select_zip),
                                        icon = Icons.TwoTone.FileUpload,
                                        selected = horizonSelected,
                                        onClick = {
                                            onMethodClick(
                                                InstallMethod.HorizonKernel(summary = summary)
                                            )
                                        },
                                    )
                                }
                                (ak3InstallMethod as? InstallMethod.HorizonKernel)?.slot?.let { slot ->
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

                            SegmentedColumn {
                                expandableItem(
                                    expanded = ak3AdvancedOptionsShown,
                                    topContent = {
                                        SettingsBaseWidget(
                                            icon = Icons.TwoTone.Settings,
                                            title = stringResource(R.string.advanced_options),
                                            onClick = { ak3AdvancedOptionsShown = !ak3AdvancedOptionsShown },
                                            trailingContent = {
                                                Icon(
                                                    imageVector = Icons.TwoTone.ExpandMore,
                                                    contentDescription = null,
                                                    modifier = Modifier.graphicsLayer { rotationZ = ak3AdvRotation }
                                                )
                                            },
                                        )
                                    },
                                    bottomContent = {
                                        item {
                                            SettingsBaseWidget(
                                                iconPlaceholder = false,
                                                title = stringResource(id = R.string.skip_ksud),
                                                description = stringResource(id = R.string.skip_ksud_summary),
                                                onClick = { skipKsud = !skipKsud },
                                                leadingContent = {
                                                    Checkbox(
                                                        checked = skipKsud,
                                                        onCheckedChange = null,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (!installState.loading) item {
                val currentMethod = if (pagerState.currentPage == 0) lkmInstallMethod else ak3InstallMethod
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = currentMethod != null,
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
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Column(modifier = Modifier.blurEffect()) {
        LargeFlexibleTopAppBar(
            title = {
                Text(stringResource(R.string.install))
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

        PrimaryTabRow(
            selectedTabIndex = selectedTab.coerceAtMost(1),
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text(stringResource(R.string.Lkm_install_methods)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(stringResource(R.string.GKI_install_methods)) }
            )
        }
    }
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
