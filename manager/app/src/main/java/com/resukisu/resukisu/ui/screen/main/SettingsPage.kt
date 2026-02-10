package com.resukisu.resukisu.ui.screen.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.content.edit
import com.maxkeppeker.sheets.core.models.base.IconSource
import com.maxkeppeler.sheets.list.models.ListOption
import com.ramcosta.composedestinations.generated.destinations.AppProfileTemplateScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.generated.destinations.LogViewerScreenDestination
import com.ramcosta.composedestinations.generated.destinations.MoreSettingsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.UmountManagerScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.AboutDialog
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.DialogHandle
import com.resukisu.resukisu.ui.component.KsuIsValid
import com.resukisu.resukisu.ui.component.ksuIsValid
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.rememberCustomDialog
import com.resukisu.resukisu.ui.component.rememberLoadingDialog
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsDropdownWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.component.settings.SplicedColumnGroup
import com.resukisu.resukisu.ui.screen.FlashIt
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.execKsud
import com.resukisu.resukisu.ui.util.getBugreportFile
import com.resukisu.resukisu.ui.util.getFeatureStatus
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
fun SettingsPage(navigator: DestinationsNavigator, bottomPadding: Dp, hazeState: HazeState?) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var isSuLogEnabled by remember { mutableStateOf(Natives.isSuLogEnabled()) }

    Scaffold(
        topBar = {
            TopBar(scrollBehavior = scrollBehavior, hazeState = hazeState)
        },
        snackbarHost = {
            SnackbarHost(
                modifier = Modifier.padding(bottom = bottomPadding),
                hostState = snackBarHost
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val aboutDialog = rememberCustomDialog {
            AboutDialog(it)
        }
        val loadingDialog = rememberLoadingDialog()

        Column(
            modifier =
                (if (hazeState != null)
                    Modifier.hazeSource(state = hazeState)
                else Modifier)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
        ) {
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
                    snackBarHost.showSnackbar(context.getString(R.string.log_saved))
                }
            }

            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))

            // 配置卡片
            KsuIsValid {
                val modeItems = listOf(
                    stringResource(id = R.string.settings_mode_default),
                    stringResource(id = R.string.settings_mode_disable_until_reboot),
                    stringResource(id = R.string.settings_mode_disable_always),
                )
                SplicedColumnGroup(
                    title = stringResource(R.string.configuration),
                    content = {
                        item {
                            // 配置文件模板入口
                            SettingsJumpPageWidget(
                                icon = Icons.Filled.Fence,
                                title = stringResource(R.string.settings_profile_template),
                                description = stringResource(R.string.settings_profile_template_summary),
                                onClick = {
                                    navigator.navigate(AppProfileTemplateScreenDestination)
                                }
                            )
                        }

                        item {
                            var suCompatMode by rememberSaveable {
                                mutableIntStateOf(
                                    run {
                                        val currentEnabled = Natives.isSuEnabled()
                                        val savedPersist = prefs.getInt("su_compat_mode", 0)
                                        if (savedPersist == 2) 2 else if (!currentEnabled) 1 else 0
                                    }
                                )
                            }
                            val suStatus by produceState(initialValue = "") {
                                value = getFeatureStatus("su_compat")
                            }
                            val suSummary = when (suStatus) {
                                "unsupported" -> {
                                    suCompatMode = 0 // fix wrongly display
                                    stringResource(id = R.string.feature_status_unsupported_summary)
                                }

                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                else -> stringResource(id = R.string.settings_sucompat_summary)
                            }
                            SettingsDropdownWidget(
                                icon = Icons.Rounded.RemoveModerator,
                                title = stringResource(id = R.string.settings_sucompat),
                                description = suSummary,
                                items = modeItems,
                                enabled = suStatus == "supported",
                                selectedIndex = suCompatMode,
                                onSelectedIndexChange = { index ->
                                    when (index) {
                                        // Default: enable and save to persist
                                        0 -> if (Natives.setSuEnabled(true)) {
                                            execKsud("feature save", true)
                                            prefs.edit { putInt("su_compat_mode", 0) }
                                            suCompatMode = 0
                                        }

                                        // Temporarily disable: save enabled state first, then disable
                                        1 -> if (Natives.setSuEnabled(true)) {
                                            execKsud("feature save", true)
                                            if (Natives.setSuEnabled(false)) {
                                                prefs.edit { putInt("su_compat_mode", 0) }
                                                suCompatMode = 1
                                            }
                                        }

                                        // Permanently disable: disable and save
                                        2 -> if (Natives.setSuEnabled(false)) {
                                            execKsud("feature save", true)
                                            prefs.edit { putInt("su_compat_mode", 2) }
                                            suCompatMode = 2
                                        }
                                    }
                                }
                            )
                        }

                        item {
                            var isKernelUmountEnabled by rememberSaveable { mutableStateOf(Natives.isKernelUmountEnabled()) }
                            val umountStatus by produceState(initialValue = "") {
                                value = getFeatureStatus("kernel_umount")
                            }
                            val umountSummary = when (umountStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                else -> stringResource(id = R.string.settings_kernel_umount_summary)
                            }
                            SettingsSwitchWidget(
                                icon = Icons.Rounded.RemoveCircle,
                                title = stringResource(id = R.string.settings_kernel_umount),
                                description = umountSummary,
                                enabled = umountStatus == "supported",
                                checked = isKernelUmountEnabled,
                                onCheckedChange = { checked ->
                                    if (Natives.setKernelUmountEnabled(checked)) {
                                        execKsud("feature save", true)
                                        isKernelUmountEnabled = checked
                                    }
                                }
                            )
                        }

                        item {
                            var suLogMode by rememberSaveable {
                                mutableIntStateOf(
                                    run {
                                        val currentEnabled = Natives.isSuLogEnabled()
                                        val savedPersist = prefs.getInt("sulog_mode", 0)
                                        if (savedPersist == 2) 2 else if (!currentEnabled) 1 else 0
                                    }
                                )
                            }
                            val suLogStatus by produceState(initialValue = "") {
                                value = getFeatureStatus("sulog")
                            }
                            val suLogSummary = when (suLogStatus) {
                                "unsupported" -> {
                                    isSuLogEnabled =
                                        true // some old kernel support sulog, we need allow show sulog in there
                                    suLogMode = 0 // fix wrongly display
                                    stringResource(id = R.string.feature_status_unsupported_summary)
                                }

                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                else -> stringResource(id = R.string.settings_sulog_summary)
                            }
                            SettingsDropdownWidget(
                                icon = Icons.Default.RemoveRedEye,
                                title = stringResource(id = R.string.settings_sulog),
                                description = suLogSummary,
                                items = modeItems,
                                enabled = suLogStatus == "supported",
                                selectedIndex = suLogMode,
                                onSelectedIndexChange = { index ->
                                    when (index) {
                                        // Default: enable and save to persist
                                        0 -> if (Natives.setSuLogEnabled(true)) {
                                            execKsud("feature save", true)
                                            prefs.edit { putInt("sulog_mode", 0) }
                                            suLogMode = 0
                                            isSuLogEnabled = true
                                        }

                                        // Temporarily disable: save enabled state first, then disable
                                        1 -> if (Natives.setSuLogEnabled(true)) {
                                            execKsud("feature save", true)
                                            if (Natives.setSuLogEnabled(false)) {
                                                prefs.edit { putInt("sulog_mode", 0) }
                                                suLogMode = 1
                                                isSuLogEnabled = false
                                            }
                                        }

                                        // Permanently disable: disable and save
                                        2 -> if (Natives.setSuLogEnabled(false)) {
                                            execKsud("feature save", true)
                                            prefs.edit { putInt("sulog_mode", 2) }
                                            suLogMode = 2
                                            isSuLogEnabled = false
                                        }
                                    }
                                }
                            )
                        }

                        item {
                            // 卸载模块开关
                            var umountChecked by rememberSaveable { mutableStateOf(Natives.isDefaultUmountModules()) }
                            SettingsSwitchWidget(
                                icon = Icons.Rounded.FolderDelete,
                                title = stringResource(id = R.string.settings_umount_modules_default),
                                description = stringResource(id = R.string.settings_umount_modules_default_summary),
                                checked = umountChecked,
                                onCheckedChange = {
                                    if (Natives.setDefaultUmountModules(it)) {
                                        umountChecked = it
                                    }
                                }
                            )
                        }
                    }
                )
            }

            // 应用设置卡片
            SplicedColumnGroup(
                title = stringResource(R.string.app_settings),
                content = {
                    item {
                        // 更新检查开关
                        var checkUpdate by rememberSaveable {
                            mutableStateOf(prefs.getBoolean("check_update", true))
                        }
                        SettingsSwitchWidget(
                            icon = Icons.Filled.Update,
                            title = stringResource(R.string.settings_check_update),
                            description = stringResource(R.string.settings_check_update_summary),
                            checked = checkUpdate,
                            onCheckedChange = { enabled ->
                                prefs.edit { putBoolean("check_update", enabled) }
                                checkUpdate = enabled
                            }
                        )
                    }

                    item {
                        // 更多设置
                        SettingsJumpPageWidget(
                            icon = Icons.Filled.Settings,
                            title = stringResource(R.string.more_settings),
                            description = stringResource(R.string.more_settings),
                            onClick = {
                                navigator.navigate(MoreSettingsScreenDestination)
                            }
                        )
                    }
                }
            )

            // 工具卡片
            var showBottomsheet by remember { mutableStateOf(false) }
            SplicedColumnGroup(
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

                    if (ksuIsValid() && isSuLogEnabled) {
                        item {
                            // 查看使用日志
                            SettingsJumpPageWidget(
                                icon = Icons.Filled.Visibility,
                                title = stringResource(R.string.log_viewer_view_logs),
                                description = stringResource(R.string.log_viewer_view_logs_summary),
                                onClick = {
                                    navigator.navigate(LogViewerScreenDestination)
                                }
                            )
                        }
                    }

                    if (ksuIsValid()) {
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.Filled.FolderOff,
                                title = stringResource(R.string.umount_path_manager),
                                description = stringResource(R.string.umount_path_manager_summary),
                                onClick = {
                                    navigator.navigate(UmountManagerScreenDestination)
                                }
                            )
                        }
                    }

                    if (Natives.isLkmMode) {
                        item {
                            UninstallItem(navigator) {
                                loadingDialog.withLoading(it)
                            }
                        }
                    }
                }
            )
            if (showBottomsheet) {
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
                                    context.getString(R.string.send_log)
                                )
                            )

                            showBottomsheet = false
                        }
                    }
                )
            }

            // 关于卡片
            SplicedColumnGroup(
                title = stringResource(R.string.about),
                content = {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.about),
                            onClick = {
                                aboutDialog.show()
                            }
                        ) {}
                    }
                }
            )

            Spacer(modifier = Modifier.height(bottomPadding + 16.dp))
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

