package com.resukisu.resukisu.ui.screen.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.miuix.WarningCard
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.theme.LocalEnableBlur
import com.resukisu.resukisu.ui.util.BlurredBar
import com.resukisu.resukisu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix rendering of ReSukiSU's open-source-license screen. Renders the
 * AboutLibraries data with a native miuix LazyColumn (scroll-end haptic +
 * overscroll like every other Miuix screen) and shows the per-library detail
 * in a miuix OverlayDialog.
 */
@Composable
fun OpenSourceLicenseScreenMiuix() {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    val libraries by produceLibraries(R.raw.aboutlibraries)
    var selectedLibrary by remember { mutableStateOf<Library?>(null) }

    Scaffold(
        topBar = {
            TopBar(
                onBack = { navigator.pop() },
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .overScrollVertical()
                    .padding(horizontal = 16.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                items(libraries?.libraries.orEmpty()) { library ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        BasicComponent(
                            title = library.name,
                            summary = buildString {
                                library.artifactVersion?.let { append(it) }
                                val lic = library.licenses.joinToString(", ") { it.name }
                                if (lic.isNotEmpty()) {
                                    if (isNotEmpty()) append("  •  ")
                                    append(lic)
                                }
                            }.ifEmpty { null },
                            onClick = { selectedLibrary = library },
                        )
                    }
                }
            }
        }
    }

    // Keep the last-selected library composed through the dialog's exit animation.
    val dialogLibrary = remember { mutableStateOf<Library?>(null) }
    if (selectedLibrary != null) dialogLibrary.value = selectedLibrary
    val lib = dialogLibrary.value
    OverlayDialog(
        show = selectedLibrary != null,
        title = lib?.name ?: "",
        onDismissRequest = { selectedLibrary = null },
        content = {
            if (lib != null) {
                val uriHandler = LocalUriHandler.current
                WarningCard(
                    message = stringResource(
                        R.string.license,
                        lib.licenses.joinToString(separator = ", ") { it.name }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    color = colorScheme.tertiaryContainer,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .scrollEndHaptic()
                        .overScrollVertical(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    overscrollEffect = null,
                ) {
                    items(lib.licenses.toList()) { license ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = license.name,
                                    color = colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            license.url?.let { url -> uriHandler.openUri(url) }
                                        }
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = license.licenseContent
                                        ?: stringResource(R.string.no_license_text),
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    lib.website?.let { url ->
                        TextButton(
                            text = stringResource(R.string.visit_home_page),
                            onClick = { uriHandler.openUri(url) },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    TextButton(
                        text = stringResource(R.string.close),
                        onClick = { selectedLibrary = null },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    )
}

@Composable
private fun TopBar(
    onBack: () -> Unit,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = stringResource(R.string.open_source_license),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    val layoutDirection = LocalLayoutDirection.current
                    Icon(
                        modifier = Modifier.graphicsLayer {
                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                        },
                        imageVector = MiuixIcons.Back,
                        tint = colorScheme.onSurface,
                        contentDescription = null,
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}
