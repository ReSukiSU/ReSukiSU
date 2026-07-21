package com.resukisu.resukisu.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.theme.LocalEnableBlur
import com.resukisu.resukisu.ui.util.BlurredBar
import com.resukisu.resukisu.ui.util.LkmSelection
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
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.MoveFile
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix rendering of ReSukiSU's install flow. Keeps ReSukiSU's install methods
 * (SelectFile / DirectInstall / DirectInstallToInactiveSlot / HorizonKernel) and
 * flash logic; only the presentation is borrowed from tiann's Miuix screen.
 *
 * @author weishu
 * @date 2024/3/12.
 */
@Composable
internal fun InstallScreenMiuix(
    isGKI: Boolean,
    installMethod: InstallMethod?,
    installMethodOptions: List<InstallMethod>,
    lkmSelection: LkmSelection,
    displayPartitions: List<String>,
    partitionSelectionIndex: Int,
    slotSuffix: String,
    canSelectPartition: Boolean,
    horizonSlot: String?,
    onBack: () -> Unit,
    onMethodClick: (InstallMethod) -> Unit,
    onUploadLkm: () -> Unit,
    onClearLkm: () -> Unit,
    onSelectPartition: (Int) -> Unit,
    onNext: () -> Unit,
) {
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            TopBar(
                onBack = onBack,
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
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(top = 12.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SelectInstallMethod(
                            options = installMethodOptions,
                            selectedMethod = installMethod,
                            onClick = onMethodClick,
                        )
                    }
                    AnimatedVisibility(
                        visible = canSelectPartition,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            OverlayDropdownPreference(
                                items = displayPartitions,
                                selectedIndex = partitionSelectionIndex,
                                title = "${stringResource(R.string.install_select_partition)} (${slotSuffix})",
                                onSelectedIndexChange = onSelectPartition,
                                startAction = {
                                    Icon(
                                        MiuixIcons.ConvertFile,
                                        tint = colorScheme.onSurface,
                                        modifier = Modifier.padding(end = 12.dp),
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                    if (isGKI) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            BasicComponent(
                                title = stringResource(id = R.string.install_upload_lkm_file),
                                summary = (lkmSelection as? LkmSelection.LkmUri)?.let {
                                    stringResource(id = R.string.selected_lkm, it.uri.lastPathSegment ?: "(file)")
                                },
                                onClick = onUploadLkm,
                                startAction = {
                                    Icon(
                                        MiuixIcons.MoveFile,
                                        tint = colorScheme.onSurface,
                                        modifier = Modifier.padding(end = 12.dp),
                                        contentDescription = null
                                    )
                                },
                                endActions = {
                                    if (lkmSelection is LkmSelection.LkmUri) {
                                        IconButton(onClick = onClearLkm) {
                                            Icon(
                                                MiuixIcons.Close,
                                                modifier = Modifier.size(16.dp),
                                                contentDescription = stringResource(android.R.string.cancel),
                                                tint = colorScheme.onSurfaceVariantActions
                                            )
                                        }
                                    } else {
                                        val layoutDirection = LocalLayoutDirection.current
                                        Icon(
                                            modifier = Modifier
                                                .size(width = 10.dp, height = 16.dp)
                                                .graphicsLayer {
                                                    scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                                                }
                                                .align(Alignment.CenterVertically),
                                            imageVector = MiuixIcons.Basic.ArrowRight,
                                            contentDescription = null,
                                            tint = colorScheme.onSurfaceVariantActions,
                                        )
                                    }
                                }
                            )
                        }
                    }
                    horizonSlot?.let { slot ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    id = R.string.selected_slot,
                                    if (slot == "a") stringResource(id = R.string.slot_a)
                                    else stringResource(id = R.string.slot_b)
                                ),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    TextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        text = stringResource(id = R.string.install_next),
                        enabled = installMethod != null,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = onNext
                    )
                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectInstallMethod(
    options: List<InstallMethod>,
    selectedMethod: InstallMethod?,
    onClick: (InstallMethod) -> Unit,
) {
    Column {
        options.forEach { option ->
            val interactionSource = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = option.javaClass == selectedMethod?.javaClass,
                        onValueChange = { onClick(option) },
                        role = Role.RadioButton,
                        indication = LocalIndication.current,
                        interactionSource = interactionSource
                    )
            ) {
                CheckboxPreference(
                    title = stringResource(id = option.label),
                    summary = option.summary,
                    checked = option.javaClass == selectedMethod?.javaClass,
                    onCheckedChange = { onClick(option) },
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = stringResource(R.string.install),
            navigationIcon = {
                IconButton(
                    onClick = onBack
                ) {
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
