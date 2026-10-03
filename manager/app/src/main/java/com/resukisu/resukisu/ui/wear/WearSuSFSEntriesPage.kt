package com.resukisu.resukisu.ui.wear

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.automirrored.twotone.AltRoute
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.FileOpen
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Storage
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.SuSFSConfig
import com.resukisu.resukisu.domain.model.SusKstatStatically
import com.resukisu.resukisu.domain.model.SusKstatType
import com.resukisu.resukisu.domain.model.UidScheme
import com.resukisu.resukisu.ui.wear.component.settings.LazySegmentedColumn
import com.resukisu.resukisu.ui.wear.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.wear.component.settings.WearChoicePage
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.wear.component.WearInfoCard
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearSubPage
import com.resukisu.resukisu.ui.wear.component.WearTextInputPage
import com.resukisu.resukisu.ui.wear.component.rememberWearConfirmDialog
import com.resukisu.resukisu.ui.viewmodel.SuSFSUiAction
import com.resukisu.resukisu.ui.viewmodel.SusKstatOperation
import com.resukisu.resukisu.ui.viewmodel.SuSFSViewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.CharacterCodingException

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

private val KstatFields = listOf(
    R.string.susfs_kstat_field_ino, R.string.susfs_kstat_field_dev, R.string.susfs_kstat_field_nlink,
    R.string.susfs_kstat_field_size, R.string.susfs_kstat_field_atime, R.string.susfs_kstat_field_atime_nsec,
    R.string.susfs_kstat_field_mtime, R.string.susfs_kstat_field_mtime_nsec, R.string.susfs_kstat_field_ctime,
    R.string.susfs_kstat_field_ctime_nsec, R.string.susfs_kstat_field_blocks, R.string.susfs_kstat_field_blksize,
)

private fun kstatLabel(type: SusKstatType): Int = when (type) {
    SusKstatType.Normal -> R.string.susfs_kstat_subtype_normal
    SusKstatType.FullClone -> R.string.susfs_kstat_subtype_full_clone
    SusKstatType.Statically -> R.string.susfs_kstat_subtype_statically
}

private fun uidLabel(scheme: UidScheme): Int = when (scheme) {
    UidScheme.NonApp -> R.string.susfs_uid_scheme_non_app
    UidScheme.RootExceptSu -> R.string.susfs_uid_scheme_root_except_su
    UidScheme.NonSu -> R.string.susfs_uid_scheme_non_su
    UidScheme.UnmountedApp -> R.string.susfs_uid_scheme_unmounted_app
    UidScheme.Unmounted -> R.string.susfs_uid_scheme_unmounted
}

@Composable
internal fun WearSuSFSEntriesPage(section: WearSuSFSSection, config: SuSFSConfig, busy: Boolean, message: String?,
    onBack: () -> Unit, onAdd: () -> Unit, onSelect: (String) -> Unit) {
    val entries = when (section) {
        WearSuSFSSection.Path -> config.sus_path.map { it.path to stringResource(if (it.is_loop)
            R.string.susfs_path_is_loop else R.string.susfs_path_is_not_loop) }
        WearSuSFSSection.Kstat -> config.sus_kstat.map { it.path to stringResource(kstatLabel(it.spoof_type)) }
        WearSuSFSSection.Redirect -> config.open_redirect.map { it.target_path to stringResource(R.string.susfs_redirect_entry_description,
            it.redirected_path, stringResource(uidLabel(it.uid_scheme))) }
        WearSuSFSSection.Map -> config.sus_map.map { it to null }
        else -> emptyList()
    }
    WearList(isLoading = busy, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(section.title)) }
        message?.let { item { WearInfoCard(spec) { Text(it) } } }
        if (section == WearSuSFSSection.Redirect) item {
            WearInfoCard(spec) {
                Text(stringResource(R.string.susfs_redirect_description))
                Text(stringResource(R.string.susfs_redirect_uid_schemes_description))
                Text(stringResource(R.string.susfs_redirect_important_notes))
            }
        }
        if (section == WearSuSFSSection.Kstat) item {
            WearInfoCard(spec) {
                Text(stringResource(R.string.kstat_config_description_add))
                Text(stringResource(R.string.kstat_config_description_update))
                Text(stringResource(R.string.kstat_config_description_update_full_clone))
                Text(stringResource(R.string.kstat_config_description_add_statically))
            }
        }
        item { WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_entry_manual_add), onAdd, icon = Icons.TwoTone.Add) }
        LazySegmentedColumn(entries.sortedBy { it.first }, { it.first }) { (path, summary) ->
            WearSettingsJumpPageWidget(spec, path, { onSelect(path) }, icon = section.icon, description = summary)
        }
        if (entries.isEmpty()) item {
            WearInfoCard(spec) {
                Text(stringResource(R.string.susfs_entry_no_entries))
                Text(stringResource(R.string.susfs_entry_no_entries_hint))
            }
        }
    }
}

