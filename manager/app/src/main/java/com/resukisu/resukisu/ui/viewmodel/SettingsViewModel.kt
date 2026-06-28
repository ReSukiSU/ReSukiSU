package com.resukisu.resukisu.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.system.OsConstants
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.appPreferences
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.magica.BootCompletedReceiver
import com.resukisu.resukisu.ui.screen.themeSettings.util.getCurrentAppLocale
import com.resukisu.resukisu.ui.screen.themeSettings.util.toggleLauncherIcon
import com.resukisu.resukisu.ui.theme.BackgroundManager
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.saveAndApplyCustomBackground
import com.resukisu.resukisu.ui.theme.saveCustomBackground
import com.resukisu.resukisu.ui.theme.saveDynamicColorSpec
import com.resukisu.resukisu.ui.theme.saveDynamicColorState
import com.resukisu.resukisu.ui.theme.saveDynamicPaletteStyle
import com.resukisu.resukisu.ui.theme.saveThemeMode
import com.resukisu.resukisu.ui.theme.saveThemeSeedColor
import com.resukisu.resukisu.ui.util.execKsud
import com.resukisu.resukisu.ui.util.getFeaturePersistValue
import com.resukisu.resukisu.ui.util.getFeatureStatus
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class PredictiveBackAnimation(val value: String) {
    None("none"),
    AOSP("aosp"),
    MIUIX("miuix"),
    Scale("scale"),
    KernelSUClassic("ksu_classic");

    companion object {
        fun fromValueOrDefault(value: String) =
            entries.find { it.value == value } ?: Scale
    }
}

enum class PredictiveBackExitDirection(val value: String) {
    FOLLOW_GESTURE("follow_gesture"),
    ALWAYS_RIGHT("always_right"),
    ALWAYS_LEFT("always_left");

    companion object {
        fun fromValueOrDefault(value: String) =
            entries.find { it.value == value } ?: FOLLOW_GESTURE
    }
}

