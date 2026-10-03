package com.resukisu.resukisu.ui.wear

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.Circle
import androidx.compose.material.icons.twotone.FolderOpen
import androidx.compose.material.icons.twotone.Language
import androidx.compose.material.icons.twotone.Smartphone
import androidx.compose.material.icons.twotone.Square
import androidx.compose.material.icons.twotone.Translate
import androidx.compose.material.icons.twotone.RemoveModerator
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.wear.component.settings.WearChoicePage
import com.resukisu.resukisu.ui.viewmodel.SettingsUiAction
import com.resukisu.resukisu.ui.viewmodel.SettingsUiState
import com.resukisu.resukisu.ui.viewmodel.WearPreferences
import com.resukisu.resukisu.ui.viewmodel.WearPreferencesViewModel
import java.util.Locale

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearSelectionPage(selection: WearSelection, state: SettingsUiState, preferences: WearPreferences,
    preferenceViewModel: WearPreferencesViewModel, message: String?, onBack: () -> Unit, onAction: (SettingsUiAction) -> Unit) {
    when (selection) {
        WearSelection.PickerMode -> WearChoicePage(stringResource(R.string.wear_picker_mode),
            PickerModes.map { it to pickerModeLabel(it) },
            preferences.picker, onBack, message = message, icon = { selectionIcon(selection, it) },
            onChoose = preferenceViewModel::setPicker)
        WearSelection.ScreenShape -> WearChoicePage(stringResource(R.string.wear_screen_shape),
            ScreenShapes.map { it to screenShapeLabel(it) },
            preferences.shape, onBack, icon = { selectionIcon(selection, it) }, onChoose = preferenceViewModel::setShape)
        WearSelection.LinkMode -> WearChoicePage(stringResource(R.string.wear_link_mode),
            LinkModes.map { it to linkModeLabel(it) },
            preferences.link, onBack, message = message,
            icon = { selectionIcon(selection, it) },
            onChoose = preferenceViewModel::setLink)
        WearSelection.Language -> {
            val tags = listOf("en", "ar", "az", "bn", "bs", "da", "de", "es", "et", "fa", "fil", "fr", "gl", "hi", "hr", "hu",
                "id", "it", "he", "ja", "kn", "ko", "lt", "lv", "mr", "ms", "nl", "pl", "pt", "pt-BR", "ro", "ru", "sl", "sr",
                "te", "th", "tk", "tr", "uk", "vi", "zh-CN", "zh-HK", "zh-TW")
            val choices = listOf("system" to stringResource(R.string.language_system_default)) + tags.map {
                val locale = Locale.forLanguageTag(it)
                locale.settingTag() to locale.getDisplayName(locale)
            }.sortedBy { it.second }
            WearChoicePage(stringResource(R.string.settings_language), choices,
                state.currentAppLocale?.settingTag() ?: "system", onBack, icon = { selectionIcon(selection, it) }) {
                onAction(SettingsUiAction.SetLanguage(it))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onAction(SettingsUiAction.RestartActivity)
            }
        }
        WearSelection.SuCompat -> WearChoicePage(stringResource(R.string.settings_sucompat),
            (0..2).map { it.toString() to suCompatModeLabel(it) },
            state.suCompatMode.toString(), onBack, enabled = state.suStatus == "supported",
            icon = { selectionIcon(selection, it) }) { onAction(SettingsUiAction.SetSuCompatMode(it.toInt())) }
    }
}

private fun selectionIcon(selection: WearSelection, value: String): ImageVector = when (selection) {
    WearSelection.ScreenShape -> when (value) {
        "round" -> Icons.TwoTone.Circle
        "square" -> Icons.TwoTone.Square
        else -> Icons.TwoTone.AutoAwesome
    }
    WearSelection.Language -> Icons.TwoTone.Translate
    WearSelection.SuCompat -> Icons.TwoTone.RemoveModerator
    WearSelection.LinkMode -> when (value) {
        "webview" -> Icons.TwoTone.Language
        "phone" -> Icons.TwoTone.Smartphone
        else -> Icons.TwoTone.AutoAwesome
    }
    WearSelection.PickerMode -> if (value == "auto") Icons.TwoTone.AutoAwesome else Icons.TwoTone.FolderOpen
}

/** The `language` or `language_COUNTRY` form stored in `app_locale` and parsed by LocaleHelper, as on the phone. */
private fun Locale.settingTag() = if (country.isEmpty()) language else "${language}_$country"

private val PickerModes = listOf("auto", "builtin", "system")
private val LinkModes = listOf("auto", "webview", "phone")
private val ScreenShapes = listOf("auto", "round", "square")

/** Labels shared by the choice pages and the current-value summaries in settings. */
@Composable
internal fun pickerModeLabel(mode: String) = stringResource(when (mode) {
    "builtin" -> R.string.wear_builtin_picker
    "system" -> R.string.wear_system_picker
    else -> R.string.wear_auto
})

@Composable
internal fun screenShapeLabel(shape: String) = stringResource(when (shape) {
    "round" -> R.string.wear_shape_round
    "square" -> R.string.wear_shape_square
    else -> R.string.wear_auto
})

@Composable
internal fun linkModeLabel(mode: String) = stringResource(when (mode) {
    "webview" -> R.string.wear_watch_webui
    "phone" -> R.string.wear_phone
    else -> R.string.wear_auto
})

@Composable
internal fun suCompatModeLabel(mode: Int) = stringResource(when (mode) {
    1 -> R.string.settings_mode_disable_until_reboot
    2 -> R.string.settings_mode_disable_always
    else -> R.string.settings_mode_default
})
