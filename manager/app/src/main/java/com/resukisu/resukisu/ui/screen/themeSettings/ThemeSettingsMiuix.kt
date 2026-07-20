// Miuix Theme screen merged from tiann/KernelSU (github.com/tiann/KernelSU), originally
// authored by YuKongA (github.com/YuKongA); GPL-3.0. The layout (live preview card, theme-mode
// TabRow, flowing cards) follows tiann's Miuix theme sub-screen and is adapted in place to
// ReSukiSU's ThemeConfig/SettingsViewModel and its extra features (custom seed colour, DPI,
// language, hide cards, predictive back). See docs/ATTRIBUTION.md.
package com.resukisu.resukisu.ui.screen.themeSettings

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.screen.themeSettings.component.LanguageSelectionDialog
import com.resukisu.resukisu.ui.screen.themeSettings.util.restartActivity
import com.resukisu.resukisu.ui.theme.BackgroundManager
import com.resukisu.resukisu.ui.theme.LocalEnableBlur
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.isInDarkTheme
import com.resukisu.resukisu.ui.util.BlurredBar
import com.resukisu.resukisu.ui.util.rememberBlurBackdrop
import com.resukisu.resukisu.ui.viewmodel.HomeUiState
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import com.resukisu.resukisu.ui.viewmodel.ModuleUiState
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.viewmodel.PredictiveBackAnimation
import com.resukisu.resukisu.ui.viewmodel.PredictiveBackExitDirection
import com.resukisu.resukisu.ui.viewmodel.SettingsUiState
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix rendering of ReSukiSU's Theme screen. Ported from tiann's Miuix theme sub-screen: a live
 * device-frame preview card, a TabRow for the theme mode, and flowing Cards (no Material-style
 * section headers). ReSukiSU's extra controls (language, custom seed colour + palette, DPI,
 * floating bar, predictive back, hide cards) are layered on with their original callbacks.
 */
@SuppressLint("RestrictedApi")
@Composable
internal fun ThemeSettingsScreenMiuix(
    settingsState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    homeUiState: HomeUiState,
    homeViewModel: HomeViewModel,
    moduleUiState: ModuleUiState,
    moduleViewModel: ModuleViewModel,
    pickImageLauncher: ManagedActivityResultLauncher<String, Uri?>,
    coroutineScope: CoroutineScope,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val isDark = isInDarkTheme()

    Scaffold(
        topBar = {
            TopBar(
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    // Live preview at the top, like tiann's theme screen. The seed follows the
                    // custom colour when dynamic colour is off, otherwise the current primary.
                    Spacer(modifier = Modifier.height(24.dp))
                    ThemePreviewCardMiuix(
                        seedColor = if (settingsState.useDynamicColor) colorScheme.primary else Color(ThemeConfig.seedColor),
                        isDark = isDark,
                        enableFloatingBottomBar = ThemeConfig.enableFloatingBottomBar,
                        enableFloatingBottomBarBlur = ThemeConfig.enableFloatingBottomBarBlur,
                        paletteStyle = settingsState.dynamicPaletteStyle,
                        colorSpec = settingsState.dynamicColorSpec,
                    )
                    Spacer(modifier = Modifier.height(64.dp))

                    // Theme mode as a single-contour segmented control (system / light / dark)
                    // with an animated sliding indicator, instead of a dropdown.
                    TabRowWithContour(
                        tabs = settingsState.themeOptions,
                        selectedTabIndex = settingsState.themeMode.coerceIn(0, 2),
                        onTabSelected = { index -> settingsViewModel.handleThemeModeChange(context, index) },
                        height = 48.dp,
                    )

                    AppearanceSettingsMiuix(
                        state = settingsState,
                        viewModel = settingsViewModel,
                        coroutineScope = coroutineScope,
                    )

                    PredictiveBackCardMiuix(
                        state = settingsState,
                        viewModel = settingsViewModel,
                    )

                    CustomizationSettingsMiuix(
                        homeUiState = homeUiState,
                        moduleUiState = moduleUiState,
                        settingsUiState = settingsState,
                        settingsViewModel = settingsViewModel,
                        homeViewModel = homeViewModel,
                        moduleViewModel = moduleViewModel,
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 16.dp))
                }
            }
        }
    }

    // Miuix dialogs (the Material ThemeSettingsDialogs is gated off in Miuix mode).
    if (settingsState.showThemeColorDialog) {
        ThemeColorDialogMiuix(
            currentSeedColor = ThemeConfig.seedColor,
            onColorSelected = { seedColor ->
                settingsViewModel.handleThemeColorChange(context, seedColor)
                settingsViewModel.setThemeColorDialogVisible(false)
            },
            onDismiss = { settingsViewModel.setThemeColorDialogVisible(false) }
        )
    }
    if (settingsState.showLanguageDialog) {
        LanguageSelectionDialog(
            onLanguageSelected = {
                settingsViewModel.refreshCurrentLocale(context)
                restartActivity(context)
            },
            onDismiss = { settingsViewModel.setLanguageDialogVisible(false) }
        )
    }
}