@Composable
internal fun WearSuSFSDetailPage(section: WearSuSFSSection, config: SuSFSConfig, path: String, busy: Boolean,
    message: String?, onBack: () -> Unit, onCommand: (WearSuSFSCommand) -> Unit) {
    val remove = rememberWearConfirmDialog(stringResource(R.string.delete), stringResource(R.string.confirm_delete)) {
        onCommand { reply -> when (section) {
            WearSuSFSSection.Path -> SuSFSUiAction.RemoveSusPath(path, reply)
            WearSuSFSSection.Kstat -> SuSFSUiAction.RemoveSusKstat(path, reply)
            WearSuSFSSection.Redirect -> SuSFSUiAction.RemoveOpenRedirect(path, reply)
            else -> SuSFSUiAction.RemoveSusMap(path, reply)
        } }
    }
    WearList(isLoading = busy, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.susfs_entry_detail)) }
        message?.let { item { WearInfoCard(spec) { Text(it) } } }
        item { WearInfoCard(spec) {
            Text(stringResource(R.string.susfs_entry_path_label))
            Text(path)
            when (section) {
                WearSuSFSSection.Path -> config.sus_path.firstOrNull { it.path == path }?.let {
                    Text(stringResource(if (it.is_loop) R.string.susfs_path_is_loop else R.string.susfs_path_is_not_loop))
                }
                WearSuSFSSection.Kstat -> config.sus_kstat.firstOrNull { it.path == path }?.let {
                    Text(stringResource(kstatLabel(it.spoof_type)))
                    it.statically?.let { values ->
                        listOf(values.ino, values.dev, values.nlink, values.size, values.atime, values.atime_nsec,
                            values.mtime, values.mtime_nsec, values.ctime, values.ctime_nsec, values.blocks, values.blksize)
                            .forEachIndexed { index, value ->
                                Text(stringResource(R.string.wear_joined, stringResource(KstatFields[index]),
                                    value?.toString() ?: stringResource(R.string.susfs_value_default)))
                            }
                    }
                }
                WearSuSFSSection.Redirect -> config.open_redirect.firstOrNull { it.target_path == path }?.let {
                    Text(stringResource(R.string.susfs_redirect_redirected_path))
                    Text(it.redirected_path)
                    Text(stringResource(uidLabel(it.uid_scheme)))
                }
                else -> Unit
            }
        } }
        item { WearSettingsJumpPageWidget(spec, stringResource(R.string.delete), remove::show, icon = Icons.TwoTone.Delete) }
    }
}

