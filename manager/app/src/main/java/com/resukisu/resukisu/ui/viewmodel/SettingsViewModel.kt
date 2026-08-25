package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.AppearanceSetting
import com.resukisu.resukisu.domain.model.PlatformSetting
import com.resukisu.resukisu.domain.model.SettingsPlatformSnapshot
import com.resukisu.resukisu.domain.model.coerceCompatibleWith
import com.resukisu.resukisu.domain.usecase.ConfigureSuLogUseCase
import com.resukisu.resukisu.domain.usecase.GetKernelFeatureSettingsUseCase
import com.resukisu.resukisu.domain.usecase.GetPlatformFeatureStatusUseCase
import com.resukisu.resukisu.domain.usecase.LoadSettingsPlatformUseCase
import com.resukisu.resukisu.domain.usecase.SetDefaultUmountModulesUseCase
import com.resukisu.resukisu.domain.usecase.SetKernelUmountEnabledUseCase
import com.resukisu.resukisu.domain.usecase.SetSelinuxHideEnabledUseCase
import com.resukisu.resukisu.domain.usecase.SetSuEnabledUseCase
import com.resukisu.resukisu.domain.usecase.SetWebViewZygoteUmountEnabledUseCase
import com.resukisu.resukisu.domain.usecase.UpdateAppearanceUseCase
import com.resukisu.resukisu.domain.usecase.UpdatePlatformSettingUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class PredictiveBackAnimation(val value: String) {
    None("none"),
    AOSP("aosp"),
    MIUIX("miuix"),
    Scale("scale"),
    KernelSUClassic("ksu_classic");

    companion object {
        fun fromValueOrDefault(value: String) = entries.find { it.value == value } ?: Scale
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

fun dpiFriendlyNameRes(dpi: Int): Int = when (dpi) {
    240 -> R.string.dpi_size_small
    320 -> R.string.dpi_size_medium
    420 -> R.string.dpi_size_large
    560 -> R.string.dpi_size_extra_large
    else -> R.string.dpi_size_custom
}

data class SettingsUiState(
    val dpi: Int = 0,
    val predictiveBackAnimation: PredictiveBackAnimation = PredictiveBackAnimation.Scale,
    val predictiveBackExitDirection: PredictiveBackExitDirection =
        PredictiveBackExitDirection.FOLLOW_GESTURE,
    val themeMode: Int = 0,
    val themeOptions: List<Int> = emptyList(),
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
    val dpiPresets: Map<Int, Int> = emptyMap(),
    val checkManagerUpdate: Boolean = true,
    val checkBetaUpdate: Boolean = true,
    val checkModuleUpdate: Boolean = true,
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
    val webViewZygoteUmountStatus: String = "",
    val isWebViewZygoteUmountEnabled: Boolean = false,
    val defaultUmountModules: Boolean = false,
    val useBuiltinMonoFont: Boolean = false,
)

sealed interface SettingsUiAction {
    data object Initialize : SettingsUiAction
    data object InitializeFirstRun : SettingsUiAction
    data object LoadFeatureSettings : SettingsUiAction
    data class SetThemeMode(val index: Int) : SettingsUiAction
    data class SetThemeColor(val color: Int) : SettingsUiAction
    data class SetThemeColorDialogVisible(val visible: Boolean) : SettingsUiAction
    data class SetPredictiveBackAnimation(val animation: PredictiveBackAnimation) : SettingsUiAction
    data class SetPredictiveBackExitDirection(
        val direction: PredictiveBackExitDirection,
    ) : SettingsUiAction

    data class SetDynamicColor(val enabled: Boolean) : SettingsUiAction
    data class SetDynamicColorSpec(val spec: ColorSpec.SpecVersion) : SettingsUiAction
    data class SetDynamicPaletteStyle(val style: PaletteStyle) : SettingsUiAction
    data class SetCustomBackground(val uri: String) : SettingsUiAction
    data object RemoveCustomBackground : SettingsUiAction
    data class SetCardAlpha(val value: Float) : SettingsUiAction
    data class SetBackgroundDim(val value: Float) : SettingsUiAction
    data object SaveCardConfig : SettingsUiAction
    data class SetLanguageDialogVisible(val visible: Boolean) : SettingsUiAction
    data class SetLanguage(val localeTag: String) : SettingsUiAction
    data object RestartActivity : SettingsUiAction
    data object ApplyDpi : SettingsUiAction
    data class SetTempDpi(val dpi: Int) : SettingsUiAction
    data class SetAlternateIcon(val enabled: Boolean) : SettingsUiAction
    data class SetManagerUpdateCheck(val enabled: Boolean) : SettingsUiAction
    data class SetBetaUpdateCheck(val enabled: Boolean) : SettingsUiAction
    data class SetModuleUpdateCheck(val enabled: Boolean) : SettingsUiAction
    data class SetSuCompatMode(val index: Int) : SettingsUiAction
    data class SetKernelUmount(val enabled: Boolean) : SettingsUiAction
    data class SetAutoJailbreak(val enabled: Boolean) : SettingsUiAction
    data class SetSelinuxHide(val enabled: Boolean) : SettingsUiAction
    data class SetAdbRoot(val enabled: Boolean) : SettingsUiAction
    data class SetSuLog(val enabled: Boolean) : SettingsUiAction
    data class SetDefaultUmountModules(val enabled: Boolean) : SettingsUiAction
    data class SetWebViewZygoteUmountEnabled(val enabled: Boolean) : SettingsUiAction
}

sealed interface SettingsUiEvent {
    data class Error(val message: String) : SettingsUiEvent
    data class Message(val stringResource: Int, val formatArg: Int? = null) : SettingsUiEvent
    data object RestartActivity : SettingsUiEvent
}

class SettingsViewModel(
    private val loadSettings: LoadSettingsPlatformUseCase,
    private val updateAppearance: UpdateAppearanceUseCase,
    private val updatePlatform: UpdatePlatformSettingUseCase,
    private val getPlatformFeatureStatus: GetPlatformFeatureStatusUseCase,
    private val getKernelFeatureSettings: GetKernelFeatureSettingsUseCase,
    private val setSuEnabled: SetSuEnabledUseCase,
    private val setKernelUmountEnabled: SetKernelUmountEnabledUseCase,
    private val setSuLogEnabled: ConfigureSuLogUseCase,
    private val setSelinuxHideEnabled: SetSelinuxHideEnabledUseCase,
    private val setDefaultUmountModules: SetDefaultUmountModulesUseCase,
    private val setWebViewZygoteUmountEnabled: SetWebViewZygoteUmountEnabledUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()
    val uiState: StateFlow<SettingsUiState> = state
    private val mutableEvents = MutableSharedFlow<SettingsUiEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<SettingsUiEvent> = mutableEvents.asSharedFlow()

    init {
        dispatch(SettingsUiAction.Initialize)
    }

fun initialize() {
        applySnapshot(loadSettings(), resetTempDpi = true)
        loadFeatureSettings()
    }

    fun initializeFirstRunSettings() {
        updatePlatformAsync(PlatformSetting.InitializeFirstRun)
    }

    fun loadFeatureSettings() {
        viewModelScope.launch {
            val features = getKernelFeatureSettings()
            val platform = getPlatformFeatureStatus()
            val suCompatMode = platform.suCompatPersistValue?.let { value ->
                if (value == 0L) 2 else if (!features.suEnabled) 1 else 0
            } ?: if (!features.suEnabled) 1 else 0
            mutableState.update {
                it.copy(
                    suCompatMode = suCompatMode,
                    suStatus = platform.suStatus,
                    kernelUmountStatus = platform.kernelUmountStatus,
                    isKernelUmountEnabled = features.kernelUmountEnabled,
                    adbRootStatus = platform.adbRootStatus,
                    isAdbRootEnabled = platform.adbRootEnabled,
                    sulogStatus = platform.sulogStatus,
                    isSuLogEnabled = features.suLogEnabled,
                    selinuxHideStatus = platform.selinuxHideStatus,
                    isSelinuxHideEnabled = features.selinuxHideEnabled,
                    webViewZygoteUmountStatus = platform.webViewZygoteUmountStatus,
                    isWebViewZygoteUmountEnabled = features.webViewZygoteUmountEnabled,
                    defaultUmountModules = features.defaultUmountModules,
                )
            }
        }
    }

    fun setPredictiveBackAnimation(animation: PredictiveBackAnimation) {
        mutableState.update { it.copy(predictiveBackAnimation = animation) }
        updatePlatformAsync(PlatformSetting.PredictiveBackAnimation(animation.value))
    }

    fun setPredictiveBackExitDirection(direction: PredictiveBackExitDirection) {
        mutableState.update { it.copy(predictiveBackExitDirection = direction) }
        updatePlatformAsync(PlatformSetting.PredictiveBackExitDirection(direction.value))
    }

    fun setThemeColorDialogVisible(visible: Boolean) =
        mutableState.update { it.copy(showThemeColorDialog = visible) }

    fun setLanguageDialogVisible(visible: Boolean) =
        mutableState.update { it.copy(showLanguageDialog = visible) }
    fun handleLanguageChange(localeTag: String) {
        updatePlatformAsync(PlatformSetting.Locale(localeTag))
    }

    fun restartActivityForLanguage() {
        mutableEvents.tryEmit(SettingsUiEvent.RestartActivity)
    }

    fun handleThemeModeChange(index: Int) {
        mutableState.update { it.copy(themeMode = index) }
        updateAppearanceAsync(AppearanceSetting.ThemeMode(index))
    }

    fun handleThemeColorChange(seedColor: Int) =
        updateAppearanceAsync(AppearanceSetting.SeedColor(seedColor))

    fun handleDynamicColorChange(enabled: Boolean) {
        mutableState.update { it.copy(useDynamicColor = enabled) }
        updateAppearanceAsync(AppearanceSetting.DynamicColor(enabled))
    }

    fun handleDynamicColorSpecChange(spec: ColorSpec.SpecVersion) {
        mutableState.update {
            it.copy(
                dynamicColorSpec = spec,
                dynamicPaletteStyle = it.dynamicPaletteStyle.coerceCompatibleWith(spec),
            )
        }
        updateAppearanceAsync(AppearanceSetting.DynamicColorSpec(spec))
    }

    fun handleDynamicPaletteStyleChange(style: PaletteStyle) {
        mutableState.update { it.copy(dynamicPaletteStyle = style) }
        updateAppearanceAsync(AppearanceSetting.DynamicPaletteStyle(style))
    }
    fun updateTempDpi(dpi: Int) {
        mutableState.update { it.copy(tempDpi = dpi, isDpiCustom = dpi !in DPI_PRESETS) }
    }

    fun handleDpiApply() {
        val current = state.value
        if (current.tempDpi == current.currentDpi) return
        viewModelScope.launch {
            updatePlatform(PlatformSetting.Dpi(current.tempDpi))
                .onSuccess {
                    applySnapshot(it, resetTempDpi = true)
                    mutableEvents.tryEmit(
                        SettingsUiEvent.Message(R.string.dpi_applied_success, current.tempDpi)
                    )
                }
                .onFailure(::emitError)
        }
    }

    fun handleCustomBackground(uri: String) {
        viewModelScope.launch {
            updateAppearance(AppearanceSetting.CustomBackground(uri))
                .onSuccess {
                    applySnapshot(it, resetTempDpi = false)
                    mutableEvents.tryEmit(SettingsUiEvent.Message(R.string.background_set_success))
                }
                .onFailure {
                    mutableEvents.tryEmit(SettingsUiEvent.Message(R.string.background_crop_failed))
                }
        }
    }

    fun handleRemoveCustomBackground() {
        viewModelScope.launch {
            updateAppearance(AppearanceSetting.RemoveCustomBackground)
                .onSuccess {
                    applySnapshot(it, resetTempDpi = false)
                    mutableEvents.tryEmit(SettingsUiEvent.Message(R.string.background_removed))
                }
                .onFailure(::emitError)
        }
    }

    fun handleCardAlphaChange(value: Float) {
        mutableState.update { it.copy(cardAlpha = value) }
        updateAppearanceAsync(AppearanceSetting.CardAlpha(value))
    }

    fun handleBackgroundDimChange(value: Float) {
        mutableState.update { it.copy(backgroundDim = value) }
        updateAppearanceAsync(AppearanceSetting.BackgroundDim(value))
    }

    fun saveCardConfig() = updateAppearanceAsync(AppearanceSetting.SaveCardConfig)

    fun handleIconChange(enabled: Boolean) {
        mutableState.update { it.copy(useAltIcon = enabled) }
        viewModelScope.launch {
            updatePlatform(PlatformSetting.AlternateIcon(enabled))
                .onSuccess {
                    applySnapshot(it, resetTempDpi = false)
                    mutableEvents.tryEmit(SettingsUiEvent.Message(R.string.icon_switched))
                }
                .onFailure(::emitError)
        }
    }

    fun handleCheckManagerUpdateChange(enabled: Boolean) {
        mutableState.update {
            it.copy(
                checkManagerUpdate = enabled,
                checkBetaUpdate = if (enabled) it.checkBetaUpdate else false,
            )
        }
        updatePlatformAsync(PlatformSetting.ManagerUpdateCheck(enabled))
    }

    fun handleCheckBetaUpdateChange(enabled: Boolean) {
        mutableState.update { it.copy(checkBetaUpdate = enabled) }
        updatePlatformAsync(PlatformSetting.BetaUpdateCheck(enabled))
    }

    fun handleCheckModuleUpdateChange(enabled: Boolean) {
        mutableState.update { it.copy(checkModuleUpdate = enabled) }
        updatePlatformAsync(PlatformSetting.ModuleUpdateCheck(enabled))
    }

    fun handleSuCompatModeChange(index: Int) {
        viewModelScope.launch {
            val changed = when (index) {
                0 -> setSuEnabled(true)
                1 -> setSuEnabled(true) && setSuEnabled(false)
                2 -> setSuEnabled(false)
                else -> false
            }
            if (changed) {
                updatePlatform(PlatformSetting.SuCompatMode(if (index == 2) 2 else 0))
                mutableState.update { it.copy(suCompatMode = index) }
            }
        }
    }

    fun handleKernelUmountChange(checked: Boolean) {
        viewModelScope.launch {
            if (setKernelUmountEnabled(checked)) {
                mutableState.update { it.copy(isKernelUmountEnabled = checked) }
            }
        }
    }

    fun handleAutoJailbreakChange(enabled: Boolean) {
        mutableState.update { it.copy(autoJailbreakEnabled = enabled) }
        updatePlatformAsync(PlatformSetting.AutoJailbreak(enabled))
    }

    fun handleAdbRootChange(checked: Boolean) {
        mutableState.update { it.copy(isAdbRootEnabled = checked) }
        updatePlatformAsync(PlatformSetting.AdbRoot(checked))
    }

    fun handleSuLogChange(checked: Boolean) {
        viewModelScope.launch {
            if (setSuLogEnabled(checked)) mutableState.update { it.copy(isSuLogEnabled = checked) }
        }
    }

    fun handleSelinuxHideChange(checked: Boolean) {
        viewModelScope.launch {
            val status = setSelinuxHideEnabled(checked)
            mutableState.update { it.copy(isSelinuxHideEnabled = checked) }
            when (status) {
                0 -> Unit
                -11 -> mutableEvents.emit(
                    SettingsUiEvent.Message(R.string.settings_selinux_hide_reboot_required)
                )

                else -> mutableEvents.emit(
                    SettingsUiEvent.Message(R.string.settings_selinux_hide_failed, status)
                )
            }
        }
    }

    fun handleDefaultUmountModulesChange(checked: Boolean) {
        viewModelScope.launch {
            if (setDefaultUmountModules(checked)) {
                mutableState.update { it.copy(defaultUmountModules = checked) }
            }
        }
    }

    fun handleWebViewZygoteUmountChange(checked: Boolean) {
        viewModelScope.launch {
            if (setWebViewZygoteUmountEnabled(checked)) {
                mutableState.update { it.copy( isWebViewZygoteUmountEnabled = checked) }
            }
        }
    }


fun dispatch(action: SettingsUiAction) {
        when (action) {
            SettingsUiAction.Initialize -> initialize()
            SettingsUiAction.InitializeFirstRun -> initializeFirstRunSettings()
            SettingsUiAction.LoadFeatureSettings -> loadFeatureSettings()
            is SettingsUiAction.SetThemeMode -> handleThemeModeChange(action.index)
            is SettingsUiAction.SetThemeColor -> handleThemeColorChange(action.color)
            is SettingsUiAction.SetThemeColorDialogVisible ->
                setThemeColorDialogVisible(action.visible)

            is SettingsUiAction.SetPredictiveBackAnimation ->
                setPredictiveBackAnimation(action.animation)

            is SettingsUiAction.SetPredictiveBackExitDirection ->
                setPredictiveBackExitDirection(action.direction)

            is SettingsUiAction.SetDynamicColor -> handleDynamicColorChange(action.enabled)
            is SettingsUiAction.SetDynamicColorSpec -> handleDynamicColorSpecChange(action.spec)
            is SettingsUiAction.SetDynamicPaletteStyle -> handleDynamicPaletteStyleChange(action.style)
            is SettingsUiAction.SetCustomBackground -> handleCustomBackground(action.uri)
            SettingsUiAction.RemoveCustomBackground -> handleRemoveCustomBackground()
            is SettingsUiAction.SetCardAlpha -> handleCardAlphaChange(action.value)
            is SettingsUiAction.SetBackgroundDim -> handleBackgroundDimChange(action.value)
            SettingsUiAction.SaveCardConfig -> saveCardConfig()
            is SettingsUiAction.SetLanguageDialogVisible -> setLanguageDialogVisible(action.visible)
            is SettingsUiAction.SetLanguage -> handleLanguageChange(action.localeTag)
            SettingsUiAction.RestartActivity -> restartActivityForLanguage()
            SettingsUiAction.ApplyDpi -> handleDpiApply()
            is SettingsUiAction.SetTempDpi -> updateTempDpi(action.dpi)
            is SettingsUiAction.SetAlternateIcon -> handleIconChange(action.enabled)
            is SettingsUiAction.SetManagerUpdateCheck -> handleCheckManagerUpdateChange(action.enabled)
            is SettingsUiAction.SetBetaUpdateCheck -> handleCheckBetaUpdateChange(action.enabled)
            is SettingsUiAction.SetModuleUpdateCheck -> handleCheckModuleUpdateChange(action.enabled)
            is SettingsUiAction.SetSuCompatMode -> handleSuCompatModeChange(action.index)
            is SettingsUiAction.SetKernelUmount -> handleKernelUmountChange(action.enabled)
            is SettingsUiAction.SetAutoJailbreak -> handleAutoJailbreakChange(action.enabled)
            is SettingsUiAction.SetSelinuxHide -> handleSelinuxHideChange(action.enabled)
            is SettingsUiAction.SetAdbRoot -> handleAdbRootChange(action.enabled)
            is SettingsUiAction.SetSuLog -> handleSuLogChange(action.enabled)
            is SettingsUiAction.SetDefaultUmountModules ->
                handleDefaultUmountModulesChange(action.enabled)
            is SettingsUiAction.SetWebViewZygoteUmountEnabled -> handleWebViewZygoteUmountChange(action.enabled)
        }
    }

    fun handleBuiltinMonospaceFontChange(checked: Boolean) {
        updatePlatformAsync(PlatformSetting.BuiltinMonospaceFont(checked))
    }

    private fun updateAppearanceAsync(setting: AppearanceSetting) {
        viewModelScope.launch {
            updateAppearance(setting)
                .onSuccess { applySnapshot(it, resetTempDpi = false) }
                .onFailure(::emitError)
        }
    }

    private fun updatePlatformAsync(setting: PlatformSetting) {
        viewModelScope.launch {
            updatePlatform(setting)
                .onSuccess { applySnapshot(it, resetTempDpi = false) }
                .onFailure(::emitError)
        }
    }

    private fun applySnapshot(snapshot: SettingsPlatformSnapshot, resetTempDpi: Boolean) {
        mutableState.update { current ->
            current.copy(
                dpi = snapshot.dpi,
                predictiveBackAnimation =
                    PredictiveBackAnimation.fromValueOrDefault(snapshot.predictiveBackAnimation),
                predictiveBackExitDirection =
                    PredictiveBackExitDirection.fromValueOrDefault(snapshot.predictiveBackExitDirection),
                themeMode = snapshot.themeMode,
                themeOptions = THEME_OPTIONS,
                useDynamicColor = snapshot.useDynamicColor,
                dynamicColorSpec = snapshot.dynamicColorSpec,
                dynamicPaletteStyle = snapshot.dynamicPaletteStyle,
                currentAppLocale = snapshot.currentLocaleTag?.let(Locale::forLanguageTag),
                useAltIcon = snapshot.useAltIcon,
                cardAlpha = snapshot.cardAlpha,
                backgroundDim = snapshot.backgroundDim,
                isCustomBackgroundEnabled = snapshot.customBackgroundEnabled,
                systemDpi = snapshot.systemDpi,
                currentDpi = snapshot.currentDpi,
                tempDpi = if (resetTempDpi) snapshot.currentDpi else current.tempDpi,
                isDpiCustom = (if (resetTempDpi) snapshot.currentDpi else current.tempDpi) !in DPI_PRESETS,
                dpiPresets = DPI_PRESET_RESOURCES,
                checkManagerUpdate = snapshot.checkManagerUpdate,
                checkBetaUpdate = snapshot.checkBetaUpdate,
                checkModuleUpdate = snapshot.checkModuleUpdate,
                autoJailbreakEnabled = snapshot.autoJailbreakEnabled,
                useBuiltinMonoFont = snapshot.useBuiltinMonoFont,
            )
        }
    }

    private fun emitError(error: Throwable) {
        mutableEvents.tryEmit(SettingsUiEvent.Error(error.message.orEmpty()))
    }

    private companion object {
        val THEME_OPTIONS = listOf(
            R.string.theme_follow_system,
            R.string.theme_light,
            R.string.theme_dark,
        )
        val DPI_PRESET_RESOURCES = mapOf(
            R.string.dpi_size_small to 240,
            R.string.dpi_size_medium to 320,
            R.string.dpi_size_large to 420,
            R.string.dpi_size_extra_large to 560,
        )
        val DPI_PRESETS = DPI_PRESET_RESOURCES.values.toSet()
    }
}
