package com.resukisu.resukisu.ui.screen.main

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maxkeppeker.sheets.core.models.base.IconSource
import com.maxkeppeler.sheets.list.models.ListOption
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.DialogHandle
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.ksuIsValid
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.rememberCustomDialog
import com.resukisu.resukisu.ui.component.rememberLoadingDialog
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsDropdownWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.screen.FlashIt
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.getBugreportFile
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */
private val SPACING_MEDIUM = 8.dp
private val SPACING_LARGE = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(bottomPadding: Dp) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val settingsViewModel = viewModel<SettingsViewModel>(viewModelStoreOwner = ksuApp)
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        settingsViewModel.loadFeatureSettings(context)
    }

    Scaffold(
        topBar = {
            TopBar(scrollBehavior = scrollBehavior)
        },
        snackbarHost = {
            SwipeableSnackbarHost(
                modifier = Modifier.padding(bottom = bottomPadding),
                hostState = snackBarHost
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val loadingDialog = rememberLoadingDialog()
        var showBottomsheet by remember { mutableStateOf(false) }
        val logSaved = stringResource(R.string.log_saved)
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val exportBugreportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/gzip")
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                loadingDialog.show()
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    getBugreportFile(context).inputStream().use {
                        it.copyTo(output)
                    }
                }
                loadingDialog.hide()
                snackBarHost.showSnackbar(logSaved)
            }
        }

        LazyColumn(
            modifier =
                Modifier
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .blurSource(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 5.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomPadding + 15.dp
            )
        ) {
            // 配置卡片
            if (ksuIsValid()) {
                item {
                    val modeItems = listOf(
                        stringResource(id = R.string.settings_mode_default),
                        stringResource(id = R.string.settings_mode_disable_until_reboot),
                        stringResource(id = R.string.settings_mode_disable_always),
                    )

                    SegmentedColumn(
                        title = stringResource(R.string.configuration),
                        content = {
                            item {
                                // 配置文件模板入口
                                SettingsJumpPageWidget(
                                    icon = Icons.Filled.Fence,
                                    title = stringResource(R.string.settings_profile_template),
                                    description = stringResource(R.string.settings_profile_template_summary),
                                    onClick = {
                                        navigator.push(Route.AppProfileTemplate)
                                    }
                                )
                            }

                            item {
                                val suSummary = when (uiState.suStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_sucompat_summary)
                                }
                                SettingsDropdownWidget(
                                    icon = Icons.Rounded.RemoveModerator,
                                    title = stringResource(id = R.string.settings_sucompat),
                                    description = suSummary,
                                    items = modeItems,
                                    enabled = uiState.suStatus == "supported",
                                    selectedIndex = uiState.suCompatMode,
                                    onSelectedIndexChange = { index ->
                                        settingsViewModel.handleSuCompatModeChange(context, index)
                                    },
                                )
                            }

                            item {
                                val umountSummary = when (uiState.kernelUmountStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_kernel_umount_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.Rounded.RemoveCircle,
                                    title = stringResource(id = R.string.settings_kernel_umount),
                                    description = umountSummary,
                                    enabled = uiState.kernelUmountStatus == "supported",
                                    checked = uiState.isKernelUmountEnabled,
                                    onCheckedChange = settingsViewModel::handleKernelUmountChange,
                                )
                            }

                            item(
                                visible = Natives.isLateLoadMode
                            ) {
                                SettingsSwitchWidget(
                                    icon = Icons.Rounded.ElectricalServices,
                                    title = stringResource(id = R.string.settings_auto_jailbreak),
                                    description = stringResource(id = R.string.settings_auto_jailbreak_summary),
                                    checked = uiState.autoJailbreakEnabled,
                                    onCheckedChange = { value ->
                                        settingsViewModel.handleAutoJailbreakChange(context, value)
                                    }
                                )
                            }

                            item(
                                visible = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
                            ) {
                                val adbRootSummary = when (uiState.adbRootStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_adb_root_summary)
                                }

                                SettingsSwitchWidget(
                                    icon = Icons.Filled.Adb,
                                    title = stringResource(id = R.string.settings_adb_root),
                                    description = adbRootSummary,
                                    checked = uiState.isAdbRootEnabled,
                                    enabled = uiState.adbRootStatus == "supported",
                                    onCheckedChange = settingsViewModel::handleAdbRootChange,
                                )
                            }


                            item {
                                val sulogSummary = when (uiState.sulogStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_sulog_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.AutoMirrored.Rounded.Article,
                                    title = stringResource(id = R.string.settings_sulog),
                                    description = sulogSummary,
                                    enabled = uiState.sulogStatus == "supported",
                                    checked = uiState.isSuLogEnabled,
                                    onCheckedChange = settingsViewModel::handleSuLogChange,
                                )
                            }


                            item {
                                val selinuxHideSummary = when (uiState.selinuxHideStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_selinux_hide_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.Filled.Policy,
                                    title = stringResource(id = R.string.settings_selinux_hide),
                                    description = selinuxHideSummary,
                                    enabled = uiState.selinuxHideStatus == "supported",
                                    checked = uiState.isSelinuxHideEnabled,
                                    onCheckedChange = { checked ->
                                        settingsViewModel.handleSelinuxHideChange(context, checked)
                                    },
                                )
                            }

                            item {
                                // 卸载模块开关
                                SettingsSwitchWidget(
                                    icon = Icons.Rounded.FolderDelete,
                                    title = stringResource(id = R.string.settings_umount_modules_default),
                                    description = stringResource(id = R.string.settings_umount_modules_default_summary),
                                    checked = uiState.defaultUmountModules,
                                    onCheckedChange = settingsViewModel::handleDefaultUmountModulesChange,
                                )
                            }
                        }
                    )
                }
            }

            item {
                // 应用设置卡片
                SegmentedColumn(
                    title = stringResource(R.string.app_settings),
                    content = {
                        item {
                            // 更新检查开关
                            SettingsSwitchWidget(
                                icon = Icons.Filled.Update,
                                title = stringResource(R.string.settings_check_update),
                                description = stringResource(R.string.settings_check_update_summary),
                                checked = uiState.checkUpdate,
                                onCheckedChange = { enabled ->
                                    settingsViewModel.handleCheckUpdateChange(context, enabled)
                                }
                            )
                        }

                        item {
                            // 更多设置
                            SettingsJumpPageWidget(
                                icon = Icons.Filled.Settings,
                                title = stringResource(R.string.theme_settings),
                                description = stringResource(R.string.theme_settings),
                                onClick = {
                                    navigator.push(Route.ThemeSettings)
                                }
                            )
                        }
                    }
                )
            }

            item {
                // 工具卡片
                SegmentedColumn(
                    title = stringResource(R.string.tools),
                    content = {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.Filled.BugReport,
                                title = stringResource(R.string.send_log),
                                onClick = {
                                    showBottomsheet = true
                                }
                            ) {}
                        }

                        if (ksuIsValid()) {
                            item(visible = uiState.isKernelUmountEnabled) {
                                SettingsJumpPageWidget(
                                    icon = Icons.Filled.FolderOff,
                                    title = stringResource(R.string.umount_path_manager),
                                    description = stringResource(R.string.umount_path_manager_summary),
                                    onClick = {
                                        navigator.push(Route.UmountManager)
                                    }
                                )
                            }
                        }

                        if (Natives.isLkmMode) {
                            item {
                                UninstallItem {
                                    loadingDialog.withLoading(it)
                                }
                            }
                        }
                    }
                )
            }

            if (showBottomsheet) {
                item {
                    val sendLog = stringResource(R.string.send_log)
                    LogBottomSheet(
                        onDismiss = { showBottomsheet = false },
                        onSaveLog = {
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
                            val current = LocalDateTime.now().format(formatter)
                            exportBugreportLauncher.launch("KernelSU_bugreport_${current}.tar.gz")
                            showBottomsheet = false
                        },
                        onShareLog = {
                            scope.launch {
                                val bugreport = loadingDialog.withLoading {
                                    withContext(Dispatchers.IO) {
                                        getBugreportFile(context)
                                    }
                                }

                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                                    bugreport
                                )

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    setDataAndType(uri, "application/gzip")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        sendLog
                                    )
                                )

                                showBottomsheet = false
                            }
                        }
                    )
                }
            }

            // 关于卡片
            item {
                SegmentedColumn(
                    title = stringResource(R.string.about),
                    content = {
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.Filled.Info,
                                title = stringResource(R.string.about),
                                onClick = {
                                    navigator.push(Route.About)
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogBottomSheet(
    onDismiss: () -> Unit,
    onSaveLog: () -> Unit,
    onShareLog: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SPACING_LARGE),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LogActionButton(
                icon = Icons.Filled.Save,
                text = stringResource(R.string.save_log),
                onClick = onSaveLog
            )

            LogActionButton(
                icon = Icons.Filled.Share,
                text = stringResource(R.string.send_log),
                onClick = onShareLog
            )
        }
        Spacer(modifier = Modifier.height(SPACING_LARGE))
    }
}

@Composable
fun LogActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(SPACING_MEDIUM)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(SPACING_MEDIUM))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun UninstallItem(
    withLoading: suspend (suspend () -> Unit) -> Unit
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uninstallConfirmDialog = rememberConfirmDialog()
    val showTodo = {
        Toast.makeText(context, "TODO", Toast.LENGTH_SHORT).show()
    }
    val uninstallDialog = rememberUninstallDialog { uninstallType ->
        scope.launch {
            val result = uninstallConfirmDialog.awaitConfirm(
                title = context.getString(uninstallType.title),
                content = context.getString(uninstallType.message)
            )
            if (result == ConfirmResult.Confirmed) {
                withLoading {
                    when (uninstallType) {
                        UninstallType.TEMPORARY -> showTodo()
                        UninstallType.PERMANENT -> navigator.push(Route.Flash(FlashIt.FlashUninstall))
                        UninstallType.RESTORE_STOCK_IMAGE -> navigator.push(Route.Flash(FlashIt.FlashRestore))
                        UninstallType.NONE -> Unit
                    }
                }
            }
        }
    }

    SettingsJumpPageWidget(
        icon = Icons.Filled.Delete,
        title = stringResource(id = R.string.settings_uninstall),
        onClick = {
            uninstallDialog.show()
        }
    )
}

