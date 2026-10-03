package com.resukisu.resukisu.ui.wear

import android.os.Build
import android.os.PowerManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.automirrored.twotone.Sort
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.ui.wear.component.settings.LazySegmentedColumn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.wear.component.WearActionButton
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.viewmodel.ModuleUiAction
import com.resukisu.resukisu.ui.viewmodel.ModuleUiState
import com.resukisu.resukisu.ui.viewmodel.SortType
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiAction
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiState

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearSuperUserPanel(state: SuperUserUiState, onBack: () -> Unit, onAction: (SuperUserUiAction) -> Unit) {
    val listState = rememberPanelListState()
    WearList(onBack = onBack, onClosePanel = onBack, snap = true, listState = listState) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_filter_sort)) }
        item { WearSettingsSwitchWidget(spec, stringResource(R.string.show_system_apps), state.showSystemApps,
            { onAction(SuperUserUiAction.SetShowSystemApps(it)) }) }
        LazySegmentedColumn(SortType.entries, { it }) { sort ->
            RadioButton(
                selected = sort == state.currentSortType,
                onSelect = { onAction(SuperUserUiAction.SetSort(sort)) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                transformation = SurfaceTransformation(spec),
                icon = { Icon(Icons.AutoMirrored.TwoTone.Sort, contentDescription = null) },
                label = { Text(stringResource(sort.displayNameRes), maxLines = 3, overflow = TextOverflow.Ellipsis) },
            )
        }
        item { WearSettingsSwitchWidget(spec, stringResource(R.string.reverse_order), state.reverseOrder,
            { onAction(SuperUserUiAction.SetReverseOrder(it)) }) }
    }
}

@Composable
internal fun WearModulePanel(state: ModuleUiState, onBack: () -> Unit, onAction: (ModuleUiAction) -> Unit) {
    val listState = rememberPanelListState()
    WearList(onBack = onBack, onClosePanel = onBack, snap = true, listState = listState) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.wear_sort)) }
        item { WearSettingsSwitchWidget(spec, stringResource(R.string.module_sort_action_first), state.sortActionFirst,
            { onAction(ModuleUiAction.Sort(state.sortEnabledFirst, it)) }) }
        item { WearSettingsSwitchWidget(spec, stringResource(R.string.module_sort_enabled_first), state.sortEnabledFirst,
            { onAction(ModuleUiAction.Sort(it, state.sortActionFirst)) }) }
    }
}

@Composable
internal fun WearRebootPanel(rootAvailable: Boolean, onBack: () -> Unit, onReboot: (String) -> Unit) {
    val listState = rememberPanelListState()
    val context = LocalContext.current
    val methods = linkedMapOf(
        R.string.reboot to "", R.string.reboot_soft to "soft_reboot", R.string.reboot_recovery to "recovery",
        R.string.reboot_bootloader to "bootloader", R.string.reboot_download to "download", R.string.reboot_edl to "edl",
    )
    @Suppress("DEPRECATION")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        context.getSystemService(PowerManager::class.java)?.isRebootingUserspaceSupported == true)
        methods[R.string.reboot_userspace] = "userspace"
    WearList(onBack = onBack, onClosePanel = onBack, snap = true, listState = listState) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.reboot)) }
        methods.forEach { (label, reason) -> item {
            WearActionButton(spec, Icons.TwoTone.RestartAlt, stringResource(label), { onReboot(reason) }, rootAvailable,
                colors = ButtonDefaults.filledTonalButtonColors())
        } }
    }
}

@Composable
private fun rememberPanelListState(): androidx.wear.compose.foundation.lazy.TransformingLazyColumnState {
    val state = rememberTransformingLazyColumnState()
    val active = LocalScreenIsActive.current
    // A menu opens at its heading even when its saveable state survived a previous visit or exit animation.
    LaunchedEffect(active) { if (active) state.scrollToItem(0) }
    return state
}
