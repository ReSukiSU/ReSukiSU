package zako.zako.zako.zakoui.screen.moreSettings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.MainActivity
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.ksuIsValid
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsDropdownWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.component.settings.SplicedColumnGroup
import com.resukisu.resukisu.ui.component.settings.SplicedGroupScope
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeColors
import com.resukisu.resukisu.ui.theme.ThemeConfig
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import zako.zako.zako.zakoui.screen.moreSettings.component.ColorCircle
import zako.zako.zako.zakoui.screen.moreSettings.component.LanguageSelectionDialog
import zako.zako.zako.zakoui.screen.moreSettings.component.MoreSettingsDialogs
import zako.zako.zako.zakoui.screen.moreSettings.state.MoreSettingsState
import zako.zako.zako.zakoui.screen.moreSettings.util.LocaleHelper
import java.io.File
import kotlin.math.roundToInt

@SuppressLint("LocalContextConfigurationRead", "LocalContextResourcesRead", "ObsoleteSdkInt")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun MoreSettingsScreen(
    navigator: DestinationsNavigator
) {
    // 顶部滚动行为
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val systemIsDark = isSystemInDarkTheme()

    // 创建设置状态管理器
    val settingsState = remember { MoreSettingsState(context, prefs, systemIsDark) }
    val activity = LocalActivity.current as MainActivity
    val settingsHandlers = remember { MoreSettingsHandlers(activity, prefs, settingsState) }

    // 图片选择器
    val cropImageLauncher = rememberLauncherForActivityResult(
        object : ActivityResultContract<Uri, Uri?>() {
            override fun createIntent(context: Context, input: Uri): Intent {
                val tempFile = File(context.cacheDir, "background_crop_cache").apply {
                    parentFile?.mkdirs()
                    delete()
                    createNewFile()
                    deleteOnExit()
                }

                context.contentResolver.openInputStream(input)?.use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                val tempUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )

                return Intent("com.android.camera.action.CROP").apply {
                    setDataAndType(tempUri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    putExtra("crop", "true")

                    val displayMetrics = context.resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels

                    putExtra("aspectX", screenWidth)
                    putExtra("aspectY", screenHeight)
                    putExtra("outputX", screenWidth)
                    putExtra("outputY", screenHeight)

                    putExtra("return-data", false)

                    putExtra(MediaStore.EXTRA_OUTPUT, tempUri)
                }
            }

            override fun parseResult(
                resultCode: Int,
                intent: Intent?
            ): Uri? {
                return intent?.data
            }
        }
    ) { uri: Uri? ->
        uri?.let {
            settingsHandlers.handleCustomBackground(it)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            cropImageLauncher.launch(uri)
        }
    }

    // 初始化设置
    LaunchedEffect(Unit) {
        settingsHandlers.initializeSettings()
    }

    // 各种设置对话框
    MoreSettingsDialogs(
        state = settingsState,
        handlers = settingsHandlers
    )

    val hazeState = if (ThemeConfig.backgroundImageLoaded) rememberHazeState() else null

    val hazeStyle = if (ThemeConfig.backgroundImageLoaded) HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = 0.8f
        ),
        tint = HazeTint(Color.Transparent)
    ) else null

    val collapsedFraction = scrollBehavior.state.collapsedFraction
    val modifier = if (ThemeConfig.backgroundImageLoaded && hazeStyle != null && hazeState != null) {
        Modifier.hazeEffect(hazeState) {
            style = hazeStyle
            noiseFactor = 0f
            blurRadius = 30.dp
            alpha = collapsedFraction
        }
    }
    else Modifier

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = modifier,
                title = {
                    Text(
                        text = stringResource(R.string.more_settings)
                    )
                },
                navigationIcon = {
                    AppBackButton(
                        onClick = {
                            navigator.popBackStack()
                        }
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
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { paddingValues ->
        LazyColumn(
            modifier = if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            item {
                Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
            }

            item {
                // 外观设置
                AppearanceSettings(
                    state = settingsState,
                    handlers = settingsHandlers,
                    pickImageLauncher = pickImageLauncher,
                    coroutineScope = coroutineScope
                )
            }

            item {
                // 自定义设置
                CustomizationSettings(
                    state = settingsState,
                    handlers = settingsHandlers
                )
            }

            if (ksuIsValid()) {
                // 高级设置
                item {
                    AdvancedSettings(
                        state = settingsState,
                        handlers = settingsHandlers
                    )
                }
            }

            // 信息卡片显示提示
            item {
                SplicedColumnGroup(title = stringResource(R.string.info_card_display_settings)) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.Filled.VisibilityOff,
                            title = stringResource(R.string.info_card_display_settings),
                            description = stringResource(R.string.info_card_display_settings_summary),
                        ) {}
                    }
                }
            }

            item {
                // 系统导航栏padding计算
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}

@Composable
private fun AppearanceSettings(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    pickImageLauncher: ManagedActivityResultLauncher<String, Uri?>,
    coroutineScope: CoroutineScope
) {
    SplicedColumnGroup(title = stringResource(R.string.appearance_settings)) {
        item {
            // 语言设置
            LanguageSetting(state = state)
        }

        item {
            // 主题模式
            SettingsDropdownWidget(
                icon = Icons.Default.DarkMode,
                title = stringResource(R.string.theme_mode),
                items = state.themeOptions,
                selectedIndex = state.themeMode,
                onSelectedIndexChange = { index ->
                    handlers.handleThemeModeChange(index)
                }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                // 动态颜色开关
                SettingsSwitchWidget(
                    icon = Icons.Filled.ColorLens,
                    title = stringResource(R.string.dynamic_color_title),
                    description = stringResource(R.string.dynamic_color_summary),
                    checked = state.useDynamicColor,
                    onCheckedChange = handlers::handleDynamicColorChange
                )
            }
        }

        item(
            visible = !state.useDynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            // 主题色选择
            ThemeColorSelection(state = state)
        }

        item {
            // DPI 设置
            DpiSettings(state = state, handlers = handlers, coroutineScope = coroutineScope)
        }

        item {
            // 自定义背景设置
            CustomBackgroundSettings(
                state = state,
                handlers = handlers,
                pickImageLauncher = pickImageLauncher,
                coroutineScope = coroutineScope
            )
        }
    }
}

@Composable
private fun CustomizationSettings(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers
) {
    SplicedColumnGroup(title = stringResource(R.string.custom_settings)) {
        item {
            // 图标切换
            SettingsSwitchWidget(
                icon = Icons.Default.Android,
                title = stringResource(R.string.icon_switch_title),
                description = stringResource(R.string.icon_switch_summary),
                checked = state.useAltIcon,
                onCheckedChange = handlers::handleIconChange
            )
        }

        item {
            // 显示更多模块信息
            SettingsSwitchWidget(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.show_more_module_info),
                description = stringResource(R.string.show_more_module_info_summary),
                checked = state.showMoreModuleInfo,
                onCheckedChange = handlers::handleShowMoreModuleInfoChange
            )
        }
    }
}

