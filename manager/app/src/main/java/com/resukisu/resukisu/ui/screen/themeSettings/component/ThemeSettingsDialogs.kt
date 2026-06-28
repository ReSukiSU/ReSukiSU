package com.resukisu.resukisu.ui.screen.themeSettings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.maxkeppeker.sheets.core.models.base.Header
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.list.ListDialog
import com.maxkeppeler.sheets.list.models.ListOption
import com.maxkeppeler.sheets.list.models.ListSelection
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.appPreferences
import com.resukisu.resukisu.ui.screen.themeSettings.util.launchSystemLanguageSettings
import com.resukisu.resukisu.ui.screen.themeSettings.util.useSystemLanguageSettings
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.viewmodel.SettingsUiState
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import android.graphics.Color as AndroidColor

@Composable
fun ThemeSettingsDialogs(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    if (state.showThemeColorDialog) {
        val context = LocalContext.current
        ThemeColorDialog(
            currentSeedColor = ThemeConfig.seedColor,
            onColorSelected = { seedColor ->
                viewModel.handleThemeColorChange(context, seedColor)
                viewModel.setThemeColorDialogVisible(false)
            },
            onDismiss = { viewModel.setThemeColorDialogVisible(false) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionDialog(
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val languageUseSystemDefault = stringResource(R.string.language_system_default)
    val systemLanguage = stringResource(R.string.settings_language)
    val prefs = context.appPreferences

    if (useSystemLanguageSettings) {
        launchSystemLanguageSettings(context)
        onDismiss()
    } else {
        val supportedLocales = remember {
            val locales = mutableListOf<java.util.Locale>()
            locales.add(java.util.Locale.ROOT)

            val resourceDirs = listOf(
                "ar", "bg", "de", "fa", "fr", "hu", "in", "it",
                "ja", "ko", "pl", "pt-rBR", "ru", "th", "tr",
                "uk", "vi", "zh-rCN", "zh-rTW"
            )

            resourceDirs.forEach { dir ->
                try {
                    val locale = when {
                        dir.contains("-r") -> {
                            val parts = dir.split("-r")
                            java.util.Locale.Builder()
                                .setLanguage(parts[0])
                                .setRegion(parts[1])
                                .build()
                        }

                        else -> java.util.Locale.Builder()
                            .setLanguage(dir)
                            .build()
                    }

                    val config = android.content.res.Configuration()
                    config.setLocale(locale)
                    val localizedContext = context.createConfigurationContext(config)
                    val testString = localizedContext.getString(R.string.settings_language)

                    if (testString != systemLanguage || locale.language == "en") {
                        locales.add(locale)
                    }
                } catch (_: Exception) {
                }
            }

            val sortedLocales = locales.drop(1).sortedBy { it.getDisplayName(it) }
            mutableListOf<java.util.Locale>().apply {
                add(locales.first())
                addAll(sortedLocales)
            }
        }

        val allOptions = supportedLocales.map { locale ->
            val tag = if (locale == java.util.Locale.ROOT) {
                "system"
            } else if (locale.country.isEmpty()) {
                locale.language
            } else {
                "${locale.language}_${locale.country}"
            }

            val displayName = if (locale == java.util.Locale.ROOT) {
                languageUseSystemDefault
            } else {
                locale.getDisplayName(locale)
            }

            tag to displayName
        }

        val currentLocale = prefs.getString("app_locale", "system") ?: "system"
        val options = allOptions.map { (tag, displayName) ->
            ListOption(
                titleText = displayName,
                selected = currentLocale == tag
            )
        }

        var selectedIndex by remember {
            mutableIntStateOf(allOptions.indexOfFirst { (tag, _) -> currentLocale == tag })
        }

        ListDialog(
            state = rememberUseCaseState(
                visible = true,
                onFinishedRequest = {
                    if (selectedIndex >= 0 && selectedIndex < allOptions.size) {
                        val newLocale = allOptions[selectedIndex].first
                        prefs.putString("app_locale", newLocale)
                        onLanguageSelected(newLocale)
                    }
                    onDismiss()
                },
                onCloseRequest = {
                    onDismiss()
                }
            ),
            header = Header.Default(
                title = stringResource(R.string.settings_language),
            ),
            selection = ListSelection.Single(
                showRadioButtons = true,
                options = options
            ) { index, _ ->
                selectedIndex = index
            }
        )
    }
}

@Composable
fun ThemeColorDialog(
    currentSeedColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(currentSeedColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(currentSeedColor, it) }
    }
    var hue by remember(currentSeedColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(currentSeedColor) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(currentSeedColor) { mutableFloatStateOf(initialHsv[2]) }
    val selectedColor = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_theme_color)) },
        text = {
            ColorPicker(
                color = Color(selectedColor),
                hue = hue,
                saturation = saturation,
                value = value,
                onHueChange = { hue = it },
                onSaturationChange = { saturation = it },
                onValueChange = { value = it },
            )
        },
        confirmButton = {
            Button(onClick = { onColorSelected(selectedColor) }) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ColorPicker(
    color: Color,
    hue: Float,
    saturation: Float,
    value: Float,
    onHueChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onValueChange: (Float) -> Unit,
) {
    Column {
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
                    .background(color)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = color.toHexString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        ColorSlider(
            label = "H",
            value = hue,
            valueRange = 0f..360f,
            onValueChange = onHueChange
        )
        ColorSlider(
            label = "S",
            value = saturation,
            valueRange = 0f..1f,
            onValueChange = onSaturationChange
        )
        ColorSlider(
            label = "V",
            value = value,
            valueRange = 0f..1f,
            onValueChange = onValueChange
        )
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(end = 12.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier.padding(start = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text( // Some stupid way to solve measure problem
                text = "360.00",
                style = MaterialTheme.typography.labelMediumEmphasized.copy(
                    fontFeatureSettings = "tnum"
                ),
                modifier = Modifier.alpha(0f)
            )
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.labelMediumEmphasized.copy(
                    fontFeatureSettings = "tnum"
                ),
            )
        }
    }
}

private fun Color.toHexString(): String {
    val argb = AndroidColor.rgb(
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255)
    )
    return "#%06X".format(argb and 0x00FFFFFF)
}
