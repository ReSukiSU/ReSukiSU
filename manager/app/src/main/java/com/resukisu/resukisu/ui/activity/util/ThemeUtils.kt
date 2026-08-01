package com.resukisu.resukisu.ui.activity.util

import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings
import com.resukisu.resukisu.data.appPreferences
import com.resukisu.resukisu.ui.MainActivity
import com.resukisu.resukisu.ui.theme.BackgroundManager
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.ThemeManager
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel

class ThemeChangeContentObserver(
    handler: Handler,
    private val onThemeChanged: () -> Unit
) : ContentObserver(handler) {
    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        onThemeChanged()
    }
}

object ThemeUtils {

    fun initializeThemeSettings(activity: MainActivity, settingsViewModel: SettingsViewModel) {
        settingsViewModel.initializeFirstRunSettings(activity)
        loadThemeSettings(activity)
        settingsViewModel.initialize(activity)
    }

    fun registerThemeChangeObserver(activity: MainActivity): ThemeChangeContentObserver {
        val contentObserver = ThemeChangeContentObserver(Handler(activity.mainLooper)) {
            activity.runOnUiThread {
                if (!ThemeConfig.preventBackgroundRefresh) {
                    ThemeConfig.backgroundImageLoaded = false
                    BackgroundManager.loadCustomBackground(activity)
                }
            }
        }

        activity.contentResolver.registerContentObserver(
            Settings.System.getUriFor("ui_night_mode"),
            false,
            contentObserver
        )

        return contentObserver
    }

    fun unregisterThemeChangeObserver(activity: MainActivity, observer: ThemeChangeContentObserver) {
        activity.contentResolver.unregisterContentObserver(observer)
    }

    fun onActivityPause(activity: MainActivity) {
        CardConfig.save(activity.applicationContext)
        activity.appPreferences.putBoolean("prevent_background_refresh", true)
        ThemeConfig.preventBackgroundRefresh = true
    }

    fun onActivityResume(activity: MainActivity) {
        activity.appPreferences.putBoolean("prevent_background_refresh", false)
        ThemeConfig.preventBackgroundRefresh = false
        loadThemeSettings(activity)
    }

    private fun loadThemeSettings(activity: MainActivity) {
        ThemeManager.loadThemeMode(activity)
        ThemeManager.loadSeedColor(activity)
        ThemeManager.loadDynamicColorState(activity)
        ThemeManager.loadDynamicColorSpec(activity)
        ThemeManager.loadDynamicPaletteStyle(activity)
        CardConfig.load(activity.applicationContext)
        BackgroundManager.loadCustomBackground(activity)
    }
}