@Composable
private fun AdvancedSettings(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers
) {

    SplicedColumnGroup(title = stringResource(R.string.advanced_settings)) {
        item {
            // SELinux 开关
            SettingsSwitchWidget(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.selinux),
                description = if (state.selinuxEnabled)
                    stringResource(R.string.selinux_enabled) else
                    stringResource(R.string.selinux_disabled),
                checked = state.selinuxEnabled,
                onCheckedChange = handlers::handleSelinuxChange
            )
        }

        item {
            // 动态管理器设置
            if (Natives.version >= Natives.MINIMAL_SUPPORTED_DYNAMIC_MANAGER && Natives.version >= Natives.MINIMAL_NEW_IOCTL_KERNEL) {
                SettingsJumpPageWidget(
                    icon = Icons.Filled.Security,
                    title = stringResource(R.string.dynamic_manager_title),
                    description = if (state.isDynamicSignEnabled) {
                        stringResource(
                            R.string.dynamic_manager_enabled_summary,
                            state.dynamicSignSize
                        )
                    } else {
                        stringResource(R.string.dynamic_manager_disabled)
                    },
                    onClick = { state.showDynamicSignDialog = true }
                )
            }
        }
    }
}

@Composable
private fun ThemeColorSelection(state: MoreSettingsState) {
    SettingsBaseWidget(
        icon = Icons.Default.Palette,
        title = stringResource(R.string.theme_color),
        description = when (ThemeConfig.currentTheme) {
            is ThemeColors.Green -> stringResource(R.string.color_green)
            is ThemeColors.Purple -> stringResource(R.string.color_purple)
            is ThemeColors.Orange -> stringResource(R.string.color_orange)
            is ThemeColors.Pink -> stringResource(R.string.color_pink)
            is ThemeColors.Gray -> stringResource(R.string.color_gray)
            is ThemeColors.Yellow -> stringResource(R.string.color_yellow)
            else -> stringResource(R.string.color_default)
        },
        onClick = { state.showThemeColorDialog = true },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            val theme = ThemeConfig.currentTheme
            val isDark = isSystemInDarkTheme()

            ColorCircle(
                color = if (isDark) theme.primaryDark else theme.primaryLight,
                isSelected = false,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            ColorCircle(
                color = if (isDark) theme.secondaryDark else theme.secondaryLight,
                isSelected = false,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            ColorCircle(
                color = if (isDark) theme.tertiaryDark else theme.tertiaryLight,
                isSelected = false,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun DpiSettings(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    coroutineScope: CoroutineScope
) {
    SettingsBaseWidget(
        icon = Icons.Default.FormatSize,
        title = stringResource(R.string.app_dpi_title),
        description = stringResource(R.string.app_dpi_summary),
        onClick = {},
    ) {
        Text(
            text = handlers.getDpiFriendlyName(state.tempDpi),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    // DPI 滑动条和控制
    DpiSliderControls(state = state, handlers = handlers, coroutineScope = coroutineScope)
}

@Composable
private fun DpiSliderControls(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    coroutineScope: CoroutineScope
) {
    val confirmDialog = rememberConfirmDialog()
    val dpiConfirmTitle = stringResource(R.string.dpi_confirm_title)
    val dpiConfirmMessage =
        stringResource(R.string.dpi_confirm_message, state.currentDpi, state.tempDpi)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val sliderValue by animateFloatAsState(
            targetValue = state.tempDpi.toFloat(),
            label = "DPI Slider Animation"
        )

        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                state.tempDpi = newValue.toInt()
                state.isDpiCustom = !state.dpiPresets.containsValue(state.tempDpi)
            },
            valueRange = 160f..600f,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // DPI 预设按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            state.dpiPresets.forEach { (name, dpi) ->
                val isSelected = state.tempDpi == dpi
                val buttonColor = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(buttonColor)
                        .clickable {
                            state.tempDpi = dpi
                            state.isDpiCustom = false
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text(
            text = if (state.isDpiCustom)
                "${stringResource(R.string.dpi_size_custom)}: ${state.tempDpi}"
            else
                "${handlers.getDpiFriendlyName(state.tempDpi)}: ${state.tempDpi}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )

        Button(
            onClick = {
                coroutineScope.launch {
                    val confirmResult = confirmDialog.awaitConfirm(
                        title = dpiConfirmTitle,
                        content = dpiConfirmMessage,
                        confirm = confirmText,
                        dismiss = cancelText
                    )

                    if (confirmResult != ConfirmResult.Confirmed) return@launch

                    handlers.handleDpiApply()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            enabled = state.tempDpi != state.currentDpi
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.dpi_apply_settings))
        }
    }
}

@Composable
private fun CustomBackgroundSettings(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    pickImageLauncher: ManagedActivityResultLauncher<String, Uri?>,
    coroutineScope: CoroutineScope
) {
    // 自定义背景开关
    SettingsSwitchWidget(
        icon = Icons.Filled.Wallpaper,
        title = stringResource(id = R.string.settings_custom_background),
        description = stringResource(id = R.string.settings_custom_background_summary),
        checked = state.isCustomBackgroundEnabled,
        onCheckedChange = { isChecked ->
            if (isChecked) {
                pickImageLauncher.launch("image/*")
            } else {
                handlers.handleRemoveCustomBackground()
            }
        }
    )

    // 透明度和亮度调节
    AnimatedVisibility(
        visible = ThemeConfig.customBackgroundUri != null,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        BackgroundAdjustmentControls(
            state = state,
            handlers = handlers,
            coroutineScope = coroutineScope
        )
    }
}

@Composable
private fun BackgroundAdjustmentControls(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    coroutineScope: CoroutineScope
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        // 透明度滑动条
        AlphaSlider(state = state, handlers = handlers, coroutineScope = coroutineScope)

        // 亮度调节滑动条
        DimSlider(state = state, handlers = handlers, coroutineScope = coroutineScope)
    }
}

@Composable
private fun AlphaSlider(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    coroutineScope: CoroutineScope
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Icon(
            Icons.Filled.Opacity,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.settings_card_alpha),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${(state.cardAlpha * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
        )
    }

    val alphaSliderValue by animateFloatAsState(
        targetValue = state.cardAlpha,
        label = "Alpha Slider Animation"
    )

    Slider(
        value = alphaSliderValue,
        onValueChange = { newValue ->
            handlers.handleCardAlphaChange(newValue)
        },
        onValueChangeFinished = {
            coroutineScope.launch(Dispatchers.IO) {
                CardConfig.save(handlers.activity)
            }
        },
        valueRange = 0f..1f,
        steps = 20,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
private fun DimSlider(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    coroutineScope: CoroutineScope
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    ) {
        Icon(
            Icons.Filled.LightMode,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.settings_background_dim),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${(state.backgroundDim * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
        )
    }

    val dimSliderValue by animateFloatAsState(
        targetValue = state.backgroundDim,
        label = "Dim Slider Animation"
    )

    Slider(
        value = dimSliderValue,
        onValueChange = { newValue ->
            handlers.handleBackgroundDimChange(newValue)
        },
        onValueChangeFinished = {
            coroutineScope.launch(Dispatchers.IO) {
                CardConfig.save(handlers.activity)
            }
        },
        valueRange = 0f..1f,
        steps = 20,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
private fun LanguageSetting(state: MoreSettingsState) {
    val context = LocalContext.current
    val language = stringResource(id = R.string.settings_language)

    // Compute display name based on current app locale
    val currentLanguageDisplay = remember(state.currentAppLocale) {
        val locale = state.currentAppLocale
        if (locale != null) {
            locale.getDisplayName(locale)
        } else {
            context.getString(R.string.language_system_default)
        }
    }

    SettingsJumpPageWidget(
        icon = Icons.Filled.Translate,
        title = language,
        description = currentLanguageDisplay,
        onClick = { state.showLanguageDialog = true }
    )

    // Language Selection Dialog
    if (state.showLanguageDialog) {
        LanguageSelectionDialog(
            onLanguageSelected = { newLocale ->
                // Update local state immediately
                state.currentAppLocale = LocaleHelper.getCurrentAppLocale(context)
                // Apply locale change immediately for Android < 13
                LocaleHelper.restartActivity(context)
            },
            onDismiss = { state.showLanguageDialog = false }
        )
    }
}