package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.FolderOff
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.Storage
import androidx.compose.material.icons.twotone.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.SuSFSConfig
import com.resukisu.resukisu.ui.wear.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.wear.component.WearInfoCard
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.wear.component.WearSubPage
import com.resukisu.resukisu.ui.wear.component.WearTextInputPage
import com.resukisu.resukisu.ui.viewmodel.SuSFSUiAction
import com.resukisu.resukisu.ui.viewmodel.SuSFSViewModel
import com.resukisu.resukisu.ui.viewmodel.awaitSuSFSSlotInfo
import kotlinx.coroutines.launch

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearSuSFSStandardPage(config: SuSFSConfig, busy: Boolean, message: String?, viewModel: SuSFSViewModel,
    onBack: () -> Unit, onCommand: (WearSuSFSCommand, () -> Unit) -> Unit) {
    var page by rememberSaveable { mutableStateOf("") }
    var field by rememberSaveable { mutableStateOf("") }
    var release by rememberSaveable { mutableStateOf(config.uname.release) }
    var version by rememberSaveable { mutableStateOf(config.uname.version) }
    var cmdline by rememberSaveable { mutableStateOf(config.cmdline_or_bootconfig) }
    var slotLoading by remember { mutableStateOf(false) }
    var slotFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unset = stringResource(R.string.susfs_standard_not_set)
    val route = if (field.isNotEmpty()) "field:$field" else page
    val shownPage = route
    if (shownPage.startsWith("field:")) {
        val shownField = shownPage.removePrefix("field:")
        WearSubPage({ field = "" }) {
            WearTextInputPage(stringResource(when (shownField) {
                "release" -> R.string.susfs_standard_uname_release
                "version" -> R.string.susfs_standard_uname_version
                else -> R.string.susfs_standard_cmdline_path
            }), when (shownField) { "release" -> release; "version" -> version; else -> cmdline }) {
                when (shownField) { "release" -> release = it.trim(); "version" -> version = it.trim(); else -> cmdline = it.trim() }
                field = ""
            }
        }
    } else if (shownPage.isNotEmpty()) {
        val back = { page = if (shownPage == "slots") "uname" else "" }
        WearSubPage(back) {
            WearList(isLoading = busy || slotLoading, onBack = back,
                onConfirm = if (shownPage == "slots") null else ({
                    val command: WearSuSFSCommand = if (shownPage == "uname") ({ SuSFSUiAction.SetUname(release, version, it) })
                        else ({ SuSFSUiAction.SetCmdlineOrBootconfig(cmdline, it) })
                    onCommand(command) { page = "" }
                })) { spec ->
                item { WearPageHeader(spec, stringResource(when (shownPage) {
                    "uname" -> R.string.susfs_standard_uname
                    "slots" -> R.string.susfs_standard_uname_tab_slot_info
                    else -> R.string.susfs_standard_cmdline_or_bootconfig
                })) }
                message?.let { item { WearInfoCard(spec) { Text(it) } } }
                if (shownPage == "uname") {
                    SegmentedColumn(listOf("release", "version"), { it }) { name ->
                        WearSettingsJumpPageWidget(spec, stringResource(if (name == "release")
                            R.string.susfs_standard_uname_release else R.string.susfs_standard_uname_version),
                            { field = name }, icon = if (name == "release") Icons.TwoTone.Memory else Icons.TwoTone.Info,
                            description = (if (name == "release") release else version).ifBlank { unset })
                    }
                    item { WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_standard_uname_tab_slot_info), {
                        page = "slots"
                        slotLoading = true
                        slotFailed = false
                        scope.launch {
                            try { slotFailed = awaitSuSFSSlotInfo(viewModel) == null }
                            finally { slotLoading = false }
                        }
                    }, icon = Icons.TwoTone.Storage) }
                } else if (shownPage == "slots") {
                    if (slotFailed) item {
                        WearInfoCard(spec) { Text(stringResource(R.string.susfs_standard_uname_slot_info_load_failed)) }
                    }
                    if (state.slotInfo?.isEmpty() == true) item {
                        WearInfoCard(spec) { Text(stringResource(R.string.susfs_standard_uname_slot_info_empty)) }
                    }
                    state.slotInfo?.takeUnless { slotFailed }?.sortedBy { it.slotName }?.forEach { slot -> item {
                        WearSettingsJumpPageWidget(spec, slot.slotName, {
                            onCommand({ SuSFSUiAction.SetUname(slot.uname.trim(), slot.buildTime.trim(), it) }) { page = "" }
                        }, icon = Icons.TwoTone.Storage, description = stringResource(R.string.wear_joined, slot.uname, slot.buildTime))
                    } }
                } else item {
                    WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_standard_cmdline_path), { field = "cmdline" },
                        icon = Icons.TwoTone.Terminal,
                        description = cmdline.ifBlank { unset })
                }
            }
        }
    } else WearList(isLoading = busy, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.susfs_tab_standard)) }
        message?.let { item { WearInfoCard(spec) { Text(it) } } }
        SegmentedColumn(listOf(
            Triple(R.string.susfs_standard_logging, R.string.susfs_standard_logging_desc, config.logging),
            Triple(R.string.susfs_standard_avc_log_spoofing, R.string.susfs_standard_avc_log_spoofing_desc, config.avc_log_spoofing),
            Triple(R.string.susfs_standard_hide_sus_mnts, R.string.susfs_standard_hide_sus_mnts_desc, config.hide_sus_mnts_for_non_su_procs),
        ), { it.first }) { (label, summary, value) ->
            WearSettingsSwitchWidget(spec, stringResource(label), value, { checked ->
                onCommand({ reply -> when (label) {
                    R.string.susfs_standard_logging -> SuSFSUiAction.EnableLog(checked, reply)
                    R.string.susfs_standard_avc_log_spoofing -> SuSFSUiAction.EnableAvcLogSpoofing(checked, reply)
                    else -> SuSFSUiAction.HideSusMnts(checked, reply)
                } }, {})
            }, icon = when (label) {
                R.string.susfs_standard_logging -> Icons.AutoMirrored.TwoTone.Article
                R.string.susfs_standard_avc_log_spoofing -> Icons.TwoTone.Policy
                else -> Icons.TwoTone.FolderOff
            }, secondaryLabel = stringResource(summary))
        }
        item { WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_standard_uname), {
            release = config.uname.release; version = config.uname.version; page = "uname"
        }, icon = Icons.TwoTone.Memory, description = stringResource(R.string.wear_joined, config.uname.release, config.uname.version)) }
        item { WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_standard_cmdline_or_bootconfig), {
            cmdline = config.cmdline_or_bootconfig; page = "cmdline"
        }, icon = Icons.TwoTone.Terminal, description = config.cmdline_or_bootconfig.ifBlank { unset }) }
    }
}
