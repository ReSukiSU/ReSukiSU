package com.resukisu.resukisu.data.theme

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.resukisu.resukisu.data.AppSettingsRepository
import com.resukisu.resukisu.domain.model.coerceCompatibleWith

class ThemeRepository(
    private val settings: AppSettingsRepository,
    private val monetCompatColorSource: MonetCompatColorSource,
) {
    fun saveThemeMode(forceDark: Boolean?) {
        settings.putString(
            "theme_mode",
            when (forceDark) {
                true -> "dark"
                false -> "light"
                null -> "system"
            },
        )
    }

    fun loadThemeMode(): Boolean? = when (settings.getString("theme_mode", "system")) {
        "dark" -> true
        "light" -> false
        else -> null
    }

    fun saveSeedColor(seedColor: Int) {
        settings.putInt("theme_seed_color", seedColor)
    }

    fun loadSeedColor(): Int {
        if (!settings.contains("theme_seed_color")) {
            val legacyThemeName = settings.getString("theme_colors", "default")
                ?: "default"
            legacySeedColor(legacyThemeName)?.let { migratedSeedColor ->
                settings.putInt("theme_seed_color", migratedSeedColor)
                return migratedSeedColor
            }
            return defaultSeedColor()
        }

        return settings.getInt("theme_seed_color", defaultSeedColor())
    }

    fun defaultSeedColor(): Int = monetCompatColorSource.seedColor()

    fun saveDynamicColorState(enabled: Boolean) {
        settings.putBoolean("use_dynamic_color", enabled)
    }

    fun loadDynamicColorState(): Boolean =
        settings.getBoolean("use_dynamic_color", true)

    fun saveDynamicColorSpec(
        spec: ColorSpec.SpecVersion,
        currentPaletteStyle: PaletteStyle,
    ): PaletteStyle {
        settings.putString("dynamic_color_spec", spec.name)
        val compatibleStyle = currentPaletteStyle.coerceCompatibleWith(spec)
        if (compatibleStyle != currentPaletteStyle) {
            settings.putString("dynamic_palette_style", compatibleStyle.name)
        }
        return compatibleStyle
    }

    fun loadDynamicColorSpec(): ColorSpec.SpecVersion {
        val specName = settings.getString(
            "dynamic_color_spec",
            ColorSpec.SpecVersion.SPEC_2021.name,
        )
        return ColorSpec.SpecVersion.entries
            .find { it.name == specName }
            ?: ColorSpec.SpecVersion.SPEC_2021
    }

    fun saveDynamicPaletteStyle(
        style: PaletteStyle,
        spec: ColorSpec.SpecVersion,
    ): PaletteStyle {
        val compatibleStyle = style.coerceCompatibleWith(spec)
        settings.putString("dynamic_palette_style", compatibleStyle.name)
        return compatibleStyle
    }

    fun loadDynamicPaletteStyle(spec: ColorSpec.SpecVersion): PaletteStyle {
        val styleName = settings.getString(
            "dynamic_palette_style",
            PaletteStyle.TonalSpot.name,
        )
        val storedStyle = PaletteStyle.entries
            .find { it.name == styleName }
            ?: PaletteStyle.TonalSpot
        val compatibleStyle = storedStyle.coerceCompatibleWith(spec)
        if (compatibleStyle != storedStyle) {
            settings.putString("dynamic_palette_style", compatibleStyle.name)
        }
        return compatibleStyle
    }

    private fun legacySeedColor(name: String): Int? = when (name.lowercase()) {
        "green" -> 0xFF4C662B.toInt()
        "purple" -> 0xFF7C4E7E.toInt()
        "orange" -> 0xFF8B4F24.toInt()
        "pink" -> 0xFF8C4A60.toInt()
        "gray" -> 0xFF5B5C5C.toInt()
        "yellow" -> 0xFF6D5E0F.toInt()
        else -> null
    }
}
