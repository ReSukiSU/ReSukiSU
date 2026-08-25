package com.resukisu.resukisu.domain.model

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

data class SettingsPlatformSnapshot(
    val dpi: Int = 0,
    val predictiveBackAnimation: String = "",
    val predictiveBackExitDirection: String = "",
    val themeMode: Int = 0,
    val useDynamicColor: Boolean = false,
    val dynamicColorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021,
    val dynamicPaletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val currentLocaleTag: String? = null,
    val useAltIcon: Boolean = false,
    val cardAlpha: Float = 1f,
    val backgroundDim: Float = 0f,
    val customBackgroundEnabled: Boolean = false,
    val systemDpi: Int = 0,
    val currentDpi: Int = 0,
    val checkManagerUpdate: Boolean = true,
    val checkBetaUpdate: Boolean = true,
    val checkModuleUpdate: Boolean = true,
    val autoJailbreakEnabled: Boolean = false,
    val useBuiltinMonoFont: Boolean = false,
)

data class PlatformFeatureStatus(
    val suCompatPersistValue: Long? = null,
    val suStatus: String = "",
    val kernelUmountStatus: String = "",
    val adbRootStatus: String = "",
    val adbRootEnabled: Boolean = false,
    val sulogStatus: String = "",
    val selinuxHideStatus: String = "",
    val webViewZygoteUmountStatus: String = "",
)

sealed interface AppearanceSetting {
    data class ThemeMode(val index: Int) : AppearanceSetting
    data class SeedColor(val color: Int) : AppearanceSetting
    data class DynamicColor(val enabled: Boolean) : AppearanceSetting
    data class DynamicColorSpec(val spec: ColorSpec.SpecVersion) : AppearanceSetting
    data class DynamicPaletteStyle(val style: PaletteStyle) : AppearanceSetting
    data class CustomBackground(val uri: String) : AppearanceSetting
    data object RemoveCustomBackground : AppearanceSetting
    data class CardAlpha(val value: Float) : AppearanceSetting
    data class BackgroundDim(val value: Float) : AppearanceSetting
    data object SaveCardConfig : AppearanceSetting
}

private val spec2025IncompatiblePaletteStyles = setOf(
    PaletteStyle.Rainbow,
    PaletteStyle.FruitSalad,
    PaletteStyle.Monochrome,
    PaletteStyle.Fidelity,
    PaletteStyle.Content,
)

fun PaletteStyle.isCompatibleWith(spec: ColorSpec.SpecVersion): Boolean =
    spec != ColorSpec.SpecVersion.SPEC_2025 || this !in spec2025IncompatiblePaletteStyles

fun PaletteStyle.coerceCompatibleWith(spec: ColorSpec.SpecVersion): PaletteStyle =
    takeIf { it.isCompatibleWith(spec) } ?: PaletteStyle.TonalSpot

fun ColorSpec.SpecVersion.availablePaletteStyles(): List<PaletteStyle> =
    PaletteStyle.entries.filter { it.isCompatibleWith(this) }

sealed interface PlatformSetting {
    data object InitializeFirstRun : PlatformSetting
    data class PredictiveBackAnimation(val value: String) : PlatformSetting
    data class PredictiveBackExitDirection(val value: String) : PlatformSetting
    data class Dpi(val value: Int) : PlatformSetting
    data class AlternateIcon(val enabled: Boolean) : PlatformSetting
    data class ManagerUpdateCheck(val enabled: Boolean) : PlatformSetting
    data class BetaUpdateCheck(val enabled: Boolean) : PlatformSetting
    data class ModuleUpdateCheck(val enabled: Boolean) : PlatformSetting
    data class Locale(val tag: String) : PlatformSetting
    data class AutoJailbreak(val enabled: Boolean) : PlatformSetting
    data class AdbRoot(val enabled: Boolean) : PlatformSetting
    data class SuCompatMode(val value: Int) : PlatformSetting
    data class BuiltinMonospaceFont(val enabled: Boolean) : PlatformSetting
}
