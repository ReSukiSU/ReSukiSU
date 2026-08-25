package com.resukisu.resukisu.data.settings

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.resukisu.resukisu.data.AppSettingsRepository
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.data.theme.ThemeRepository
import com.resukisu.resukisu.domain.model.AppearanceSetting
import com.resukisu.resukisu.domain.model.PlatformFeatureStatus
import com.resukisu.resukisu.domain.model.PlatformSetting
import com.resukisu.resukisu.domain.model.SettingsPlatformSnapshot
import com.resukisu.resukisu.magica.BootCompletedReceiver
import com.resukisu.resukisu.ui.theme.BackgroundManager
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsPlatformRepository(
    private val application: Application,
    private val settings: AppSettingsRepository,
    private val themeConfig: ThemeConfig,
    private val themeRepository: ThemeRepository,
    private val backgroundManager: BackgroundManager,
    private val cardConfig: CardConfig,
    private val localeHelper: LocaleHelper,
    private val ksuCliRepository: KsuCliRepository,
) {
    fun load(): SettingsPlatformSnapshot {
        themeConfig.forceDarkMode = themeRepository.loadThemeMode()
        themeConfig.seedColor = themeRepository.loadSeedColor()
        themeConfig.useDynamicColor = themeRepository.loadDynamicColorState()
        themeConfig.dynamicColorSpec = themeRepository.loadDynamicColorSpec()
        themeConfig.dynamicPaletteStyle = themeRepository.loadDynamicPaletteStyle(
            themeConfig.dynamicColorSpec,
        )
        themeConfig.useBuiltinMonoFont = settings.getBoolean("use_builtin_monospace_font", false)
        backgroundManager.loadCustomBackground()
        val systemDpi = application.resources.displayMetrics.densityDpi
        val currentDpi = settings.getInt("app_dpi", systemDpi)
        val customBackground = settings.getString("custom_background", null) != null
        val themeMode = when (themeConfig.forceDarkMode) {
            true -> 2
            false -> 1
            null -> 0
        }
        cardConfig.load()
        cardConfig.updateBackground(customBackground)
        when (themeMode) {
            2 -> cardConfig.updateThemePreference(darkMode = true, lightMode = false)
            1 -> cardConfig.updateThemePreference(darkMode = false, lightMode = true)
            else -> cardConfig.updateThemePreference(darkMode = null, lightMode = null)
        }
        if (themeMode == 0 && isSystemDark()) cardConfig.setThemeDefaults(true)
        cardConfig.save()
        return SettingsPlatformSnapshot(
            dpi = settings.getInt("app_dpi", 0),
            predictiveBackAnimation = settings.getString("predictive_back_animation", "").orEmpty(),
            predictiveBackExitDirection = settings.getString("predictive_back_exit_direction", "")
                .orEmpty(),
            themeMode = themeMode,
            useDynamicColor = themeConfig.useDynamicColor,
            dynamicColorSpec = themeConfig.dynamicColorSpec,
            dynamicPaletteStyle = themeConfig.dynamicPaletteStyle,
            currentLocaleTag = localeHelper.getCurrentAppLocale(application)?.toLanguageTag(),
            useAltIcon = settings.getBoolean("use_alt_icon", false),
            cardAlpha = cardConfig.cardAlpha,
            backgroundDim = themeConfig.backgroundDim,
            customBackgroundEnabled = customBackground,
            systemDpi = systemDpi,
            currentDpi = currentDpi,
            checkManagerUpdate = settings.getBoolean("check_update", true),
            checkBetaUpdate = settings.getBoolean("check_beta_update", true),
            checkModuleUpdate = loadModuleUpdatePreference(),
            autoJailbreakEnabled = settings.getBoolean("auto_jailbreak", false),
            useBuiltinMonoFont = themeConfig.useBuiltinMonoFont,
        )
    }

    suspend fun updateAppearance(
        setting: AppearanceSetting,
    ): Result<SettingsPlatformSnapshot> = try {
        when (setting) {
            is AppearanceSetting.ThemeMode -> setThemeMode(setting.index)
            is AppearanceSetting.SeedColor -> {
                themeRepository.saveSeedColor(setting.color)
                themeConfig.seedColor = setting.color
            }

            is AppearanceSetting.DynamicColor -> {
                themeRepository.saveDynamicColorState(setting.enabled)
                themeConfig.useDynamicColor = setting.enabled
            }

            is AppearanceSetting.DynamicColorSpec -> {
                themeConfig.dynamicPaletteStyle = themeRepository.saveDynamicColorSpec(
                    setting.spec,
                    themeConfig.dynamicPaletteStyle,
                )
                themeConfig.dynamicColorSpec = setting.spec
            }

            is AppearanceSetting.DynamicPaletteStyle -> {
                themeConfig.dynamicPaletteStyle = themeRepository.saveDynamicPaletteStyle(
                    setting.style,
                    themeConfig.dynamicColorSpec,
                )
            }

            is AppearanceSetting.CustomBackground -> {
                check(
                    backgroundManager.saveAndApplyCustomBackground(
                        application,
                        setting.uri.toUri()
                    )
                )
                backgroundManager.saveBackgroundDim(0.3f)
                backgroundManager.saveEnableBlur(true)
                backgroundManager.saveEnableBlurExp(false)
                backgroundManager.saveUseBackgroundSeedColor(true)
                backgroundManager.saveEnableHighContrastMode(false)
                cardConfig.cardElevation = 0.dp
                cardConfig.updateBackground(true)
                cardConfig.save()
            }

            AppearanceSetting.RemoveCustomBackground -> removeCustomBackground()
            is AppearanceSetting.CardAlpha -> {
                cardConfig.cardAlpha = setting.value
                cardConfig.isCustomAlphaSet = true
                settings.putBoolean("is_custom_alpha_set", true)
                settings.putFloat("card_alpha", setting.value)
            }

            is AppearanceSetting.BackgroundDim ->
                backgroundManager.saveBackgroundDim(setting.value)

            AppearanceSetting.SaveCardConfig -> cardConfig.save()
        }
        Result.success(load())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    fun updatePlatform(
        setting: PlatformSetting,
    ): Result<SettingsPlatformSnapshot> = try {
        when (setting) {
            PlatformSetting.InitializeFirstRun -> initializeFirstRun()
            is PlatformSetting.PredictiveBackAnimation ->
                settings.putString("predictive_back_animation", setting.value)

            is PlatformSetting.PredictiveBackExitDirection ->
                settings.putString("predictive_back_exit_direction", setting.value)

            is PlatformSetting.Dpi -> settings.putInt("app_dpi", setting.value)
            is PlatformSetting.AlternateIcon -> {
                settings.putBoolean("use_alt_icon", setting.enabled)
                toggleLauncherIcon(setting.enabled)
            }

            is PlatformSetting.ManagerUpdateCheck -> {
                settings.putBoolean("check_update", setting.enabled)
                if (!setting.enabled) settings.putBoolean("check_beta_update", false)
            }

            is PlatformSetting.BetaUpdateCheck ->
                settings.putBoolean("check_beta_update", setting.enabled)

            is PlatformSetting.ModuleUpdateCheck ->
                settings.putBoolean("check_module_update", setting.enabled)

            is PlatformSetting.Locale -> settings.putString("app_locale", setting.tag)
            is PlatformSetting.AutoJailbreak -> setAutoJailbreak(setting.enabled)
            is PlatformSetting.AdbRoot -> setAdbRoot(setting.enabled)
            is PlatformSetting.SuCompatMode -> settings.putInt("su_compat_mode", setting.value)
            is PlatformSetting.BuiltinMonospaceFont -> {
                settings.putBoolean("use_builtin_monospace_font", setting.enabled)
                themeConfig.useBuiltinMonoFont = setting.enabled
            }
        }
        Result.success(load())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun getFeatureStatus(): PlatformFeatureStatus = withContext(Dispatchers.IO) {
        PlatformFeatureStatus(
            suCompatPersistValue = runCatching {
                ksuCliRepository.getFeaturePersistValue("su_compat")
            }.getOrNull(),
            suStatus = runCatching {
                ksuCliRepository.getFeatureStatus("su_compat")
            }.getOrDefault(""),
            kernelUmountStatus = runCatching {
                ksuCliRepository.getFeatureStatus("kernel_umount")
            }.getOrDefault(""),
            adbRootStatus = runCatching {
                ksuCliRepository.getFeatureStatus("adb_root")
            }.getOrDefault(""),
            adbRootEnabled = runCatching {
                ksuCliRepository.getFeaturePersistValue("adb_root") == 1L
            }.getOrDefault(false),
            sulogStatus = runCatching {
                ksuCliRepository.getFeatureStatus("sulog")
            }.getOrDefault(""),
            selinuxHideStatus = runCatching {
                ksuCliRepository.getFeatureStatus("selinux_hide")
            }.getOrDefault(""),
            webViewZygoteUmountStatus = runCatching {
                ksuCliRepository.getFeatureStatus("webview_zygote_umount")
            }.getOrDefault(""),
        )
    }

    private fun setThemeMode(index: Int) {
        val forceDark = when (index) {
            1 -> false
            2 -> true
            else -> null
        }
        themeRepository.saveThemeMode(forceDark)
        themeConfig.forceDarkMode = forceDark
        when (index) {
            2 -> {
                cardConfig.updateThemePreference(darkMode = true, lightMode = false)
                cardConfig.setThemeDefaults(true)
            }

            1 -> {
                cardConfig.updateThemePreference(darkMode = false, lightMode = true)
                cardConfig.setThemeDefaults(false)
            }

            else -> {
                cardConfig.updateThemePreference(darkMode = null, lightMode = null)
                cardConfig.setThemeDefaults(isSystemDark())
            }
        }
        cardConfig.save()
    }

    private fun removeCustomBackground() {
        backgroundManager.clearCustomBackground(application)
        cardConfig.cardAlpha = 1f
        cardConfig.isCustomAlphaSet = false
        cardConfig.isCustomBackgroundEnabled = false
        cardConfig.save()
        themeConfig.preventBackgroundRefresh = false
        backgroundManager.saveBackgroundDim(0f)
        backgroundManager.saveEnableBlur(false)
        backgroundManager.saveEnableBlurExp(false)
        backgroundManager.saveUseBackgroundSeedColor(false)
        backgroundManager.saveEnableHighContrastMode(false)
        settings.putBoolean("prevent_background_refresh", false)
    }

    private fun initializeFirstRun() {
        if (settings.getBoolean("is_first_run", true)) {
            themeConfig.preventBackgroundRefresh = false
            settings.putBoolean("prevent_background_refresh", false)
            settings.putBoolean("is_first_run", false)
        }
    }

    private fun setAutoJailbreak(enabled: Boolean) {
        application.packageManager.setComponentEnabledSetting(
            ComponentName(application, BootCompletedReceiver::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        settings.putBoolean("auto_jailbreak", enabled)
    }

    private fun setAdbRoot(enabled: Boolean) {
        if (ksuCliRepository.execKsud("feature set adb_root ${if (enabled) 1 else 0}", true)) {
            ShellUtils.fastCmd("setprop ctl.restart adbd")
            ksuCliRepository.execKsud("feature save", true)
        }
    }

    private fun toggleLauncherIcon(useAlt: Boolean) {
        val packageName = application.packageName
        val main = ComponentName(packageName, "$packageName.ui.MainActivity")
        val alias = ComponentName(packageName, "$packageName.ui.MainActivityAlias")
        application.packageManager.setComponentEnabledSetting(
            if (useAlt) alias else main,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        application.packageManager.setComponentEnabledSetting(
            if (useAlt) main else alias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun loadModuleUpdatePreference(): Boolean {
        val enabled = settings.getBoolean(
            "check_module_update",
            settings.getBoolean("check_update", true),
        )
        if (!settings.contains("check_module_update")) {
            settings.putBoolean("check_module_update", enabled)
        }
        return enabled
    }

    private fun isSystemDark(): Boolean =
        application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
}
