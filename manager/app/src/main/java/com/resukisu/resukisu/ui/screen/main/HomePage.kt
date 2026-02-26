package com.resukisu.resukisu.ui.screen.main

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.system.Os
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuSFSConfigScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.KernelSUApplication
import com.resukisu.resukisu.KernelVersion
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.KsuIsValid
import com.resukisu.resukisu.ui.component.WarningCard
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.screen.LabelText
import com.resukisu.resukisu.ui.theme.CardConfig.cardElevation
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.getCardColors
import com.resukisu.resukisu.ui.theme.getCardElevation
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.util.InfoCardItem
import com.resukisu.resukisu.ui.util.checkNewVersion
import com.resukisu.resukisu.ui.util.module.LatestVersionInfo
import com.resukisu.resukisu.ui.util.reboot
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomePage(
    navigator: DestinationsNavigator,
    bottomPadding: Dp,
    hazeState: HazeState?
) {
    val context = LocalContext.current
    val viewModel = viewModel<HomeViewModel>(
        viewModelStoreOwner = context.applicationContext as KernelSUApplication
    )
    val coroutineScope = rememberCoroutineScope()

    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            viewModel.refreshData(context, false)
        }
    }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopBar(
                viewModel = viewModel,
                scrollBehavior = scrollBehavior,
                navigator = navigator,
                hazeState = hazeState
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        snackbarHost = {
            SnackbarHost(
                modifier = Modifier.padding(bottom = bottomPadding),
                hostState = LocalSnackbarHost.current
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = viewModel.isEditingInfoCard,
                enter = scaleIn(),
                exit = scaleOut()
            ) {
                FloatingActionButton(
                    onClick = { viewModel.saveInfoCardEdits(context) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = bottomPadding)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = viewModel.isRefreshing,
            onRefresh = { viewModel.refreshData(context) },
            modifier = (if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
                .fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .align(Alignment.TopCenter),
                    state = pullRefreshState,
                    isRefreshing = viewModel.isRefreshing,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(scrollState)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
                // 状态卡片
                if (viewModel.isCoreDataLoaded) {
                    StatusCard(
                        systemStatus = viewModel.systemStatus,
                        onClickInstall = {
                            navigator.navigate(InstallScreenDestination(preselectedKernelUri = null))
                        }
                    )

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
                    if (viewModel.systemStatus.requireNewKernel) {
                        WarningCard(
                            message = stringResource(id = R.string.require_kernel_version).format(
                                Natives.getSimpleVersionFull(),
                                Natives.MINIMAL_SUPPORTED_KERNEL_FULL
                            )
                        )
                    }

                    if (viewModel.systemStatus.ksuVersion != null && !viewModel.systemStatus.isRootAvailable) {
                        WarningCard(
                            message = stringResource(id = R.string.grant_root_failed)
                        )
                    }

                    // 只有在没有其他警告信息时才显示不兼容内核警告
                    val shouldShowWarnings = viewModel.systemStatus.requireNewKernel ||
                            (viewModel.systemStatus.ksuVersion != null && !viewModel.systemStatus.isRootAvailable)

                    if (Natives.version <= Natives.MINIMAL_NEW_IOCTL_KERNEL && !shouldShowWarnings && viewModel.systemStatus.ksuVersion != null) {
                        IncompatibleKernelCard()
                        Spacer(Modifier.height(12.dp))
                    }
                }

                val checkUpdate = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getBoolean("check_update", true)
                if (checkUpdate) {
                    UpdateCard()
                }

                AnimatedVisibility(
                    visible = viewModel.isExtendedDataLoaded
                ) {
                    InfoCard(
                        viewModel = viewModel,
                        lkmMode = viewModel.systemStatus.lkmMode,
                    )
                }

                // 编辑卡片
                AnimatedVisibility(
                    visible = viewModel.isEditingInfoCard,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    InfoCardEditCard(viewModel = viewModel, lkmMode = viewModel.systemStatus.lkmMode)
                }

                // 链接卡片
                if (InfoCardItem.LINK_CARDS.key !in viewModel.hiddenItems) {
                    DonateCard()
                    LearnMoreCard()
                }

                Spacer(Modifier.height(bottomPadding))
            }
        }
    }
}

@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val latestVersionInfo = LatestVersionInfo()
    val newVersion by produceState(initialValue = latestVersionInfo) {
        value = withContext(Dispatchers.IO) {
            checkNewVersion()
        }
    }

    val currentVersionCode = getManagerVersion(context).second
    val newVersionCode = newVersion.versionCode
    val newVersionUrl = newVersion.downloadUrl
    val changelog = newVersion.changelog

    val uriHandler = LocalUriHandler.current
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)

    AnimatedVisibility(
        visible = newVersionCode > currentVersionCode,
        enter = fadeIn() + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ),
        exit = shrinkVertically() + fadeOut()
    ) {
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

@Composable
fun RebootDropdownItem(@StringRes id: Int, reason: String = "") {
    DropdownMenuItem(
        text = { Text(stringResource(id)) },
        onClick = { reboot(reason) })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    viewModel: HomeViewModel,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    navigator: DestinationsNavigator,
    hazeState: HazeState? = null
) {
    LocalContext.current

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
            Text(
                text = stringResource(R.string.app_name)
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =
                if (ThemeConfig.backgroundImageLoaded) Color.Transparent
                else MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor =
                if (ThemeConfig.backgroundImageLoaded) Color.Transparent
                else MaterialTheme.colorScheme.surfaceContainer,
        ),
        actions = {
            if (viewModel.isCoreDataLoaded) {
                // SuSFS 配置按钮
                if (viewModel.systemInfo.susfsVersionSupported) {
                    IconButton(onClick = {
                        navigator.navigate(SuSFSConfigScreenDestination)
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

                        DropdownMenu(expanded = showDropdown, onDismissRequest = {
                            showDropdown = false
                        }) {
                            RebootDropdownItem(id = R.string.reboot)

                            val pm =
                                LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?
                            @Suppress("DEPRECATION")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm?.isRebootingUserspaceSupported == true) {
                                RebootDropdownItem(id = R.string.reboot_userspace, reason = "userspace")
                            }
                            RebootDropdownItem(id = R.string.reboot_recovery, reason = "recovery")
                            RebootDropdownItem(id = R.string.reboot_bootloader, reason = "bootloader")
                            RebootDropdownItem(id = R.string.reboot_download, reason = "download")
                            RebootDropdownItem(id = R.string.reboot_edl, reason = "edl")
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
    onClickInstall: () -> Unit = {}
) {
    ElevatedCard(
        colors = getCardColors(
            if (systemStatus.ksuVersion != null) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer
        ),
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
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.primary
                            )

                            Spacer(Modifier.width(6.dp))

                            // 架构标签
                            if (Os.uname().machine != "aarch64") {
                                LabelText(
                                    label = Os.uname().machine,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        val hiddenSet = LocalContext.current.getSharedPreferences(
                            "settings",
                            Context.MODE_PRIVATE
                        ).getStringSet("hidden_info_items", emptySet()) ?: emptySet()
                        val isHideVersion = InfoCardItem.VERSION_IN_STATUS.key in hiddenSet

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

                    Column(Modifier.padding(start = 20.dp)) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoCard(
    viewModel: HomeViewModel,
    lkmMode: Boolean?
) {
    val hiddenItems = viewModel.hiddenItems

    ElevatedCard(
        colors = getCardColors(MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = getCardElevation(),
        modifier = Modifier.combinedClickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = {},
            onLongClick = {
                if (!viewModel.isEditingInfoCard) viewModel.startEditingInfoCard()
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            InfoCardContent(
                systemInfo = viewModel.systemInfo,
                hiddenItems = hiddenItems,
                lkmMode = lkmMode,
            )
        }
    }
}

@Composable
private fun InfoCardEditCard(
    viewModel: HomeViewModel,
    lkmMode: Boolean?
) {
    val systemInfo = viewModel.systemInfo
    val availableItems = InfoCardItem.entries.filter { item ->
        when (item) {
            InfoCardItem.HOOK_TYPE -> !systemInfo.susfsEnabled
            InfoCardItem.ACTIVE_MANAGERS -> systemInfo.managersList != null
            InfoCardItem.ZYGISK_IMPLEMENT -> systemInfo.zygiskImplement.isNotEmpty() && systemInfo.zygiskImplement != "None"
            InfoCardItem.META_MODULE -> systemInfo.metaModuleImplement.isNotEmpty() && systemInfo.metaModuleImplement != "None"
            InfoCardItem.KPM_VERSION -> lkmMode == false
            InfoCardItem.SUSFS_VERSION -> systemInfo.susfsEnabled && systemInfo.susfsVersion.isNotEmpty()
            else -> true
        }
    }

    ElevatedCard(
        colors = getCardColors(MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = getCardElevation(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            availableItems.forEach { item ->
                SettingsBaseWidget(
                    icon = item.icon,
                    title = stringResource(item.labelRes),
                    onClick = { _ -> viewModel.toggleEditingItem(item) },
                    noVerticalPadding = true,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Checkbox(
                        checked = item.key !in viewModel.editingHiddenItems,
                        onCheckedChange = { viewModel.toggleEditingItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCardContent(
    systemInfo: HomeViewModel.SystemInfo,
    hiddenItems: Set<String>,
    lkmMode: Boolean?
) {
            @Composable
            fun InfoRow(
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

            if (InfoCardItem.KERNEL_VERSION.key !in hiddenItems) {
                InfoRow(
                    stringResource(R.string.home_kernel),
                    systemInfo.kernelRelease,
                    icon = Icons.Default.Memory,
                )
            }

            if (InfoCardItem.ANDROID_VERSION.key !in hiddenItems) {
                InfoRow(
                    stringResource(R.string.home_android_version),
                    systemInfo.androidVersion,
                    icon = Icons.Default.Android,
                )
            }

            if (InfoCardItem.DEVICE_MODEL.key !in hiddenItems) {
                InfoRow(
                    stringResource(R.string.home_device_model),
                    systemInfo.deviceModel,
                    icon = Icons.Default.PhoneAndroid,
                )
            }

            if (InfoCardItem.MANAGER_VERSION.key !in hiddenItems) {
                InfoRow(
                    stringResource(R.string.home_manager_version),
                    "${systemInfo.managerVersion.first} (${systemInfo.managerVersion.second.toInt()})",
                    icon = Icons.Default.SettingsSuggest,
                )
            }

            if (InfoCardItem.HOOK_TYPE.key !in hiddenItems && !systemInfo.susfsEnabled) {
                InfoRow(
                    stringResource(R.string.home_hook_type),
                    Natives.getHookType(),
                    icon = Icons.Default.Link
                )
            }

            // 活跃管理器
            if (InfoCardItem.ACTIVE_MANAGERS.key !in hiddenItems && systemInfo.managersList != null) {
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

                InfoRow(
                    stringResource(R.string.multi_manager_list),
                    managersText.ifEmpty { stringResource(R.string.no_active_manager) },
                    icon = Icons.Default.Group,
                )
            }

            if (InfoCardItem.SELINUX_STATUS.key !in hiddenItems) {
                InfoRow(
                    stringResource(R.string.home_selinux_status),
                    systemInfo.selinuxStatus,
                    icon = Icons.Default.Security,
                )
            }

            if (InfoCardItem.ZYGISK_IMPLEMENT.key !in hiddenItems && systemInfo.zygiskImplement.isNotEmpty() && systemInfo.zygiskImplement != "None") {
                InfoRow(
                    stringResource(R.string.home_zygisk_implement),
                    systemInfo.zygiskImplement,
                    icon = Icons.Default.Adb,
                )
            }

            if (InfoCardItem.META_MODULE.key !in hiddenItems && systemInfo.metaModuleImplement.isNotEmpty() && systemInfo.metaModuleImplement != "None") {
                InfoRow(
                    stringResource(R.string.home_meta_module_implement),
                    systemInfo.metaModuleImplement,
                    icon = Icons.Default.Extension,
                )
            }

            if (InfoCardItem.KPM_VERSION.key !in hiddenItems && lkmMode == false) {
                val kpmNotSupport =
                    systemInfo.kpmVersion.isEmpty() || systemInfo.kpmVersion.startsWith("Error")
                val displayText = when {
                    kpmNotSupport && Natives.isKPMEnabled() -> {
                        stringResource(
                            R.string.kpm_not_supported,
                            stringResource(R.string.kernel_not_patched)
                        )
                    }

                    kpmNotSupport && !Natives.isKPMEnabled() -> {
                        stringResource(
                            R.string.kpm_not_supported,
                            stringResource(R.string.kernel_not_enabled)
                        )
                    }

                    else -> {
                        stringResource(R.string.kpm_supported, systemInfo.kpmVersion)
                    }
                }

                InfoRow(
                    stringResource(R.string.home_kpm_version),
                    displayText,
                    icon = Icons.Default.Archive
                )
            }

            if (InfoCardItem.SUSFS_VERSION.key !in hiddenItems && systemInfo.susfsEnabled && systemInfo.susfsVersion.isNotEmpty()) {
                val infoText = SuSFSInfoText(systemInfo)

                InfoRow(
                    stringResource(R.string.home_susfs_version),
                    infoText,
                    icon = Icons.Default.Storage
                )
            }
        }

@SuppressLint("ComposableNaming")
@Composable
private fun SuSFSInfoText(systemInfo: HomeViewModel.SystemInfo): String = buildString {
    append(systemInfo.susfsVersion)

    when {
        Natives.getHookType() == "Manual" -> {
            append(" (${stringResource(R.string.manual_hook)})")
        }

        Natives.getHookType() == "Inline" -> {
            append(" (${stringResource(R.string.inline_hook)})")
        }

        else -> {
            append(" (${Natives.getHookType()})")
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

@Composable
private fun IncompatibleKernelCard() {
    val currentKver = remember { Natives.version }
    val threshold   = Natives.MINIMAL_NEW_IOCTL_KERNEL

    val msg = stringResource(
        id = R.string.incompatible_kernel_msg,
        currentKver,
        threshold
    )

    WarningCard(
        message = msg
    )
}