/**
 * A small device-frame mock that recolours itself with the currently selected seed colour /
 * palette so the effect of a theme change is visible before applying it. Ported from tiann.
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun ThemePreviewCardMiuix(
    seedColor: Color,
    isDark: Boolean,
    enableFloatingBottomBar: Boolean,
    enableFloatingBottomBarBlur: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
) {
    val configuration = LocalConfiguration.current
    val screenRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()

    val dynamicCs = remember(seedColor, isDark, paletteStyle, colorSpec) {
        dynamicColorScheme(
            seedColor = seedColor,
            isDark = isDark,
            style = paletteStyle,
            specVersion = colorSpec,
        )
    }

    val bgColor = dynamicCs.background
    val textColor = dynamicCs.onSurface
    val accentCardColor = dynamicCs.secondaryContainer
    val cardColor = dynamicCs.surfaceContainerHighest
    val navBarColor = dynamicCs.surfaceContainer
    val iconColor = dynamicCs.primary
    val navSelectedColor = colorScheme.onSurfaceContainer
    val navUnselectedColor = colorScheme.onSurfaceContainer.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio)
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .border(1.dp, colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        fontSize = 12.sp,
                        color = textColor
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(65.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentCardColor)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(cardColor)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(cardColor)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.8f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardColor)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(.1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardColor)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(.1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardColor)
                    )
                }
            }

            if (enableFloatingBottomBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (enableFloatingBottomBarBlur) navBarColor.copy(alpha = 0.5f)
                                else navBarColor
                            )
                            .border(0.5.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (it == 0) iconColor else textColor)
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(textColor.copy(alpha = 0.1f))
                    )
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .fillMaxWidth()
                            .background(navBarColor)
                            .padding(top = 2.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (it == 0) navSelectedColor else navUnselectedColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeColorDialogMiuix(
    currentSeedColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember(currentSeedColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(currentSeedColor, it) }
    }
    var hue by remember(currentSeedColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(currentSeedColor) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(currentSeedColor) { mutableFloatStateOf(initialHsv[2]) }
    val selectedColor = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))

    OverlayDialog(
        show = true,
        title = stringResource(R.string.choose_theme_color),
        onDismissRequest = onDismiss,
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(selectedColor))
                )
                Spacer(modifier = Modifier.size(16.dp))
                Text(text = "#%06X".format(selectedColor and 0x00FFFFFF))
            }
            ColorSliderMiuix("H", hue, 0f..360f) { hue = it }
            ColorSliderMiuix("S", saturation, 0f..1f) { saturation = it }
            ColorSliderMiuix("V", value, 0f..1f) { value = it }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.size(12.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { onColorSelected(selectedColor) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    )
}

@Composable
private fun ColorSliderMiuix(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.size(width = 20.dp, height = 24.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AppearanceSettingsMiuix(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    coroutineScope: CoroutineScope,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        val languageSystemDefault = stringResource(R.string.language_system_default)
        val currentLanguageDisplay = state.currentAppLocale?.let { it.getDisplayName(it) }
            ?: languageSystemDefault
        ArrowPreference(
            title = stringResource(R.string.settings_language),
            summary = currentLanguageDisplay,
            startAction = { PrefIcon(Icons.Filled.Translate) },
            onClick = { viewModel.setLanguageDialogVisible(true) }
        )
        SwitchPreference(
            title = stringResource(R.string.dynamic_color_title),
            summary = stringResource(R.string.dynamic_color_summary),
            startAction = { PrefIcon(Icons.Filled.ColorLens) },
            checked = state.useDynamicColor,
            onCheckedChange = { viewModel.handleDynamicColorChange(context, it) }
        )
        // Only the custom seed colour is dynamic-colour-specific; it animates in when dynamic
        // colour is off. Palette style + colour spec apply to both modes, so they stay visible
        // (matching the Material screen).
        AnimatedVisibility(visible = !state.useDynamicColor) {
            BasicComponent(
                title = stringResource(R.string.theme_color),
                summary = "#%06X".format(ThemeConfig.seedColor and 0x00FFFFFF),
                startAction = { PrefIcon(Icons.Filled.Palette) },
                onClick = { viewModel.setThemeColorDialogVisible(true) },
                endActions = {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(ThemeConfig.seedColor))
                    )
                }
            )
        }
        OverlayDropdownPreference(
            title = stringResource(R.string.dynamic_palette_style),
            items = PaletteStyle.entries.map { it.displayName() },
            startAction = { PrefIcon(Icons.Filled.Style) },
            selectedIndex = PaletteStyle.entries.indexOf(state.dynamicPaletteStyle),
            onSelectedIndexChange = { index ->
                viewModel.handleDynamicPaletteStyleChange(
                    context,
                    PaletteStyle.entries.getOrElse(index) { PaletteStyle.TonalSpot }
                )
            }
        )
        OverlayDropdownPreference(
            title = stringResource(R.string.dynamic_color_spec),
            items = ColorSpec.SpecVersion.entries.map { it.displayName() },
            startAction = { PrefIcon(Icons.Filled.DesignServices) },
            selectedIndex = ColorSpec.SpecVersion.entries.indexOf(state.dynamicColorSpec),
            onSelectedIndexChange = { index ->
                viewModel.handleDynamicColorSpecChange(
                    context,
                    ColorSpec.SpecVersion.entries.getOrElse(index) { ColorSpec.SpecVersion.SPEC_2021 }
                )
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            DpiSliderControlsMiuix(state = state, viewModel = viewModel, coroutineScope = coroutineScope)
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        SwitchPreference(
            title = stringResource(R.string.settings_config_enable_blur),
            summary = stringResource(R.string.settings_config_enable_blur_summary),
            startAction = { PrefIcon(Icons.Filled.BlurOn) },
            checked = ThemeConfig.miuixEnableBlur,
            onCheckedChange = { BackgroundManager.saveMiuixEnableBlur(context, it) }
        )
        SwitchPreference(
            title = stringResource(R.string.settings_floating_bottom_bar),
            summary = stringResource(R.string.settings_floating_bottom_bar_summary),
            startAction = { PrefIcon(Icons.Filled.Style) },
            checked = ThemeConfig.enableFloatingBottomBar,
            onCheckedChange = { isChecked ->
                BackgroundManager.saveEnableFloatingBottomBar(context, isChecked)
                if (!isChecked) BackgroundManager.saveEnableFloatingBottomBarBlur(context, false)
            }
        )
        AnimatedVisibility(visible = ThemeConfig.enableFloatingBottomBar) {
            SwitchPreference(
                title = stringResource(R.string.settings_floating_bottom_bar_blur),
                summary = stringResource(R.string.settings_floating_bottom_bar_blur_summary),
                startAction = { PrefIcon(Icons.Filled.Opacity) },
                checked = ThemeConfig.enableFloatingBottomBarBlur,
                onCheckedChange = { BackgroundManager.saveEnableFloatingBottomBarBlur(context, it) }
            )
        }
    }
}

@Composable
private fun PredictiveBackCardMiuix(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val transition = LocalNavAnimatedContentScope.current.transition
    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        OverlayDropdownPreference(
            title = stringResource(R.string.predictive_back_animation),
            items = listOf(
                stringResource(R.string.predictive_back_animation_none),
                stringResource(R.string.predictive_back_animation_aosp),
                stringResource(R.string.predictive_back_animation_miuix),
                stringResource(R.string.predictive_back_animation_scale),
                stringResource(R.string.predictive_back_animation_ksu_classic),
            ),
            startAction = { PrefIcon(Icons.Rounded.Animation) },
            selectedIndex = state.predictiveBackAnimation.ordinal,
            onSelectedIndexChange = { index ->
                transition.setPlaytimeAfterInitialAndTargetStateEstablished(
                    transition.targetState,
                    transition.targetState,
                    transition.playTimeNanos
                )
                PredictiveBackAnimation.entries.getOrNull(index)?.let {
                    viewModel.setPredictiveBackAnimation(context, it)
                }
            }
        )
        AnimatedVisibility(
            visible = state.predictiveBackAnimation == PredictiveBackAnimation.Scale ||
                    state.predictiveBackAnimation == PredictiveBackAnimation.AOSP
        ) {
            OverlayDropdownPreference(
                title = stringResource(R.string.predictive_back_exit_direction),
                items = listOf(
                    stringResource(R.string.predictive_back_exit_direction_follow_gesture),
                    stringResource(R.string.predictive_back_exit_direction_always_right),
                    stringResource(R.string.predictive_back_exit_direction_always_left),
                ),
                startAction = { PrefIcon(Icons.Rounded.SwapHoriz) },
                selectedIndex = state.predictiveBackExitDirection.ordinal,
                onSelectedIndexChange = { index ->
                    PredictiveBackExitDirection.entries.getOrNull(index)?.let {
                        viewModel.setPredictiveBackExitDirection(context, it)
                    }
                }
            )
        }
    }
}

@Composable
private fun CustomizationSettingsMiuix(
    homeUiState: HomeUiState,
    moduleUiState: ModuleUiState,
    settingsUiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    homeViewModel: HomeViewModel,
    moduleViewModel: ModuleViewModel,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        SwitchPreference(
            title = stringResource(R.string.icon_switch_title),
            summary = stringResource(R.string.icon_switch_summary),
            startAction = { PrefIcon(Icons.Filled.Android) },
            checked = settingsUiState.useAltIcon,
            onCheckedChange = { settingsViewModel.handleIconChange(context, it) }
        )
        SwitchPreference(
            title = stringResource(R.string.show_more_module_info),
            summary = stringResource(R.string.show_more_module_info_summary),
            startAction = { PrefIcon(Icons.Filled.Info) },
            checked = moduleUiState.showMoreModuleInfo,
            onCheckedChange = { moduleViewModel.handleShowMoreModuleInfoChange(context, it) }
        )
        SwitchPreference(
            title = stringResource(R.string.simple_mode),
            summary = stringResource(R.string.simple_mode_summary),
            startAction = { PrefIcon(Icons.Filled.Brush) },
            checked = homeUiState.isSimpleMode,
            onCheckedChange = { homeViewModel.handleSimpleModeChange(context, it) }
        )
    }

    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        HideSwitch(R.string.hide_other_info, R.string.hide_other_info_summary, homeUiState.isHideOtherInfo, homeViewModel::handleHideOtherInfoChange)
        HideSwitch(R.string.hide_susfs_status, R.string.hide_susfs_status_summary, homeUiState.isHideSusfsStatus, homeViewModel::handleHideSusfsStatusChange)
        HideSwitch(R.string.hide_zygisk_implement, R.string.hide_zygisk_implement_summary, homeUiState.isHideZygiskImplement, homeViewModel::handleHideZygiskImplementChange)
        HideSwitch(R.string.hide_meta_module_implement, R.string.hide_meta_module_implement_summary, homeUiState.isHideMetaModuleImplement, homeViewModel::handleHideMetaModuleImplementChange)
        HideSwitch(R.string.hide_link_card, R.string.hide_link_card_summary, homeUiState.isHideLinkCard, homeViewModel::handleHideLinkCardChange)
        HideSwitch(R.string.hide_tag_card, R.string.hide_tag_card_summary, moduleUiState.isHideTagRow, moduleViewModel::handleHideTagRowChange)
    }
}

@Composable
private fun HideSwitch(
    titleRes: Int,
    summaryRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        title = stringResource(titleRes),
        summary = stringResource(summaryRes),
        startAction = { PrefIcon(Icons.Filled.VisibilityOff) },
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun DpiSliderControlsMiuix(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    coroutineScope: CoroutineScope,
) {
    val context = LocalContext.current
    val confirmDialog = rememberConfirmDialog()
    val dpiConfirmTitle = stringResource(R.string.dpi_confirm_title)
    val dpiConfirmMessage = stringResource(R.string.dpi_confirm_message, state.currentDpi, state.tempDpi)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)

    // Drive the slider directly from tempDpi (no animateFloatAsState feedback loop,
    // which made the drag feel laggy/jumpy).
    Slider(
        value = state.tempDpi.toFloat(),
        onValueChange = { newValue -> viewModel.updateTempDpi(newValue.toInt()) },
        modifier = Modifier.fillMaxWidth(),
        valueRange = 160f..600f,
        showKeyPoints = true,
        keyPoints = state.dpiPresets.map { (_, dpi) -> dpi.toFloat() },
        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
    )

    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        state.dpiPresets.forEach { (name, dpi) ->
            val isSelected = state.tempDpi == dpi
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) colorScheme.primary else colorScheme.surfaceContainerHigh)
                    .clickable { viewModel.updateTempDpi(dpi) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name,
                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    Text(
        text = if (state.isDpiCustom)
            "${stringResource(R.string.dpi_size_custom)}: ${state.tempDpi}"
        else
            "${viewModel.getDpiFriendlyName(context, state.tempDpi)}: ${state.tempDpi}",
        modifier = Modifier.padding(top = 8.dp),
    )

    TextButton(
        text = stringResource(R.string.dpi_apply_settings),
        onClick = {
            coroutineScope.launch {
                val result = confirmDialog.awaitConfirm(
                    title = dpiConfirmTitle,
                    content = dpiConfirmMessage,
                    confirm = confirmText,
                    dismiss = cancelText,
                )
                if (result != ConfirmResult.Confirmed) return@launch
                viewModel.handleDpiApply(context)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        enabled = state.tempDpi != state.currentDpi,
        colors = ButtonDefaults.textButtonColorsPrimary(),
    )
}

@Composable
private fun PrefIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        modifier = Modifier.padding(end = 6.dp),
        tint = colorScheme.onBackground,
        contentDescription = null,
    )
}

@Composable
private fun TopBar(
    onBack: () -> Unit,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = stringResource(R.string.theme_settings),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    val layoutDirection = LocalLayoutDirection.current
                    Icon(
                        modifier = Modifier.graphicsLayer {
                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                        },
                        imageVector = MiuixIcons.Back,
                        tint = colorScheme.onSurface,
                        contentDescription = null,
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}