data class SettingsUiState(
    val dpi: Int = 0,
    val predictiveBackAnimation: PredictiveBackAnimation = PredictiveBackAnimation.Scale,
    val predictiveBackExitDirection: PredictiveBackExitDirection = PredictiveBackExitDirection.FOLLOW_GESTURE,

    val themeMode: Int = 0,
    val themeOptions: List<String> = emptyList(),
    val useDynamicColor: Boolean = false,
    val dynamicColorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021,
    val dynamicPaletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val showLanguageDialog: Boolean = false,
    val currentAppLocale: Locale? = null,
    val showThemeColorDialog: Boolean = false,

    val useAltIcon: Boolean = false,

    val cardAlpha: Float = 1f,
    val backgroundDim: Float = 0f,
    val isCustomBackgroundEnabled: Boolean = false,

    val systemDpi: Int = 0,
    val currentDpi: Int = 0,
    val tempDpi: Int = 0,
    val isDpiCustom: Boolean = true,
    val dpiPresets: Map<String, Int> = emptyMap(),

    val checkUpdate: Boolean = true,
    val suCompatMode: Int = 0,
    val suStatus: String = "",
    val kernelUmountStatus: String = "",
    val isKernelUmountEnabled: Boolean = false,
    val autoJailbreakEnabled: Boolean = false,
    val adbRootStatus: String = "",
    val isAdbRootEnabled: Boolean = false,
    val sulogStatus: String = "",
    val isSuLogEnabled: Boolean = false,
    val selinuxHideStatus: String = "",
    val isSelinuxHideEnabled: Boolean = false,
    val defaultUmountModules: Boolean = false,
)

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun initialize(context: Context, systemIsDark: Boolean = isSystemDark(context)) {
        val prefs = context.appPreferences
        val systemDpi = context.resources.displayMetrics.densityDpi
        val currentDpi = prefs.getInt("app_dpi", systemDpi)

        CardConfig.load(context)

        _uiState.update {
            it.copy(
                dpi = prefs.getInt("app_dpi", 0),
                predictiveBackAnimation = PredictiveBackAnimation.fromValueOrDefault(
                    prefs.getString("predictive_back_animation", "") ?: ""
                ),
                predictiveBackExitDirection = PredictiveBackExitDirection.fromValueOrDefault(
                    prefs.getString("predictive_back_exit_direction", "") ?: ""
                ),
                themeMode = when (ThemeConfig.forceDarkMode) {
                    true -> 2
                    false -> 1
                    null -> 0
                },
                themeOptions = listOf(
                    context.getString(R.string.theme_follow_system),
                    context.getString(R.string.theme_light),
                    context.getString(R.string.theme_dark)
                ),
                useDynamicColor = ThemeConfig.useDynamicColor,
                dynamicColorSpec = ThemeConfig.dynamicColorSpec,
                dynamicPaletteStyle = ThemeConfig.dynamicPaletteStyle,
                currentAppLocale = getCurrentAppLocale(context),
                useAltIcon = prefs.getBoolean("use_alt_icon", false),
                cardAlpha = CardConfig.cardAlpha,
                backgroundDim = ThemeConfig.backgroundDim,
                isCustomBackgroundEnabled = ThemeConfig.customBackgroundUri != null,
                systemDpi = systemDpi,
                currentDpi = currentDpi,
                tempDpi = currentDpi,
                isDpiCustom = !dpiPresetValues().contains(currentDpi),
                dpiPresets = dpiPresets(context),
                checkUpdate = prefs.getBoolean("check_update", true),
                autoJailbreakEnabled = prefs.getBoolean("auto_jailbreak", false),
            )
        }

        when (_uiState.value.themeMode) {
            2 -> {
                CardConfig.isUserDarkModeEnabled = true
                CardConfig.isUserLightModeEnabled = false
            }

            1 -> {
                CardConfig.isUserDarkModeEnabled = false
                CardConfig.isUserLightModeEnabled = true
            }

            0 -> {
                CardConfig.isUserDarkModeEnabled = false
                CardConfig.isUserLightModeEnabled = false
            }
        }

        if (_uiState.value.themeMode == 0 && systemIsDark) {
            CardConfig.setThemeDefaults(true)
        }

        CardConfig.save(context)
        loadFeatureSettings(context)
    }

    fun initializeFirstRunSettings(context: Context) {
        val prefs = context.appPreferences
        if (prefs.getBoolean("is_first_run", true)) {
            ThemeConfig.preventBackgroundRefresh = false
            prefs.putBoolean("prevent_background_refresh", false)
            prefs.putBoolean("is_first_run", false)
        }
    }

    fun loadFeatureSettings(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = context.appPreferences
            val currentSuEnabled = runCatching { Natives.isSuEnabled() }.getOrDefault(false)
            val suPersistValue = runCatching { getFeaturePersistValue("su_compat") }.getOrNull()
            val suCompatMode = suPersistValue?.let { value ->
                if (value == 0L) 2 else if (!currentSuEnabled) 1 else 0
            } ?: if (!currentSuEnabled) 1 else 0

            _uiState.update {
                it.copy(
                    checkUpdate = prefs.getBoolean("check_update", true),
                    suCompatMode = suCompatMode,
                    suStatus = runCatching { getFeatureStatus("su_compat") }.getOrDefault(""),
                    kernelUmountStatus = runCatching { getFeatureStatus("kernel_umount") }.getOrDefault(
                        ""
                    ),
                    isKernelUmountEnabled = runCatching { Natives.isKernelUmountEnabled() }.getOrDefault(
                        false
                    ),
                    autoJailbreakEnabled = prefs.getBoolean("auto_jailbreak", false),
                    adbRootStatus = runCatching { getFeatureStatus("adb_root") }.getOrDefault(""),
                    isAdbRootEnabled = runCatching { getFeaturePersistValue("adb_root") == 1L }.getOrDefault(
                        false
                    ),
                    sulogStatus = runCatching { getFeatureStatus("sulog") }.getOrDefault(""),
                    isSuLogEnabled = runCatching { Natives.isSuLogEnabled() }.getOrDefault(false),
                    selinuxHideStatus = runCatching { getFeatureStatus("selinux_hide") }.getOrDefault(
                        ""
                    ),
                    isSelinuxHideEnabled = runCatching { Natives.isSelinuxHideEnabled() }.getOrDefault(
                        false
                    ),
                    defaultUmountModules = runCatching { Natives.isDefaultUmountModules() }.getOrDefault(
                        false
                    ),
                )
            }
        }
    }

    fun setPredictiveBackAnimation(context: Context, animation: PredictiveBackAnimation) {
        context.appPreferences.putString("predictive_back_animation", animation.value)
        _uiState.update { it.copy(predictiveBackAnimation = animation) }
    }

    fun setPredictiveBackExitDirection(context: Context, direction: PredictiveBackExitDirection) {
        context.appPreferences.putString("predictive_back_exit_direction", direction.value)
        _uiState.update { it.copy(predictiveBackExitDirection = direction) }
    }

    fun setThemeColorDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showThemeColorDialog = visible) }
    }

    fun setLanguageDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showLanguageDialog = visible) }
    }

    fun refreshCurrentLocale(context: Context) {
        _uiState.update { it.copy(currentAppLocale = getCurrentAppLocale(context)) }
    }

    fun handleThemeModeChange(context: Context, index: Int) {
        val newThemeMode = when (index) {
            0 -> null
            1 -> false
            2 -> true
            else -> null
        }
        context.saveThemeMode(newThemeMode)
        ThemeConfig.updateTheme(darkMode = newThemeMode)

        when (index) {
            2 -> {
                ThemeConfig.updateTheme(darkMode = true)
                CardConfig.updateThemePreference(darkMode = true, lightMode = false)
                CardConfig.setThemeDefaults(true)
            }

            1 -> {
                ThemeConfig.updateTheme(darkMode = false)
                CardConfig.updateThemePreference(darkMode = false, lightMode = true)
                CardConfig.setThemeDefaults(false)
            }

            0 -> {
                ThemeConfig.updateTheme(darkMode = null)
                CardConfig.updateThemePreference(darkMode = null, lightMode = null)
                CardConfig.setThemeDefaults(isSystemDark(context))
            }
        }
        CardConfig.save(context)
        _uiState.update {
            it.copy(
                themeMode = index,
                cardAlpha = CardConfig.cardAlpha,
                backgroundDim = ThemeConfig.backgroundDim,
            )
        }
    }

    fun handleThemeColorChange(context: Context, seedColor: Int) {
        context.saveThemeSeedColor(seedColor)
        ThemeConfig.updateTheme(seedColor = seedColor)
    }

    fun handleDynamicColorChange(context: Context, enabled: Boolean) {
        context.saveDynamicColorState(enabled)
        ThemeConfig.updateTheme(dynamicColor = enabled)
        _uiState.update { it.copy(useDynamicColor = enabled) }
    }

    fun handleDynamicColorSpecChange(context: Context, spec: ColorSpec.SpecVersion) {
        context.saveDynamicColorSpec(spec)
        _uiState.update { it.copy(dynamicColorSpec = spec) }
    }

    fun handleDynamicPaletteStyleChange(context: Context, style: PaletteStyle) {
        context.saveDynamicPaletteStyle(style)
        _uiState.update { it.copy(dynamicPaletteStyle = style) }
    }

    fun getDpiFriendlyName(context: Context, dpi: Int): String {
        return when (dpi) {
            240 -> context.getString(R.string.dpi_size_small)
            320 -> context.getString(R.string.dpi_size_medium)
            420 -> context.getString(R.string.dpi_size_large)
            560 -> context.getString(R.string.dpi_size_extra_large)
            else -> context.getString(R.string.dpi_size_custom)
        }
    }

    fun updateTempDpi(dpi: Int) {
        _uiState.update {
            it.copy(
                tempDpi = dpi,
                isDpiCustom = !dpiPresetValues().contains(dpi),
            )
        }
    }

    fun handleDpiApply(context: Context) {
        val state = _uiState.value
        if (state.tempDpi == state.currentDpi) return
        context.appPreferences.putInt("app_dpi", state.tempDpi)
        _uiState.update {
            it.copy(
                currentDpi = state.tempDpi,
                dpi = state.tempDpi,
            )
        }
        Toast.makeText(
            context,
            context.getString(R.string.dpi_applied_success, state.tempDpi),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun handleCustomBackground(context: Context, transformedUri: Uri) {
        context.saveAndApplyCustomBackground(transformedUri)
        CardConfig.cardAlpha = 0.55f
        BackgroundManager.saveBackgroundDim(context, 0.3f)
        BackgroundManager.saveEnableBlur(context, true)
        BackgroundManager.saveEnableBlurExp(context, false)
        BackgroundManager.saveUseBackgroundSeedColor(context, true)
        BackgroundManager.saveEnableHighContrastMode(context, false)
        CardConfig.cardElevation = 0.dp
        CardConfig.isCustomBackgroundEnabled = true
        CardConfig.save(context)

        _uiState.update {
            it.copy(
                isCustomBackgroundEnabled = true,
                cardAlpha = CardConfig.cardAlpha,
                backgroundDim = ThemeConfig.backgroundDim,
            )
        }

        Toast.makeText(
            context,
            context.getString(R.string.background_set_success),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun handleRemoveCustomBackground(context: Context) {
        context.saveCustomBackground(null)
        CardConfig.cardAlpha = 1f
        CardConfig.isCustomAlphaSet = false
        CardConfig.isCustomBackgroundEnabled = false
        CardConfig.save(context)
        ThemeConfig.preventBackgroundRefresh = false

        BackgroundManager.saveBackgroundDim(context, 0f)
        BackgroundManager.saveEnableBlur(context, false)
        BackgroundManager.saveEnableBlurExp(context, false)
        BackgroundManager.saveUseBackgroundSeedColor(context, false)
        BackgroundManager.saveEnableHighContrastMode(context, false)

        context.appPreferences.putBoolean("prevent_background_refresh", false)

        _uiState.update {
            it.copy(
                isCustomBackgroundEnabled = false,
                cardAlpha = CardConfig.cardAlpha,
                backgroundDim = ThemeConfig.backgroundDim,
            )
        }

        Toast.makeText(
            context,
            context.getString(R.string.background_removed),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun handleCardAlphaChange(context: Context, newValue: Float) {
        CardConfig.cardAlpha = newValue
        CardConfig.isCustomAlphaSet = true
        context.appPreferences.putBoolean("is_custom_alpha_set", true)
        context.appPreferences.putFloat("card_alpha", newValue)
        _uiState.update { it.copy(cardAlpha = newValue) }
    }

    fun handleBackgroundDimChange(context: Context, newValue: Float) {
        BackgroundManager.saveBackgroundDim(context, newValue)
        _uiState.update { it.copy(backgroundDim = newValue) }
    }

    fun saveCardConfig(context: Context) {
        CardConfig.save(context)
    }

    fun handleIconChange(context: Context, newValue: Boolean) {
        context.appPreferences.putBoolean("use_alt_icon", newValue)
        toggleLauncherIcon(context, newValue)
        _uiState.update { it.copy(useAltIcon = newValue) }
        Toast.makeText(context, context.getString(R.string.icon_switched), Toast.LENGTH_SHORT)
            .show()
    }

    fun handleCheckUpdateChange(context: Context, enabled: Boolean) {
        updateBooleanPref(context, "check_update", enabled) { it.copy(checkUpdate = enabled) }
    }

    fun handleSuCompatModeChange(context: Context, index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = context.appPreferences
            val changed = when (index) {
                0 -> if (Natives.setSuEnabled(true)) {
                    execKsud("feature save", true)
                    prefs.putInt("su_compat_mode", 0)
                    true
                } else false

                1 -> if (Natives.setSuEnabled(true)) {
                    execKsud("feature save", true)
                    if (Natives.setSuEnabled(false)) {
                        prefs.putInt("su_compat_mode", 0)
                        true
                    } else false
                } else false

                2 -> if (Natives.setSuEnabled(false)) {
                    execKsud("feature save", true)
                    prefs.putInt("su_compat_mode", 2)
                    true
                } else false

                else -> false
            }
            if (changed) {
                _uiState.update { it.copy(suCompatMode = index) }
            }
        }
    }

    fun handleKernelUmountChange(checked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (Natives.setKernelUmountEnabled(checked)) {
                execKsud("feature save", true)
                _uiState.update { it.copy(isKernelUmountEnabled = checked) }
            }
        }
    }

    fun handleAutoJailbreakChange(context: Context, value: Boolean) {
        runCatching {
            ksuApp.packageManager.setComponentEnabledSetting(
                ComponentName(ksuApp, BootCompletedReceiver::class.java),
                if (value) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP
            )
        }.onFailure {
            Log.e("Settings", "failed to change boot receiver state to $value", it)
        }
        context.appPreferences.putBoolean("auto_jailbreak", value)
        _uiState.update { it.copy(autoJailbreakEnabled = value) }
    }

    fun handleAdbRootChange(checked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (execKsud("feature set adb_root ${if (checked) 1 else 0}", true)) {
                ShellUtils.fastCmd("setprop ctl.restart adbd")
                execKsud("feature save", true)
            }
            _uiState.update { it.copy(isAdbRootEnabled = checked) }
        }
    }

    fun handleSuLogChange(checked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (Natives.setSuLogEnabled(checked)) {
                execKsud("feature save", true)
                _uiState.update { it.copy(isSuLogEnabled = checked) }
            }
        }
    }

    fun handleSelinuxHideChange(context: Context, checked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = Natives.setSelinuxHideEnabled(checked)
            execKsud("feature save", true)
            _uiState.update { it.copy(isSelinuxHideEnabled = checked) }
            withContext(Dispatchers.Main) {
                when (status) {
                    0 -> Unit
                    -OsConstants.EAGAIN -> {
                        Toast.makeText(
                            context,
                            R.string.settings_selinux_hide_reboot_required,
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    else -> {
                        Toast.makeText(
                            context,
                            ksuApp.getString(R.string.settings_selinux_hide_failed, status),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    fun handleDefaultUmountModulesChange(checked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (Natives.setDefaultUmountModules(checked)) {
                _uiState.update { it.copy(defaultUmountModules = checked) }
            }
        }
    }

    private fun updateBooleanPref(
        context: Context,
        key: String,
        value: Boolean,
        reducer: (SettingsUiState) -> SettingsUiState,
    ) {
        context.appPreferences.putBoolean(key, value)
        _uiState.update(reducer)
    }

    private fun dpiPresets(context: Context): Map<String, Int> {
        return mapOf(
            context.getString(R.string.dpi_size_small) to 240,
            context.getString(R.string.dpi_size_medium) to 320,
            context.getString(R.string.dpi_size_large) to 420,
            context.getString(R.string.dpi_size_extra_large) to 560
        )
    }

    private fun dpiPresetValues(): Set<Int> = setOf(240, 320, 420, 560)

    private fun isSystemDark(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }
}
