package com.resukisu.resukisu.data.settings

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.resukisu.resukisu.data.AppSettingsRepository
import java.util.Locale

/**
 * Launch system app locale settings (Android 13+)
 */
fun launchSystemLanguageSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to app language settings if system settings not available
        }
    }
}

/**
 * Apply saved language setting to context (for Android < 13)
 */
class LocaleHelper(
    private val settings: AppSettingsRepository,
) {
fun applyLanguage(context: Context): Context {
    // On Android 13+, language is handled by system
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return context
    }

    val localeTag = settings.getString("app_locale", "system") ?: "system"

    return if (localeTag == "system") {
        context
    } else {
        val locale = parseLocaleTag(localeTag)
        setLocale(context, locale)
    }
}

/**
 * Set locale for context (Android < 13)
 */
@SuppressLint("ObsoleteSdkInt")
private fun setLocale(context: Context, locale: Locale): Context {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        updateResources(context, locale)
    } else {
        updateResourcesLegacy(context, locale)
    }
}

@SuppressLint("UseRequiresApi", "ObsoleteSdkInt")
@TargetApi(Build.VERSION_CODES.N)
private fun updateResources(context: Context, locale: Locale): Context {
    val configuration = Configuration()
    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)
    return context.createConfigurationContext(configuration)
}

@Suppress("DEPRECATION")
@SuppressWarnings("deprecation")
private fun updateResourcesLegacy(context: Context, locale: Locale): Context {
    Locale.setDefault(locale)
    val resources = context.resources
    val configuration = resources.configuration
    configuration.locale = locale
    configuration.setLayoutDirection(locale)
    resources.updateConfiguration(configuration, resources.displayMetrics)
    return context
}

/**
 * Parse locale tag to Locale object
 */
private fun parseLocaleTag(tag: String): Locale {
    return try {
        if (tag.contains("_")) {
            val parts = tag.split("_")
            Locale.Builder()
                .setLanguage(parts[0])
                .setRegion(parts.getOrNull(1) ?: "")
                .build()
        } else {
            Locale.Builder()
                .setLanguage(tag)
                .build()
        }
    } catch (_: Exception) {
        Locale.getDefault()
    }
}

/**
 * Get current app locale
 */
@SuppressLint("ObsoleteSdkInt")
fun getCurrentAppLocale(context: Context): Locale? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val localeManager =
                context.getSystemService(Context.LOCALE_SERVICE) as? android.app.LocaleManager
            val locales = localeManager?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                locales.get(0)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    } else {
        val localeTag = settings.getString("app_locale", "system") ?: "system"
        if (localeTag == "system") {
            null
        } else {
            parseLocaleTag(localeTag)
        }
    }
}
}
