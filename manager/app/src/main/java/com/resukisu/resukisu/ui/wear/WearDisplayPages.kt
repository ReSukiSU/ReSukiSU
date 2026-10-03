package com.resukisu.resukisu.ui.wear

import androidx.core.graphics.toColorInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.FormatSize
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconToggleButton
import androidx.wear.compose.material3.IconToggleButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.wear.component.WearActionButton
import com.resukisu.resukisu.ui.wear.component.WearInfoCard
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearScaledItem
import com.resukisu.resukisu.ui.wear.component.WearSectionHeader
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.wear.component.WearSliderCard
import com.resukisu.resukisu.ui.wear.component.WearSubPage
import com.resukisu.resukisu.ui.wear.component.WearTextInputPage
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.viewmodel.SettingsUiAction
import com.resukisu.resukisu.ui.viewmodel.SettingsUiState
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

private fun Int.toHex() = "#%06X".format(this and 0xFFFFFF)

/**
 * One scrolling page in three sections: accent color (follow-system switch and swatches), custom
 * color (hex value and its apply action, the screen's single high-emphasis button) and background
 * (image, removal and dimming).
 */
@Composable
internal fun WearColorPage(state: SettingsUiState, message: String?, onBack: () -> Unit, onAction: (SettingsUiAction) -> Unit, onImage: () -> Unit) {
    val config = koinInject<ThemeConfig>()
    val resources = LocalResources.current
    val swatches = remember(resources) {
        resources.obtainTypedArray(R.array.wear_theme_swatches).run {
            try { List(length()) { getColor(it, 0) } } finally { recycle() }
        }
    }
    var hex by rememberSaveable { mutableStateOf(config.seedColor.toHex()) }
    var editingHex by rememberSaveable { mutableStateOf(false) }
    val valid = hex.matches(Regex("#[0-9a-fA-F]{6}"))
    if (editingHex) {
        WearSubPage({ editingHex = false }) {
            WearTextInputPage(stringResource(R.string.wear_custom_color), hex) { text ->
                hex = text.trim().uppercase().let { if (it.startsWith("#")) it else "#$it" }
                editingHex = false
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            // Keep three equally sized cells; each toggle leaves 5% space on each side.
            val columns = 3
            WearList(onBack = onBack) { spec ->
                item { WearPageHeader(spec, stringResource(R.string.theme_color)) }
                if (!message.isNullOrBlank()) item { WearInfoCard(spec) { Text(message) } }
                item {
                    WearSettingsSwitchWidget(spec, stringResource(R.string.dynamic_color_title), state.useDynamicColor,
                        { onAction(SettingsUiAction.SetDynamicColor(it)) }, icon = Icons.TwoTone.Palette,
                        secondaryLabel = stringResource(R.string.dynamic_color_summary))
                }
                item { WearSectionHeader(spec, stringResource(R.string.choose_theme_color)) }
                // Swatches share the row equally so they fill the screen width (margins stretch evenly).
                swatches.chunked(columns).forEachIndexed { index, row ->
                    item(key = "swatches-$index") {
                        WearScaledItem(spec) {
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { value ->
                                    Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                                        ColorSwatch(Color(value), !state.useDynamicColor && config.seedColor == Color(value).toArgb()) {
                                            hex = Color(value).toArgb().toHex()
                                            onAction(SettingsUiAction.SetDynamicColor(false))
                                            onAction(SettingsUiAction.SetThemeColor(Color(value).toArgb()))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { WearSectionHeader(spec, stringResource(R.string.wear_custom_color)) }
                item { HexColorButton(spec, hex, valid) { editingHex = true } }
                item {
                    WearActionButton(spec, Icons.TwoTone.Check, stringResource(R.string.background_crop_apply), {
                        onAction(SettingsUiAction.SetDynamicColor(false))
                        onAction(SettingsUiAction.SetThemeColor(hex.toColorInt()))
                    }, enabled = valid, colors = ButtonDefaults.buttonColors())
                }
                item { WearSectionHeader(spec, stringResource(R.string.settings_custom_background)) }
                item {
                    WearActionButton(spec, Icons.TwoTone.Image, stringResource(R.string.settings_custom_background_summary), onImage,
                        colors = ButtonDefaults.filledTonalButtonColors())
                }
                // Dimming only affects the custom background image, so it appears with one.
                if (state.isCustomBackgroundEnabled) {
                    item {
                        WearActionButton(spec, Icons.TwoTone.Delete, stringResource(R.string.wear_remove_background),
                            { onAction(SettingsUiAction.RemoveCustomBackground) }, colors = ButtonDefaults.filledTonalButtonColors())
                    }
                    item {
                        WearSliderCard(spec, Icons.TwoTone.Image, stringResource(R.string.settings_background_dim),
                            "${(state.backgroundDim * 100).roundToInt()}%", state.backgroundDim,
                            { onAction(SettingsUiAction.SetBackgroundDim(it)) }, valueRange = 0f..1f, steps = 19)
                    }
                }
            }
        }
    }
}

/** A swatch toggle: the selected color morphs its shape and shows a check and an outline, not color alone. */
@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onSelect: () -> Unit) {
    IconToggleButton(
        checked = selected,
        onCheckedChange = { onSelect() },
        // Fill the wider weighted cell while retaining the Wear toggle's selection animation.
        modifier = Modifier.fillMaxWidth(0.9f).heightIn(min = 48.dp),
        colors = IconToggleButtonDefaults.colors(
            checkedContainerColor = color, checkedContentColor = Color.Black,
            uncheckedContainerColor = color, uncheckedContentColor = Color.Black,
        ),
        shapes = IconToggleButtonDefaults.variantAnimatedShapes(),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
    ) {
        Icon(
            Icons.TwoTone.Check,
            contentDescription = "${stringResource(R.string.theme_color)} ${color.toArgb().toHex()}",
            tint = if (selected) Color.Black else Color.Transparent,
        )
    }
}

/**
 * The editable hex value, previewed by a color dot in the icon slot; the `#RRGGBB` hint is its
 * secondary label, and the button turns red while the value is invalid.
 */
@Composable
private fun TransformingLazyColumnItemScope.HexColorButton(spec: TransformationSpec, hex: String, valid: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        transformation = SurfaceTransformation(spec),
        colors = if (valid) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            secondaryContentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        icon = {
            Box(Modifier.size(ButtonDefaults.IconSize).background(
                if (valid) Color(hex.toColorInt()) else Color.Transparent, CircleShape))
        },
        secondaryLabel = { Text(stringResource(R.string.wear_invalid_color), maxLines = 2) },
    ) { Text(hex, maxLines = 1) }
}

/**
 * The pending UI scale in a slider card; the apply action is the single high-emphasis
 * button and runs only when the scale changed, and restoring the system scale only resets the
 * pending value.
 */
@Composable
internal fun WearDpiPage(state: SettingsUiState, message: String?, onBack: () -> Unit, onAction: (SettingsUiAction) -> Unit) {
    val systemDpi = state.systemDpi.coerceAtLeast(1)
    val pending = (state.tempDpi.toFloat() / systemDpi).coerceIn(0.75f, 1.5f)
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_display_scaling)) }
        if (!message.isNullOrBlank()) item { WearInfoCard(spec) { Text(message) } }
        // The scale label and value sit inside the slider card, above its track.
        item {
            WearSliderCard(spec, Icons.TwoTone.FormatSize, stringResource(R.string.app_dpi_title),
                stringResource(R.string.wear_ui_scale, pending), pending,
                { onAction(SettingsUiAction.SetTempDpi((it * systemDpi).roundToInt())) },
                valueRange = 0.75f..1.5f, steps = 14)
        }
        item {
            WearActionButton(spec, Icons.TwoTone.Check, stringResource(R.string.dpi_apply_settings),
                { onAction(SettingsUiAction.ApplyDpi) }, state.tempDpi != state.currentDpi, colors = ButtonDefaults.buttonColors())
        }
        item {
            WearActionButton(spec, Icons.TwoTone.Restore, stringResource(R.string.language_system_default),
                { onAction(SettingsUiAction.SetTempDpi(systemDpi)) }, state.tempDpi != systemDpi,
                secondaryText = stringResource(R.string.wear_ui_scale, 1f), colors = ButtonDefaults.filledTonalButtonColors())
        }
    }
}