@Composable
internal fun WearSuSFSAddPage(section: WearSuSFSSection, busy: Boolean, message: String?,
    viewModel: SuSFSViewModel, onBack: () -> Unit, onCommand: (List<WearSuSFSCommand>) -> Unit) {
    var field by rememberSaveable { mutableIntStateOf(-1) }
    var path by rememberSaveable { mutableStateOf("") }
    var redirected by rememberSaveable { mutableStateOf("") }
    var subtype by rememberSaveable { mutableStateOf(SusKstatType.Normal.name) }
    var loop by rememberSaveable { mutableStateOf(false) }
    var uid by rememberSaveable { mutableStateOf(UidScheme.NonApp.name) }
    var staticValues by rememberSaveable { mutableStateOf(List(12) { "" }) }
    var choosing by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var readingFile by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val readFailed = stringResource(R.string.susfs_entry_import_file_failed)
    val notText = stringResource(R.string.susfs_entry_import_file_not_text)
    val context = LocalContext.current
    // Reads the picked file as the phone's entry dialog does.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        readingFile = true
        fileError = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        checkNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
                            stream.readBytes().decodeToString(throwOnInvalidSequence = true)
                        }
                    }
                }.onSuccess { path = it; invalid = false }
                    .onFailure { fileError = if (it is CharacterCodingException) notText else readFailed }
            } finally { readingFile = false }
        }
    }
    val type = SusKstatType.valueOf(subtype)
    val route = when {
        field >= 0 -> "field:$field"
        choosing.isNotEmpty() -> "choice:$choosing"
        else -> ""
    }
    val shownPage = route
    if (shownPage.startsWith("field:")) {
        val shownField = shownPage.removePrefix("field:").toInt()
        WearSubPage({ field = -1 }) {
            WearTextInputPage(stringResource(when (shownField) {
                0 -> R.string.susfs_entry_path_label
                1 -> R.string.susfs_redirect_redirected_path
                else -> KstatFields[shownField - 2]
            }), when (shownField) { 0 -> path; 1 -> redirected; else -> staticValues[shownField - 2] },
                multiline = shownField == 0 && section != WearSuSFSSection.Redirect) {
                when (shownField) {
                    0 -> path = it.trim()
                    1 -> redirected = it.trim()
                    else -> staticValues = staticValues.toMutableList().also { values -> values[shownField - 2] = it.trim() }
                }
                invalid = false
                field = -1
            }
        }
    } else if (shownPage.startsWith("choice:")) {
        val shownChoice = shownPage.removePrefix("choice:")
        WearSubPage({ choosing = "" }) {
            val choices = when (shownChoice) {
                "uid" -> UidScheme.entries.map { it.name to stringResource(uidLabel(it)) }
                "path" -> listOf("normal" to stringResource(R.string.susfs_path_subtype_path),
                    "loop" to stringResource(R.string.susfs_path_subtype_loop))
                else -> SusKstatType.entries.map { it.name to stringResource(kstatLabel(it)) }
            }
            WearChoicePage(stringResource(if (shownChoice == "uid") R.string.susfs_redirect_uid_scheme else R.string.susfs_entry_select_subtype),
                choices, when (shownChoice) { "uid" -> uid; "path" -> if (loop) "loop" else "normal"; else -> subtype },
                onBack = { choosing = "" },
                icon = { if (shownChoice == "uid") Icons.TwoTone.Security else section.icon }) {
                when (shownChoice) { "uid" -> uid = it; "path" -> loop = it == "loop"; else -> subtype = it }
                choosing = ""
            }
        }
    } else {
        val default = stringResource(R.string.susfs_value_default)
        WearList(isLoading = busy || readingFile, onBack = onBack, onConfirm = {
            val paths = path.lineSequence().map { it.replace("\uFEFF", "").trim() }
                .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith('#') }.toList()
            val values = staticValues.map { it.toLongOrNull() }
            invalid = paths.isEmpty() || paths.any { !it.startsWith('/') } ||
                (section == WearSuSFSSection.Redirect && (!redirected.startsWith('/') || paths.size != 1)) ||
                (section == WearSuSFSSection.Kstat && type == SusKstatType.Statically &&
                    staticValues.withIndex().any { (index, value) -> value.isNotBlank() && values[index] == null })
            if (!invalid && !readingFile) onCommand(paths.map { entry -> { reply -> when (section) {
                WearSuSFSSection.Path -> SuSFSUiAction.AddSusPath(entry, loop, reply)
                WearSuSFSSection.Kstat -> SuSFSUiAction.AddSusKstat(entry, SusKstatOperation.valueOf(subtype),
                    if (type == SusKstatType.Statically) SusKstatStatically(values[0], values[1], values[2], values[3],
                        values[4], values[5], values[6], values[7], values[8], values[9], values[10], values[11]) else null, reply)
                WearSuSFSSection.Redirect -> SuSFSUiAction.AddOpenRedirect(entry, redirected, UidScheme.valueOf(uid), reply)
                else -> SuSFSUiAction.AddSusMap(entry, reply)
            } } })
        }) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.susfs_entry_manual_add)) }
            if (invalid || fileError != null || message != null) item {
                WearInfoCard(spec) { Text(if (invalid) stringResource(R.string.susfs_operation_failed) else fileError ?: message.orEmpty()) }
            }
            item { WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_entry_path_label), { field = 0 },
                icon = Icons.TwoTone.Folder,
                description = path.ifBlank { default }) }
            if (section != WearSuSFSSection.Redirect) item {
                WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_entry_import_from_file), {
                    runCatching { importLauncher.launch(arrayOf("*/*")) }.onFailure { fileError = readFailed }
                }, icon = Icons.TwoTone.FileOpen, description = stringResource(R.string.susfs_entry_import_hint))
            }
            if (section == WearSuSFSSection.Path || section == WearSuSFSSection.Kstat) item {
                WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_entry_select_subtype), {
                    choosing = if (section == WearSuSFSSection.Path) "path" else "kstat"
                }, icon = Icons.TwoTone.Tune, description = stringResource(if (section == WearSuSFSSection.Kstat) kstatLabel(type)
                    else if (loop) R.string.susfs_path_subtype_loop else R.string.susfs_path_subtype_path))
            }
            if (section == WearSuSFSSection.Redirect) {
                item { WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_redirect_redirected_path), { field = 1 },
                    icon = Icons.AutoMirrored.TwoTone.AltRoute,
                    description = redirected.ifBlank { default }) }
                item { WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_redirect_uid_scheme), { choosing = "uid" },
                    icon = Icons.TwoTone.Security,
                    description = stringResource(uidLabel(UidScheme.valueOf(uid)))) }
            }
            if (section == WearSuSFSSection.Kstat && type == SusKstatType.Statically) {
                item { WearInfoCard(spec) { Text(stringResource(R.string.susfs_kstat_statically_fields)) } }
                SegmentedColumn(KstatFields.withIndex().toList(), { it.value }) { (index, label) ->
                    WearSettingsJumpPageWidget(spec, stringResource(label), { field = index + 2 },
                        icon = Icons.TwoTone.Storage,
                        description = staticValues[index].ifBlank { default })
                }
            }
        }
    }
}
