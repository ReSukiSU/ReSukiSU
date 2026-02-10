package com.resukisu.resukisu.ui.screen.main

import android.annotation.SuppressLint
import android.app.Activity.CLIPBOARD_SERVICE
import android.app.Activity.MODE_PRIVATE
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Wysiwyg
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.capsule.ContinuousRoundedRectangle
import com.ramcosta.composedestinations.generated.destinations.ExecuteModuleActionScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleRepoScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.InstallConfirmationDialog
import com.resukisu.resukisu.ui.component.SearchAppBar
import com.resukisu.resukisu.ui.component.WarningCard
import com.resukisu.resukisu.ui.component.ZipFileDetector.parseModuleInfo
import com.resukisu.resukisu.ui.component.ZipFileInfo
import com.resukisu.resukisu.ui.component.ZipType
import com.resukisu.resukisu.ui.component.pinnedScrollBehavior
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.rememberLoadingDialog
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsTextFieldWidget
import com.resukisu.resukisu.ui.component.settings.SplicedColumnGroup
import com.resukisu.resukisu.ui.screen.FlashIt
import com.resukisu.resukisu.ui.screen.LabelText
import com.resukisu.resukisu.ui.theme.getCardColors
import com.resukisu.resukisu.ui.theme.getCardElevation
import com.resukisu.resukisu.ui.util.DownloadListener
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.download
import com.resukisu.resukisu.ui.util.hasMagisk
import com.resukisu.resukisu.ui.util.module.ModuleUtils
import com.resukisu.resukisu.ui.util.module.Shortcut
import com.resukisu.resukisu.ui.util.reboot
import com.resukisu.resukisu.ui.util.toggleModule
import com.resukisu.resukisu.ui.util.undoUninstallModule
import com.resukisu.resukisu.ui.util.uninstallModule
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.webui.WebUIActivity
import com.topjohnwu.superuser.io.SuFile
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ShortcutType {
    Action,
    WebUI
}

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */
@SuppressLint("ResourceType", "AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulePage(navigator: DestinationsNavigator, bottomPadding: Dp, hazeState: HazeState?) {
    val context = LocalContext.current
    val viewModel = viewModel<ModuleViewModel>(
        viewModelStoreOwner = ksuApp
    )
    val prefs = context.getSharedPreferences("settings", MODE_PRIVATE)
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    var lastClickTime by remember { mutableStateOf(0L) }

    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    var showBottomSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    var showConfirmationDialog by remember { mutableStateOf(false) }
    var pendingZipFiles by remember { mutableStateOf<List<ZipFileInfo>>(emptyList()) }
    InstallConfirmationDialog(
        show = showConfirmationDialog,
        zipFiles = pendingZipFiles,
        onConfirm = { info ->
            showConfirmationDialog = false
            navigator.navigate(
                FlashScreenDestination(
                    FlashIt.FlashModules(ArrayList(info.filter { it.type == ZipType.MODULE }.map { it.uri }))
                )
            )
            viewModel.markNeedRefresh()
        },
        onDismiss = {
            showConfirmationDialog = false
            pendingZipFiles = emptyList()
        }
    )

    val selectZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode != RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val data = it.data ?: return@rememberLauncherForActivityResult

        scope.launch {
            val zipFiles = mutableListOf<ZipFileInfo>()
            val clipData = data.clipData
            if (clipData != null) {
                val selectedModules = mutableListOf<Uri>()
                val selectedModuleNames = mutableMapOf<Uri, String>()

                fun processUri(uri: Uri) {
                    try {
                        if (!ModuleUtils.isUriAccessible(context, uri)) {
                            return
                        }
                        ModuleUtils.takePersistableUriPermission(context, uri)
                        val moduleName = ModuleUtils.extractModuleName(context, uri)
                        selectedModules.add(uri)
                        selectedModuleNames[uri] = moduleName
                    } catch (e: Exception) {
                        Log.e("ModuleScreen", "Error while processing URI: $uri, Error: ${e.message}")
                    }
                }

                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    processUri(uri)
                }

                if (selectedModules.isEmpty()) {
                    snackBarHost.showSnackbar("Unable to access selected module files")
                    return@launch
                }
                selectedModules.forEach { it ->
                    zipFiles.add(parseModuleInfo(context, it))
                }
                pendingZipFiles = zipFiles

                showConfirmationDialog = true
            } else {
                val uri = data.data ?: return@launch
                // 单个安装模块
                try {
                    if (!ModuleUtils.isUriAccessible(context, uri)) {
                        snackBarHost.showSnackbar("Unable to access selected module files")
                        return@launch
                    }

                    ModuleUtils.takePersistableUriPermission(context, uri)

                    zipFiles.add(parseModuleInfo(context, uri))
                    pendingZipFiles = zipFiles

                    showConfirmationDialog = true
                } catch (e: Exception) {
                    Log.e("ModuleScreen", "Error processing a single URI: $uri, Error: ${e.message}")
                    snackBarHost.showSnackbar("Error processing module file: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.moduleList.isEmpty() || viewModel.isNeedRefresh) {
            viewModel.sortEnabledFirst = prefs.getBoolean("module_sort_enabled_first", false)
            viewModel.sortActionFirst = prefs.getBoolean("module_sort_action_first", false)
            viewModel.fetchModuleList()
        }
    }

    val isSafeMode = Natives.isSafeMode
    val hasMagisk = hasMagisk()
    val hideInstallButton = isSafeMode || hasMagisk

    val scrollBehavior = pinnedScrollBehavior()

    Scaffold(
        topBar = {
            SearchAppBar(
                searchText = viewModel.search,
                onSearchTextChange = { viewModel.search = it },
                dropdownContent = {
                    IconButton(
                        onClick = {
                            navigator.navigate(ModuleRepoScreenDestination)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cloud,
                            contentDescription = stringResource(id = R.string.module_repo),
                        )
                    }
                    IconButton(
                        onClick = { showBottomSheet = true },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(id = R.string.settings),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.search_modules),
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            if (hideInstallButton) return@Scaffold

            FloatingActionButton(
                modifier = Modifier.padding(bottom = bottomPadding),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    selectZipLauncher.launch(
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    )
                },
                content = {
                    Icon(
                        painter = painterResource(id = R.drawable.package_import),
                        contentDescription = null
                    )
                }
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        snackbarHost = {
            SnackbarHost(
                hostState = snackBarHost
            )
        }
    ) { innerPadding ->
        when {
            hasMagisk -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(bottom = 16.dp)
                        )
                        Text(
                            stringResource(R.string.module_magisk_conflict),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            viewModel.moduleList.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Extension,
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .padding(bottom = 16.dp)
                        )
                        Text(
                            text = stringResource(R.string.module_empty),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            else -> {
                ModuleList(
                    navigator = navigator,
                    viewModel = viewModel,
                    listState = listState,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    onInstallModule = {
                        navigator.navigate(FlashScreenDestination(FlashIt.FlashModule(it)))
                    },
                    onUpdateModule = {
                        navigator.navigate(FlashScreenDestination(FlashIt.FlashModuleUpdate(it)))
                    },
                    onClickModule = { id, name, hasWebUi ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 600) {
                            Log.d("ModuleScreen", "Click too fast, ignoring")
                            return@ModuleList
                        }
                        lastClickTime = currentTime

                        if (hasWebUi) {
                            try {
                                context.startActivity(
                                    Intent(context, WebUIActivity::class.java)
                                    .setData("kernelsu://webui/$id".toUri())
                                    .putExtra("id", id)
                                        .putExtra("name", name)
                                )
                            } catch (e: Exception) {
                                Log.e("ModuleScreen", "Error launching WebUI: ${e.message}", e)
                                scope.launch {
                                    snackBarHost.showSnackbar("Error launching WebUI: ${e.message}")
                                }
                            }
                            return@ModuleList
                        }
                    },
                    context = context,
                    snackBarHost = snackBarHost,
                    bottomPadding = bottomPadding,
                    topPadding = innerPadding.calculateTopPadding(),
                    hazeState = hazeState
                )
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                },
                sheetState = bottomSheetState,
                dragHandle = {
                    Surface(
                        modifier = Modifier.padding(vertical = 11.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            Modifier.size(
                                width = 32.dp,
                                height = 4.dp
                            )
                        )
                    }
                }
            ) {
                ModuleBottomSheetContent(
                    viewModel = viewModel,
                    prefs = prefs
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModuleBottomSheetContent(
    viewModel: ModuleViewModel,
    prefs: android.content.SharedPreferences
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        // 标题
        Text(
            text = stringResource(R.string.menu_options),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        // 排序选项

        Text(
            text = stringResource(R.string.sort_options),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 优先显示有操作的模块
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.module_sort_action_first),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = viewModel.sortActionFirst,
                    onCheckedChange = { checked ->
                        viewModel.sortActionFirst = checked
                        prefs.edit {
                            putBoolean("module_sort_action_first", checked)
                        }
                    },
                    thumbContent = {
                        if (viewModel.sortActionFirst) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        } else
                        {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    }
                )
            }

            // 优先显示已启用的模块
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.module_sort_enabled_first),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = viewModel.sortEnabledFirst,
                    onCheckedChange = { checked ->
                        viewModel.sortEnabledFirst = checked
                        prefs.edit {
                            putBoolean("module_sort_enabled_first", checked)
                        }
                    },
                    thumbContent = {
                        if (viewModel.sortEnabledFirst) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        } else
                        {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    }
                )
            }
        }
    }
}

var showMetamoduleWarning by mutableStateOf(true)

private fun getMetaModuleWarningText(
    viewModel: ModuleViewModel,
    context: Context
) : String? {
    if (!showMetamoduleWarning) return null

    val hasSystemModule = viewModel.moduleList.any { module ->
        SuFile.open("/data/adb/modules/${module.id}/system").exists()
    }

    if (!hasSystemModule) return null

    val metaProp = SuFile.open("/data/adb/metamodule/module.prop").exists()
    val metaRemoved = SuFile.open("/data/adb/metamodule/remove").exists()
    val metaDisabled = SuFile.open("/data/adb/metamodule/disable").exists()

    return when {
        !metaProp ->
            context.getString(R.string.no_meta_module_installed)

        metaProp && metaRemoved ->
            context.getString(R.string.meta_module_removed)

        metaProp && metaDisabled ->
            context.getString(R.string.meta_module_disabled)

        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetaModuleWarningCard(
    text: String
) {
    AnimatedVisibility(
        visible = showMetamoduleWarning,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        WarningCard(
            message = text,
            onClose = {
                showMetamoduleWarning = false
            }
        )

        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModuleList(
    navigator: DestinationsNavigator,
    viewModel: ModuleViewModel,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    boxModifier: Modifier = Modifier,
    onInstallModule: (Uri) -> Unit,
    onUpdateModule: (Uri) -> Unit,
    onClickModule: (id: String, name: String, hasWebUi: Boolean) -> Unit,
    context: Context,
    snackBarHost: SnackbarHostState,
    bottomPadding : Dp,
    topPadding : Dp,
    hazeState : HazeState?
) {
    val pullRefreshState = rememberPullToRefreshState()
    val failedEnable = stringResource(R.string.module_failed_to_enable)
    val failedDisable = stringResource(R.string.module_failed_to_disable)
    val failedUninstall = stringResource(R.string.module_uninstall_failed)
    val successUninstall = stringResource(R.string.module_uninstall_success)
    val reboot = stringResource(R.string.reboot)
    val rebootToApply = stringResource(R.string.reboot_to_apply)
    val moduleStr = stringResource(R.string.module)
    val uninstall = stringResource(R.string.uninstall)
    val cancel = stringResource(android.R.string.cancel)
    val moduleUninstallConfirm = stringResource(R.string.module_uninstall_confirm)
    val metaModuleUninstallConfirm = stringResource(R.string.metamodule_uninstall_confirm)
    val updateText = stringResource(R.string.module_update)
    val changelogText = stringResource(R.string.module_changelog)
    val downloadingText = stringResource(R.string.module_downloading)
    val startDownloadingText = stringResource(R.string.module_start_downloading)
    val fetchChangeLogFailed = stringResource(R.string.module_changelog_failed)
    val downloadErrorText = stringResource(R.string.module_download_error)

    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()

    var shortcutModuleId by rememberSaveable { mutableStateOf<String?>(null) }
    val textFieldState = rememberTextFieldState()
    var shortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultShortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultActionShortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultWebUiShortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedShortcutType by rememberSaveable { mutableStateOf<ShortcutType?>(null) }
    val showShortcutDialog = remember { mutableStateOf(false) }
    val showShortcutTypeRow = remember { mutableStateOf(false) }

    fun openShortcutDialogForType(type: ShortcutType) {
        selectedShortcutType = type
        val defaultIcon = when (type) {
            ShortcutType.Action -> defaultActionShortcutIconUri ?: defaultWebUiShortcutIconUri
            ShortcutType.WebUI -> defaultWebUiShortcutIconUri ?: defaultActionShortcutIconUri
        }
        defaultShortcutIconUri = defaultIcon
        shortcutIconUri = defaultIcon
        showShortcutDialog.value = true
    }

    fun hasModuleShortcut(context: Context, moduleId: String, type: ShortcutType): Boolean {
        return when (type) {
            ShortcutType.Action -> Shortcut.hasModuleActionShortcut(context, moduleId)
            ShortcutType.WebUI -> Shortcut.hasModuleWebUiShortcut(context, moduleId)
        }
    }

    fun deleteModuleShortcut(context: Context, moduleId: String, type: ShortcutType) {
        when (type) {
            ShortcutType.Action -> Shortcut.deleteModuleActionShortcut(context, moduleId)
            ShortcutType.WebUI -> Shortcut.deleteModuleWebUiShortcut(context, moduleId)
        }
    }

    fun createModuleShortcut(
        context: Context,
        moduleId: String,
        name: String,
        iconUri: String?,
        type: ShortcutType
    ) {
        when (type) {
            ShortcutType.Action -> {
                Shortcut.createModuleActionShortcut(
                    context = context,
                    moduleId = moduleId,
                    name = name,
                    iconUri = iconUri
                )
            }

            ShortcutType.WebUI -> {
                Shortcut.createModuleWebUiShortcut(
                    context = context,
                    moduleId = moduleId,
                    name = name,
                    iconUri = iconUri
                )
            }
        }
    }

    val pickShortcutIconLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        shortcutIconUri = uri?.toString()
    }

    val shortcutPreviewIcon = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(shortcutIconUri) {
        val uriStr = shortcutIconUri
        if (uriStr.isNullOrBlank()) {
            shortcutPreviewIcon.value = null
            return@LaunchedEffect
        }
        val bitmap = withContext(Dispatchers.IO) {
            Shortcut.loadShortcutBitmap(context, uriStr)
        }
        shortcutPreviewIcon.value = bitmap?.asImageBitmap()
    }

    var hasExistingShortcut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(shortcutModuleId, selectedShortcutType, showShortcutDialog.value) {
        val moduleId = shortcutModuleId
        val type = selectedShortcutType
        if (!showShortcutDialog.value || moduleId.isNullOrBlank() || type == null) {
            hasExistingShortcut = false
            return@LaunchedEffect
        }
        val exists = withContext(Dispatchers.IO) {
            hasModuleShortcut(context, moduleId, type)
        }
        hasExistingShortcut = exists
    }

    suspend fun onModuleUpdate(
        module: ModuleViewModel.ModuleInfo,
        changelogUrl: String,
        downloadUrl: String,
        fileName: String
    ) {
        val request = okhttp3.Request.Builder()
            .url(changelogUrl)
            .build()

        val changelogResult = loadingDialog.withLoading {
            withContext(Dispatchers.IO) {
                runCatching {
                    ksuApp.okhttpClient.newCall(request).execute().body!!.string()
                }
            }
        }

        val showToast: suspend (String) -> Unit = { msg ->
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    msg,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val changelog = changelogResult.getOrElse {
            showToast(fetchChangeLogFailed.format(it.message))
            return
        }

        val confirmResult = confirmDialog.awaitConfirm(
            changelogText,
            content = changelog,
            markdown = true,
            confirm = updateText,
        )

        if (confirmResult != ConfirmResult.Confirmed) {
            return
        }

        showToast(startDownloadingText.format(module.name))

        val downloading = downloadingText.format(module.name)
        withContext(Dispatchers.IO) {
            download(
                context,
                downloadUrl,
                fileName,
                downloading,
                onDownloaded = { uri ->
                    onUpdateModule(uri)
                },
                onDownloading = {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, downloading, Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { errorMsg ->
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "$downloadErrorText: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    suspend fun onModuleUninstallClicked(module: ModuleViewModel.ModuleInfo) {
        val isUninstall = !module.remove
        if (isUninstall) {
            val formatter = if (module.metamodule) metaModuleUninstallConfirm else moduleUninstallConfirm
            val confirmResult = confirmDialog.awaitConfirm(
                moduleStr,
                content = formatter.format(module.name),
                confirm = uninstall,
                dismiss = cancel
            )
            if (confirmResult != ConfirmResult.Confirmed) {
                return
            }
        }

        val success = loadingDialog.withLoading {
            withContext(Dispatchers.IO) {
                if (isUninstall) {
                    Shortcut.deleteModuleActionShortcut(context, module.id)
                    Shortcut.deleteModuleWebUiShortcut(context, module.id)
                    uninstallModule(module.dirId)
                } else {
                    undoUninstallModule(module.dirId)
                }
            }
        }

        if (success) {
            viewModel.fetchModuleList()
            viewModel.markNeedRefresh()
        }
        if (!isUninstall) return
        val message = if (success) {
            successUninstall.format(module.name)
        } else {
            failedUninstall.format(module.name)
        }
        val actionLabel = if (success) {
            reboot
        } else {
            null
        }
        val result = snackBarHost.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            reboot()
        }
    }

    fun onModuleAddShortcut(module: ModuleViewModel.ModuleInfo) {
        shortcutModuleId = module.id
        textFieldState.edit {
            replace(0, length, module.name)
        }
        shortcutIconUri = null
        defaultShortcutIconUri = null
        defaultActionShortcutIconUri = module.actionIconPath
            ?.takeIf { it.isNotBlank() }
            ?.let { "su:$it" }
        defaultWebUiShortcutIconUri = module.webUiIconPath
            ?.takeIf { it.isNotBlank() }
            ?.let { "su:$it" }
        if (module.hasActionScript && module.hasWebUi) {
            selectedShortcutType = null
            showShortcutTypeRow.value = true
            openShortcutDialogForType(ShortcutType.Action)
        } else if (module.hasActionScript) {
            openShortcutDialogForType(ShortcutType.Action)
        } else if (module.hasWebUi) {
            openShortcutDialogForType(ShortcutType.WebUI)
        }
    }

    var pullToRefreshBoxModifier = boxModifier

    pullToRefreshBoxModifier = if (hazeState != null) pullToRefreshBoxModifier.hazeSource(state = hazeState) else pullToRefreshBoxModifier

    PullToRefreshBox(
        state = pullRefreshState,
        onRefresh = {
            viewModel.fetchModuleList(true)
        },
        modifier = pullToRefreshBoxModifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier = Modifier
                    .padding(top = topPadding)
                    .align(Alignment.TopCenter),
                state = pullRefreshState,
                isRefreshing = viewModel.isRefreshing,
            )
        },
        isRefreshing = viewModel.isRefreshing
    ) {
        val metaModuleWarningText by produceState<String?>(initialValue = null, viewModel.moduleList) {
            value = withContext(Dispatchers.IO) {
                getMetaModuleWarningText(viewModel, context)
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = remember {
                PaddingValues(
                    start = 16.dp,
                    top = 0.dp,
                    end = 16.dp,
                    bottom = 65.dp
                )
            },
        ) {
            item {
                Spacer(modifier = Modifier.height(topPadding))
            }

            if (metaModuleWarningText != null) {
                item(
                    key = "warning"
                ) {
                    MetaModuleWarningCard(metaModuleWarningText!!)
                }
            }

            items(
                items = viewModel.moduleList,
                key = { "module-$it.id" }
            ) { module ->
                ModuleItem(
                    viewModel = viewModel,
                    navigator = navigator,
                    module = module,
                    updateUrl = module.moduleUpdate?.zipUrl.orEmpty(),
                    onUninstallClicked = {
                        viewModel.viewModelScope.launch {
                            withContext(Dispatchers.IO) {
                                onModuleUninstallClicked(module)
                            }
                        }
                    },
                    onCheckChanged = {
                        viewModel.viewModelScope.launch {
                            withContext(Dispatchers.IO) {
                                val success = withContext(Dispatchers.IO) {
                                    toggleModule(module.dirId, !module.enabled)
                                }
                                if (success) {
                                    viewModel.fetchModuleList()

                                    val result = snackBarHost.showSnackbar(
                                        message = rebootToApply,
                                        actionLabel = reboot,
                                        duration = SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        reboot()
                                    }
                                } else {
                                    val message =
                                        if (module.enabled) failedDisable else failedEnable
                                    snackBarHost.showSnackbar(message.format(module.name))
                                }
                            }
                        }
                    },
                    onUpdate = {
                        viewModel.viewModelScope.launch {
                            withContext(Dispatchers.IO) {
                                onModuleUpdate(
                                    module,
                                    module.moduleUpdate!!.changelog,
                                    module.moduleUpdate.zipUrl,
                                    "${module.name}-${module.moduleUpdate.version}.zip"
                                )
                            }
                        }
                    },
                    onClick = {
                        onClickModule(it.dirId, it.name, it.hasWebUi)
                    },
                    onModuleAddShortcut = {
                        onModuleAddShortcut(it)
                    }
                )

                Spacer(Modifier.height(1.dp))
            }

            item {
                Spacer(modifier = Modifier.height(bottomPadding))
            }
        }

        DownloadListener(context, onInstallModule)
    }

    if (showShortcutDialog.value) {
        ModalBottomSheet(
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
            onDismissRequest = {
                showShortcutDialog.value = false
                showShortcutTypeRow.value = false
            }
        ) {
            var error by remember { mutableStateOf("") }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.module_shortcut_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                )
                if (showShortcutTypeRow.value) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedShortcutType?.ordinal ?: 0,
                        containerColor = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedShortcutType == ShortcutType.Action,
                            onClick = {
                                selectedShortcutType = ShortcutType.Action
                                shortcutIconUri = defaultActionShortcutIconUri
                                defaultShortcutIconUri = defaultActionShortcutIconUri
                            },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text("Action") }
                        )

                        Tab(
                            selected = selectedShortcutType == ShortcutType.WebUI,
                            onClick = {
                                selectedShortcutType = ShortcutType.WebUI
                                shortcutIconUri = defaultWebUiShortcutIconUri
                                defaultShortcutIconUri = defaultWebUiShortcutIconUri
                            },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text("WebUI") }
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .size(100.dp)
                        .clip(ContinuousRoundedRectangle(25.dp))
                ) {
                    val preview = shortcutPreviewIcon.value
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            modifier = Modifier.size(100.dp),
                            contentDescription = null,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(Color.White)
                        )
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            contentScale = FixedScale(1.5f)
                        )
                    }
                }
                SplicedColumnGroup {
                    if (shortcutIconUri == defaultShortcutIconUri) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.Rounded.Photo,
                                title = stringResource(id = R.string.module_shortcut_icon_pick),
                                onClick = {
                                    pickShortcutIconLauncher.launch("image/*")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.Rounded.Restore,
                                title = stringResource(id = R.string.restore),
                                onClick = {
                                    shortcutIconUri = defaultShortcutIconUri
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    item {
                        val shouldNotEmpty =
                            stringResource(R.string.module_shortcut_should_not_empty)
                        SettingsTextFieldWidget(
                            state = textFieldState,
                            title = stringResource(id = R.string.module_shortcut_name_label),
                            error = error,
                        )

                        LaunchedEffect(textFieldState.text) {
                            error = if (textFieldState.text.isBlank()) {
                                shouldNotEmpty
                            } else ""
                        }
                    }

                    if (hasExistingShortcut) {
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.Rounded.Delete,
                                title = stringResource(id = R.string.module_shortcut_delete),
                                onClick = {
                                    val moduleId = shortcutModuleId
                                    val type = selectedShortcutType
                                    if (!moduleId.isNullOrBlank() && type != null) {
                                        deleteModuleShortcut(context, moduleId, type)
                                    }
                                    showShortcutDialog.value = false
                                },
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showShortcutDialog.value = false },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = stringResource(id = android.R.string.cancel),
                        )
                    }

                    Button(
                        onClick = {
                            val moduleId = shortcutModuleId
                            val type = selectedShortcutType
                            if (!moduleId.isNullOrBlank() && textFieldState.text.isNotBlank() && type != null) {
                                createModuleShortcut(
                                    context = context,
                                    moduleId = moduleId,
                                    name = textFieldState.text.toString(),
                                    iconUri = shortcutIconUri,
                                    type = type
                                )
                            }
                            showShortcutDialog.value = false
                        },
                        enabled = error.isBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = if (hasExistingShortcut) {
                                stringResource(id = R.string.module_update)
                            } else {
                                stringResource(id = android.R.string.ok)
                            },
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ModuleItem(
    viewModel: ModuleViewModel,
    navigator: DestinationsNavigator,
    module: ModuleViewModel.ModuleInfo,
    updateUrl: String,
    onUninstallClicked: (ModuleViewModel.ModuleInfo) -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onUpdate: (ModuleViewModel.ModuleInfo) -> Unit,
    onClick: (ModuleViewModel.ModuleInfo) -> Unit,
    onModuleAddShortcut: (ModuleViewModel.ModuleInfo) -> Unit,
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", MODE_PRIVATE)
    val isHideTagRow = prefs.getBoolean("is_hide_tag_row", false)
    // 获取显示更多模块信息的设置
    val showMoreModuleInfo = prefs.getBoolean("show_more_module_info", false)

    // 剪贴板管理器和触觉反馈
    val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val hapticFeedback = LocalHapticFeedback.current

    ElevatedCard(
        colors = getCardColors(MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = getCardElevation(),
    ) {
        val textDecoration = if (!module.remove) null else TextDecoration.LineThrough
        val interactionSource = remember { MutableInteractionSource() }

        LaunchedEffect(module.dirId) {
            viewModel.loadSize(module.dirId)
        }

        val sizes by viewModel.moduleSize.collectAsStateWithLifecycle()

        val sizeStr = sizes[module.dirId]

        Column(
            modifier = Modifier
                .run {
                    if (module.hasActionScript || module.hasWebUi) {
                        combinedClickable(
                            onLongClick = {
                                onModuleAddShortcut(module)
                            },
                            onClick = {
                                if (module.hasWebUi) {
                                    onClick(module)
                                }
                            }
                        )
                    } else {
                        this
                    }
                }
                .padding(22.dp, 18.dp, 22.dp, 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val moduleVersion = stringResource(id = R.string.module_version)
                val moduleAuthor = stringResource(id = R.string.module_author)

                Column(
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = module.name,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                            textDecoration = textDecoration,
                            modifier = Modifier.weight(1f, false)
                        )
                    }

                    Text(
                        text = "$moduleVersion: ${module.version}",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                        textDecoration = textDecoration,
                    )

                    Text(
                        text = "$moduleAuthor: ${module.author}",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                        textDecoration = textDecoration,
                    )

                    // 显示更多模块信息时添加updateJson
                    if (showMoreModuleInfo && module.updateJson.isNotEmpty()) {
                        val updateJsonLabel = stringResource(R.string.module_update_json)
                        Text(
                            text = "$updateJsonLabel: ${module.updateJson}",
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                            textDecoration = textDecoration,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = {
                                        val clipData = ClipData.newPlainText(
                                            "Update JSON URL",
                                            module.updateJson
                                        )
                                        clipboardManager.setPrimaryClip(clipData)
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.module_update_json_copied),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Switch(
                        enabled = !module.update,
                        checked = module.enabled,
                        onCheckedChange = onCheckChanged,
                        interactionSource = if (!module.hasWebUi) interactionSource else null,
                        thumbContent = {
                            if (module.enabled) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            } else
                            {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = module.description,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
                overflow = TextOverflow.Ellipsis,
                maxLines = 4,
                textDecoration = textDecoration,
            )

            if (!isHideTagRow) {
                Spacer(modifier = Modifier.height(12.dp))
                // 文件夹名称和大小标签
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LabelText(
                        label = module.dirId,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                    if (module.metamodule) {
                        LabelText(
                            label = "META",
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                    LabelText(
                        label = sizeStr ?: "0 KB",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(thickness = Dp.Hairline)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (module.hasActionScript) {
                    FilledTonalButton(
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                        enabled = !module.remove && module.enabled,
                        onClick = {
                            navigator.navigate(ExecuteModuleActionScreenDestination(module.dirId))
                            viewModel.markNeedRefresh()
                        },
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null
                        )
                    }
                }

                if (module.hasWebUi) {
                    FilledTonalButton(
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                        enabled = !module.remove && module.enabled,
                        onClick = { onClick(module) },
                        interactionSource = interactionSource,
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.AutoMirrored.Outlined.Wysiwyg,
                            contentDescription = null
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f, true))

                if (updateUrl.isNotEmpty()) {
                    Button(
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                        enabled = !module.remove,
                        onClick = { onUpdate(module) },
                        shape = ButtonDefaults.textShape,
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null
                        )
                    }
                }

                FilledTonalButton(
                    modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                    onClick = { onUninstallClicked(module) },
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    if (!module.remove) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                        )
                    } else {
                        Icon(
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(180f),
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun ModuleItemPreview() {
    val module = ModuleViewModel.ModuleInfo(
        id = "id",
        name = "name",
        version = "version",
        versionCode = 1,
        author = "author",
        description = "I am a test module and i do nothing but show a very long description",
        enabled = true,
        update = true,
        remove = false,
        updateJson = "",
        hasWebUi = true,
        hasActionScript = true,
        metamodule = true,
        actionIconPath = null,
        webUiIconPath = null,
        dirId = "dirId",
        moduleUpdate = null
    )
    ModuleItem(
        viewModel<ModuleViewModel>(),
        EmptyDestinationsNavigator,
        module,
        "",
        {},
        {},
        {},
        {},
        {})
}