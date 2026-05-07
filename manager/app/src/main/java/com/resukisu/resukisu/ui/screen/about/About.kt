package com.resukisu.resukisu.ui.screen.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.WarningCard
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SplicedColumnGroup
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Navigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(
                ),
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                title = { Text(text = stringResource(id = R.string.about)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    AppBackButton(
                        onClick = {
                            navigator.pop()
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        if (ThemeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                    scrolledContainerColor =
                        if (ThemeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                ),
            )
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        containerColor = Color.Transparent,
    ) { innerPadding ->
        val uriHandler = LocalUriHandler.current

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource(),
        ) {
            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            }

            item {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    StatusCard()
                }
            }

            item {
                WarningCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                        alpha = CardConfig.cardAlpha
                    ),
                    message = AnnotatedString.fromHtml(
                        htmlString = stringResource(
                            id = R.string.about_anime_character_sticker,
                            "<b>怡子曰曰</b>",
                            "<b>明风 OuO</b>",
                            "<b><a href=\"https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.txt\">CC BY-NC-SA 4.0</a></b>"
                        ),
                        linkStyles = TextLinkStyles(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            ),
                            pressedStyle = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                background = MaterialTheme.colorScheme.secondaryContainer,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    )
                )
            }

            item {
                SplicedColumnGroup(
                    title = stringResource(R.string.about)
                ) {
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.Rounded.Code,
                            title = stringResource(R.string.get_source_code),
                            description = stringResource(R.string.get_source_code_detail),
                            onClick = { uriHandler.openUri("https://github.com/ReSukiSU/ReSukiSU") }
                        )
                    }
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.Rounded.Group,
                            title = stringResource(R.string.join_telegram_group),
                            description = stringResource(R.string.join_telegram_group_detail),
                            onClick = { uriHandler.openUri("https://t.me/ReSukiSU") }
                        )
                    }
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.Rounded.Copyright,
                            title = stringResource(R.string.open_source_license),
                            description = stringResource(R.string.open_source_license_settings_description),
                            onClick = {
                                navigator.push(Route.OpenSourceLicense)
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
            }
        }
    }
}

@Preview
@Composable
fun AboutScreenPreview() {
    CompositionLocalProvider(
        LocalNavigator provides Navigator(Route.About)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            AboutScreen()
        }
    }
}

@Composable
private fun StatusCard() {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                alpha = CardConfig.cardAlpha
            ),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.secondary) {
                Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Image(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        painter = rememberDrawablePainter(
                            drawable = ContextCompat.getDrawable(
                                LocalContext.current,
                                R.mipmap.ic_launcher
                            )
                        ),
                        contentDescription = stringResource(id = R.string.app_name)
                    )
                }
            }
            ProvideTextStyle(value = MaterialTheme.typography.titleLarge) {
                Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(
                        modifier = Modifier,
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Box {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}