@Composable
fun UninstallItem(
    navigator: DestinationsNavigator,
    withLoading: suspend (suspend () -> Unit) -> Unit
) {
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
                        UninstallType.PERMANENT -> navigator.navigate(
                            FlashScreenDestination(FlashIt.FlashUninstall)
                        )
                        UninstallType.RESTORE_STOCK_IMAGE -> navigator.navigate(
                            FlashScreenDestination(FlashIt.FlashRestore)
                        )
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

        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null,
    hazeState: HazeState? = null
) {
    val hazeStyle = if (ThemeConfig.backgroundImageLoaded) HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = 0.8f
        ),
        tint = HazeTint(Color.Transparent)
    ) else null

    val collapsedFraction = scrollBehavior?.state?.collapsedFraction ?: 0f
    val modifier = if (ThemeConfig.backgroundImageLoaded && hazeStyle != null && hazeState != null) {
        Modifier.hazeEffect(hazeState) {
            style = hazeStyle
            noiseFactor = 0f
            blurRadius = 30.dp
            alpha = collapsedFraction
        }
    }
    else Modifier

    LargeFlexibleTopAppBar(
        modifier = modifier,
        title = {
            Text(text = stringResource(R.string.settings))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =
                if (ThemeConfig.backgroundImageLoaded) Color.Transparent
                else MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor =
                if (ThemeConfig.backgroundImageLoaded) Color.Transparent
                else MaterialTheme.colorScheme.surfaceContainer
        ),
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}
