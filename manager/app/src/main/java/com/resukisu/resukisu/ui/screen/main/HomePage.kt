package com.resukisu.resukisu.ui.screen.main

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.system.Os
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.KernelVersion
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.magica.MagicaService
import com.resukisu.resukisu.ui.component.KsuIsValid
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.WarningCard
import com.resukisu.resukisu.ui.component.ksuIsValid
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.rememberLoadingDialog
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.screen.LabelText
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.CardConfig.cardElevation
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.theme.getCardColors
import com.resukisu.resukisu.ui.theme.getCardElevation
import com.resukisu.resukisu.ui.theme.renderBackgroundBlur
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.module.LatestVersionInfo
import com.resukisu.resukisu.ui.util.reboot
import com.resukisu.resukisu.ui.viewmodel.HomeUiState
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomePage(
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val viewModel = viewModel<HomeViewModel>(
        viewModelStoreOwner = ksuApp
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.awaitInitialData(context)
    }

    if (!uiState.isInitialDataLoaded) return

    val pullRefreshState = rememberPullToRefreshState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val scrollState = rememberScrollState()
    val navigator = LocalNavigator.current
    val loadingDialog = rememberLoadingDialog()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopBar(
                uiState = uiState,
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        snackbarHost = {
            SwipeableSnackbarHost(
                modifier = Modifier.padding(bottom = bottomPadding),
                hostState = LocalSnackbarHost.current
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshData(context, refreshUI = true) },
            modifier = Modifier
                .fillMaxSize()
                .blurSource(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .align(Alignment.TopCenter),
                    state = pullRefreshState,
                    isRefreshing = uiState.isRefreshing,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(scrollState)
                    .padding(
                        top = innerPadding.calculateTopPadding() + 2.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 状态卡片
                if (uiState.isCoreDataLoaded) {
                    StatusCard(
                        systemStatus = uiState.systemStatus,
                        isHideVersion = uiState.isHideVersion,
                        onClickInstall = {
                            navigator.push(Route.Install(preselectedKernelUri = null))
                        },
                        onClickJailbreak = {
                            loadingDialog.showLoading()
                            context.startService(Intent(context, MagicaService::class.java))
                            // Manager will be force-stopped and restarted by late-load on success.
                            // If that doesn't happen within timeout, jailbreak likely failed.
                            scope.launch(Dispatchers.IO) {
                                delay(30_000)
                                withContext(Dispatchers.Main) {
                                    loadingDialog.hide()
                                    Toast.makeText(
                                        context,
                                        R.string.jailbreak_timeout,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )

                    if (uiState.systemStatus.requireNewKernel) {
                        WarningCard(
                            message = stringResource(
                                id = R.string.incompatible_kernel_msg,
                                Natives.version,
                                Natives.MINIMAL_SUPPORTED_KERNEL
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    // 警告信息
                    if (BuildConfig.DEBUG) {
                        WarningCard(
                            message = stringResource(R.string.debug_version_notice),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    if (!uiState.systemStatus.isOfficialSignature) {
                        WarningCard(
                            message = stringResource(
                                R.string.unofficial_version_notice,
                                stringResource(R.string.app_name)
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    if (BuildConfig.IS_PR_BUILD || Natives.isPrBuild) {
                        WarningCard(
                            message = stringResource(
                                id = R.string.home_pr_build_warning
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    if (uiState.systemStatus.kernelPatchImplement == Natives.KernelPatchImplement.KERNEL_PATCH_OFFICIAL) {
                        WarningCard(
                            message = stringResource(
                                R.string.conflict_with_apatch,
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    if (uiState.systemStatus.ksuVersion != null && !uiState.systemStatus.isRootAvailable) {
                        WarningCard(
                            message = stringResource(id = R.string.grant_root_failed),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }

                UpdateCard(uiState.latestVersionInfo)

                if (uiState.isExtendedDataLoaded) {
                    InfoCard(
                        systemInfo = uiState.systemInfo,
                        isSimpleMode = uiState.isSimpleMode,
                        isHideSusfsStatus = uiState.isHideSusfsStatus,
                        isHideZygiskImplement = uiState.isHideZygiskImplement,
                        isHideMetaModuleImplement = uiState.isHideMetaModuleImplement,
                    )
                }

                // 链接卡片
                if (!uiState.isSimpleMode && !uiState.isHideLinkCard) {
                    DonateCard()
                    LearnMoreCard()
                }

                Spacer(Modifier.height(bottomPadding))
            }
        }
    }
}

@Composable
fun UpdateCard(newVersion: LatestVersionInfo) {
    val context = LocalContext.current
    val currentVersionCode = getManagerVersion(context).second
    val newVersionCode = newVersion.versionCode
    val newVersionUrl = newVersion.downloadUrl
    val changelog = newVersion.changelog

    val uriHandler = LocalUriHandler.current
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)

    if (newVersionCode > currentVersionCode) {
        val updateDialog = rememberConfirmDialog(onConfirm = { uriHandler.openUri(newVersionUrl) })
        WarningCard(
            message = stringResource(id = R.string.new_version_available).format(newVersionCode),
            color = MaterialTheme.colorScheme.outlineVariant,
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                if (changelog.isEmpty()) {
                    uriHandler.openUri(newVersionUrl)
                } else {
                    updateDialog.showConfirm(
                        title = title,
                        content = changelog,
                        markdown = true,
                        confirm = updateText
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RebootDropdownItems(items: Map<Int, String>) {
    items.onEachIndexed { index, (id, reason) ->
        DropdownMenuItem(
            selected = false,
            text = { Text(stringResource(id)) },
            onClick = { reboot(reason) },
            shapes = MenuDefaults.itemShape(
                index = index,
                count = items.size
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    uiState: HomeUiState,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val navigator = LocalNavigator.current

    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(),
        title = {
            Text(
                text = stringResource(R.string.app_name)
            )
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
                    MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
        ),
        actions = {
            if (uiState.isCoreDataLoaded) {
                // SuSFS 配置按钮
                if (uiState.systemInfo.susfsVersionSupported) {
                    IconButton(onClick = {
                        navigator.push(Route.SuSFSConfig)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = stringResource(R.string.susfs_config_setting_title)
                        )
                    }
                }

                // 重启按钮
                var showDropdown by remember { mutableStateOf(false) }
                KsuIsValid {
                    IconButton(onClick = {
                        showDropdown = true
                    }) {
                        Icon(
                            imageVector = Icons.Filled.PowerSettingsNew,
                            contentDescription = stringResource(id = R.string.reboot)
                        )

                        DropdownMenuPopup(expanded = showDropdown, onDismissRequest = {
                            showDropdown = false
                        }) {
                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShapes()
                            ) {
                                val pm =
                                    LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?
                                var methods = mapOf(
                                    R.string.reboot to "",
                                    R.string.reboot_soft to "soft_reboot",
                                    R.string.reboot_recovery to "recovery",
                                    R.string.reboot_bootloader to "bootloader",
                                    R.string.reboot_download to "download",
                                    R.string.reboot_edl to "edl"
                                )

                                @Suppress("DEPRECATION")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm?.isRebootingUserspaceSupported == true) {
                                    methods = methods + (R.string.reboot_userspace to "userspace")
                                }

                                RebootDropdownItems(methods)
                            }
                        }
                    }
                }
            }
        },
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun StatusCard(
    systemStatus: HomeViewModel.SystemStatus,
    isHideVersion: Boolean = false,
    onClickInstall: () -> Unit = {},
    onClickJailbreak: () -> Unit = {}
) {
    val containerColor =
        if (systemStatus.ksuVersion != null)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.errorContainer

    ElevatedCard(
        modifier = Modifier
            .clip(CardDefaults.elevatedShape)
            .renderBackgroundBlur(containerColor),
        colors = getCardColors(containerColor),
        elevation = getCardElevation(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (systemStatus.isRootAvailable || systemStatus.kernelVersion.isGKI()) {
                        onClickInstall()
                    }
                }
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                systemStatus.ksuVersion != null -> {

                    val workingModeText = when {
                        Natives.isSafeMode -> stringResource(id = R.string.safe_mode)
                        else -> stringResource(id = R.string.home_working)
                    }

                    val workingModeSurfaceText = when {
                        systemStatus.lkmMode == true -> "LKM"
                        else -> "Built-in"
                    }

                    Icon(
                        Icons.Outlined.TaskAlt,
                        contentDescription = stringResource(R.string.home_working),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(
                                horizontal = 4.dp
                            ),
                    )

                    Column(Modifier.padding(start = 20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = workingModeText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            Spacer(Modifier.width(8.dp))

                            // 工作模式标签
                            LabelText(
                                label = workingModeSurfaceText,
                                containerColor = MaterialTheme.colorScheme.primary
                            )

                            if (Natives.isLateLoadMode) {
                                Spacer(Modifier.width(6.dp))
                                LabelText(
                                    label = stringResource(id = R.string.jailbreak_mode),
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            }

                            // 架构标签
                            if (Os.uname().machine != "aarch64") {
                                Spacer(Modifier.width(6.dp))
                                LabelText(
                                    label = Os.uname().machine,
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (!isHideVersion) {
                            Spacer(Modifier.height(4.dp))
                            systemStatus.ksuFullVersion?.let {
                                Text(
                                    text = stringResource(R.string.home_working_version, it),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }

                systemStatus.kernelVersion.isGKI() -> {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = stringResource(R.string.home_not_installed),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(
                                horizontal = 4.dp
                            ),
                    )

                    Column(Modifier
                        .padding(start = 20.dp)
                        .weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_not_installed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_click_to_install),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    if (systemStatus.isSELinuxPermissive) {
                        Button(
                            onClick = onClickJailbreak,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(stringResource(R.string.home_jailbreak))
                        }
                    }
                }

                else -> {
                    Icon(
                        Icons.Outlined.Block,
                        contentDescription = stringResource(R.string.home_unsupported),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(
                                horizontal = 4.dp
                            ),
                    )

                    Column(Modifier.padding(start = 20.dp)) {
                        Text(
                            text = stringResource(R.string.home_unsupported),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_unsupported_reason),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LearnMoreCard() {
    val uriHandler = LocalUriHandler.current
    val url = stringResource(R.string.home_learn_kernelsu_url)

    ElevatedCard(
        modifier = Modifier
            .clip(CardDefaults.elevatedShape)
            .renderBackgroundBlur(),
        colors = getCardColors(MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    uriHandler.openUri(url)
                }
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_learn_kernelsu),
                    style = MaterialTheme.typography.titleSmall,
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_click_to_learn_kernelsu),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun DonateCard() {
    val uriHandler = LocalUriHandler.current

    ElevatedCard(
        modifier = Modifier
            .clip(CardDefaults.elevatedShape)
            .renderBackgroundBlur(),
        colors = getCardColors(MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = getCardElevation(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    uriHandler.openUri("https://patreon.com/weishu")
                }
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_support_title),
                    style = MaterialTheme.typography.titleSmall,
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_support_content),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    systemInfo: HomeViewModel.SystemInfo,
    isSimpleMode: Boolean,
    isHideSusfsStatus: Boolean,
    isHideZygiskImplement: Boolean,
    isHideMetaModuleImplement: Boolean
) {
    ElevatedCard(
        modifier = Modifier
            .clip(CardDefaults.elevatedShape)
            .renderBackgroundBlur(),
        colors = getCardColors(MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = getCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            @Composable
            fun InfoCardItem(
                label: String,
                content: String,
                icon: ImageVector? = null,
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier
                                .size(28.dp)
                                .padding(vertical = 4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodyMedium,
                            softWrap = true
                        )
                    }
                }
            }

            InfoCardItem(
                stringResource(R.string.home_kernel),
                systemInfo.kernelRelease,
                icon = Icons.Default.Memory,
            )

            if (!isSimpleMode) {
                InfoCardItem(
                    stringResource(R.string.home_android_version),
                    systemInfo.androidVersion,
                    icon = Icons.Default.Android,
                )
            }

            InfoCardItem(
                stringResource(R.string.home_device_model),
                systemInfo.deviceModel,
                icon = Icons.Default.PhoneAndroid,
            )

            InfoCardItem(
                stringResource(R.string.home_manager_version),
                "${systemInfo.managerVersion.first} (${systemInfo.managerVersion.second.toInt()})",
                icon = Icons.Default.SettingsSuggest,
            )

            if (!isSimpleMode && ksuIsValid()) {
                InfoCardItem(
                    stringResource(R.string.home_hook_type),
                    Natives.getHookType(),
                    icon = Icons.Default.Link
                )
            }

            // 活跃管理器
            if (!isSimpleMode && systemInfo.managersList != null) {
                val signatureMap =
                    systemInfo.managersList.managers.groupBy { it.signatureIndex }

                val managersText = buildString {
                    signatureMap.toSortedMap().forEach { (signatureIndex, managers) ->
                        append(managers.joinToString(", ") { "UID: ${it.uid}" })
                        append(" ")
                        append(
                            when (signatureIndex) {
                                0 -> "(${stringResource(R.string.app_name)})"
                                255 -> "(${stringResource(R.string.dynamic_managerature)})"
                                else -> if (signatureIndex >= 1) "(${
                                    stringResource(
                                        R.string.signature_index,
                                        signatureIndex
                                    )
                                })" else "(${stringResource(R.string.unknown_signature)})"
                            }
                        )
                        append(" | ")
                    }
                }.trimEnd(' ', '|')

                InfoCardItem(
                    stringResource(R.string.multi_manager_list),
                    managersText.ifEmpty { stringResource(R.string.no_active_manager) },
                    icon = Icons.Default.Group,
                )
            }

            InfoCardItem(
                stringResource(R.string.home_selinux_status),
                systemInfo.selinuxStatus,
                icon = Icons.Default.Security,
            )

            val seccompDisplay = when (systemInfo.seccompStatus) {
                -1 -> stringResource(R.string.seccomp_status_not_supported)
                0 -> stringResource(R.string.seccomp_status_disabled)
                1 -> stringResource(R.string.seccomp_status_strict)
                2 -> stringResource(R.string.seccomp_status_filter)
                else -> stringResource(R.string.seccomp_status_unknown)
            }

            InfoCardItem(
                stringResource(R.string.home_seccomp_status),
                seccompDisplay,
                icon = Icons.Default.LocalPolice,
            )

            if (!isHideZygiskImplement && !isSimpleMode && systemInfo.zygiskImplement.isNotEmpty() && systemInfo.zygiskImplement != "None") {
                InfoCardItem(
                    stringResource(R.string.home_zygisk_implement),
                    systemInfo.zygiskImplement,
                    icon = Icons.Default.Adb,
                )
            }

            if (!isHideMetaModuleImplement && !isSimpleMode && systemInfo.metaModuleImplement.isNotEmpty() && systemInfo.metaModuleImplement != "None") {
                InfoCardItem(
                    stringResource(R.string.home_meta_module_implement),
                    systemInfo.metaModuleImplement,
                    icon = Icons.Default.Extension,
                )
            }


            if (!isSimpleMode && !isHideSusfsStatus && systemInfo.susfsEnabled && systemInfo.susfsVersion.isNotEmpty()) {
                InfoCardItem(
                    stringResource(R.string.home_susfs_version),
                    systemInfo.susfsVersion,
                    icon = Icons.Default.Storage
                )
            }
        }
    }
}

fun getManagerVersion(context: Context): Pair<String, Long> {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)!!
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    return Pair(packageInfo.versionName!!, versionCode)
}

@Preview
@Composable
private fun StatusCardPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusCard(
            HomeViewModel.SystemStatus(
                isManager = true,
                ksuVersion = 1,
                lkmMode = null,
                kernelVersion = KernelVersion(5, 10, 101),
                isRootAvailable = true
            )
        )

        StatusCard(
            HomeViewModel.SystemStatus(
                isManager = true,
                ksuVersion = 30000,
                lkmMode = true,
                kernelVersion = KernelVersion(5, 10, 101),
                isRootAvailable = true
            )
        )

        StatusCard(
            HomeViewModel.SystemStatus(
                isManager = false,
                ksuVersion = null,
                lkmMode = true,
                kernelVersion = KernelVersion(5, 10, 101),
                isRootAvailable = false
            )
        )

        StatusCard(
            HomeViewModel.SystemStatus(
                isManager = false,
                ksuVersion = null,
                lkmMode = false,
                kernelVersion = KernelVersion(4, 10, 101),
                isRootAvailable = false
            )
        )
    }
}
