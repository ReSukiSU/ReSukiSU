package zako.zako.zako.zakoui.screen.moreSettings.component

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.maxkeppeker.sheets.core.models.base.Header
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.list.ListDialog
import com.maxkeppeler.sheets.list.models.ListOption
import com.maxkeppeler.sheets.list.models.ListSelection
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.theme.ThemeColors
import com.resukisu.resukisu.ui.theme.ThemeConfig
import zako.zako.zako.zakoui.screen.moreSettings.MoreSettingsHandlers
import zako.zako.zako.zakoui.screen.moreSettings.state.MoreSettingsState
import zako.zako.zako.zakoui.screen.moreSettings.util.LocaleHelper

@Composable
fun MoreSettingsDialogs(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers
) {
    // 主题色选择对话框
    if (state.showThemeColorDialog) {
        // FIXME Dynamic calculate
        ThemeColorDialog(
            onColorSelected = { theme ->
                handlers.handleThemeColorChange(theme)
                state.showThemeColorDialog = false
            },
            onDismiss = { state.showThemeColorDialog = false }
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
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Check if should use system language settings
    if (LocaleHelper.useSystemLanguageSettings) {
        // Android 13+ - Jump to system settings
        LocaleHelper.launchSystemLanguageSettings(context)
        onDismiss()
    } else {
        // Android < 13 - Show app language selector
        // Dynamically detect supported locales from resources
        val supportedLocales = remember {
            val locales = mutableListOf<java.util.Locale>()

            // Add system default first
            locales.add(java.util.Locale.ROOT) // This will represent "System Default"

            // Dynamically detect available locales by checking resource directories
            // FIXME From manifest
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

                    // Test if this locale has translated resources
                    val config = android.content.res.Configuration()
                    config.setLocale(locale)
                    val localizedContext = context.createConfigurationContext(config)

                    // Try to get a translated string to verify the locale is supported
                    val testString = localizedContext.getString(R.string.settings_language)
                    val defaultString = context.getString(R.string.settings_language)

                    // If the string is different or it's English, it's supported
                    if (testString != defaultString || locale.language == "en") {
                        locales.add(locale)
                    }
                } catch (_: Exception) {
                    // Skip unsupported locales
                }
            }

            // Sort by display name
            val sortedLocales = locales.drop(1).sortedBy { it.getDisplayName(it) }
            mutableListOf<java.util.Locale>().apply {
                add(locales.first()) // System default first
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
                context.getString(R.string.language_system_default)
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
                        prefs.edit { putString("app_locale", newLocale) }
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
    onColorSelected: (ThemeColors) -> Unit,
    onDismiss: () -> Unit
) {
    val themeColorOptions = listOf(
        stringResource(R.string.color_default) to ThemeColors.Default,
        stringResource(R.string.color_green) to ThemeColors.Green,
        stringResource(R.string.color_purple) to ThemeColors.Purple,
        stringResource(R.string.color_orange) to ThemeColors.Orange,
        stringResource(R.string.color_pink) to ThemeColors.Pink,
        stringResource(R.string.color_gray) to ThemeColors.Gray,
        stringResource(R.string.color_yellow) to ThemeColors.Yellow
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_theme_color)) },
        text = {
            Column {
                themeColorOptions.forEach { (name, theme) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onColorSelected(theme) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isDark = isSystemInDarkTheme()
                        Box(
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ColorCircle(
                                    color = if (isDark) theme.primaryDark else theme.primaryLight,
                                    isSelected = false,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                                ColorCircle(
                                    color = if (isDark) theme.secondaryDark else theme.secondaryLight,
                                    isSelected = false,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                                ColorCircle(
                                    color = if (isDark) theme.tertiaryDark else theme.tertiaryLight,
                                    isSelected = false,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                        Text(name)
                        Spacer(modifier = Modifier.weight(1f))
                        // 当前选中的主题显示选中标记
                        if (ThemeConfig.currentTheme::class == theme::class) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