enum class UninstallType(val title: Int, val message: Int, val icon: ImageVector) {
    TEMPORARY(
        R.string.settings_uninstall_temporary,
        R.string.settings_uninstall_temporary_message,
        Icons.Filled.Delete
    ),
    PERMANENT(
        R.string.settings_uninstall_permanent,
        R.string.settings_uninstall_permanent_message,
        Icons.Filled.DeleteForever
    ),
    RESTORE_STOCK_IMAGE(
        R.string.settings_restore_stock_image,
        R.string.settings_restore_stock_image_message,
        Icons.AutoMirrored.Filled.Undo
    ),
    NONE(0, 0, Icons.Filled.Delete)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberUninstallDialog(onSelected: (UninstallType) -> Unit): DialogHandle {
    return rememberCustomDialog { dismiss ->
        val options = listOf(
            UninstallType.PERMANENT,
            UninstallType.RESTORE_STOCK_IMAGE
        )
        val listOptions = options.map {
            ListOption(
                titleText = stringResource(it.title),
                subtitleText = if (it.message != 0) stringResource(it.message) else null,
                icon = IconSource(it.icon)
            )
        }

        var selectedOption by remember { mutableStateOf<UninstallType?>(null) }

        AlertDialog(
            onDismissRequest = {
                dismiss()
            },
            title = {
                Text(
                    text = stringResource(R.string.settings_uninstall),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    options.forEachIndexed { index, option ->
                        val isSelected = selectedOption == option
                        val backgroundColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            Color.Transparent
                        val contentColor = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(backgroundColor)
                                .clickable {
                                    selectedOption = option
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(24.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = listOptions[index].titleText,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                listOptions[index].subtitleText?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected)
                                            contentColor.copy(alpha = 0.8f)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonChecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedOption?.let { onSelected(it) }
                        dismiss()
                    },
                    enabled = selectedOption != null,
                ) {
                    Text(
                        text = stringResource(android.R.string.ok)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dismiss()
                    }
                ) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                    )
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 4.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(
        ),
        title = {
            Text(text = stringResource(R.string.settings))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =
                if (ThemeConfig.isEnableBlur)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
            scrolledContainerColor =
                if (ThemeConfig.isEnableBlur)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha)
        ),
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}
