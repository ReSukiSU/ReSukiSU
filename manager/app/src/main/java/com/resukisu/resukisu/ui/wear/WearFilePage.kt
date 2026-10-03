package com.resukisu.resukisu.ui.wear

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DriveFileRenameOutline
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.FilePresent
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.FolderOff
import androidx.compose.material.icons.twotone.FolderZip
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.SubdirectoryArrowLeft
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.file.matchesMimeType
import com.resukisu.resukisu.data.file.wearFileMimeType
import com.resukisu.resukisu.ui.wear.component.WearActionButton
import com.resukisu.resukisu.ui.wear.component.WearChip
import com.resukisu.resukisu.ui.wear.component.WearChipEmphasis
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearScaledItem
import com.resukisu.resukisu.ui.wear.component.WearStatusItem
import com.resukisu.resukisu.ui.wear.component.WearStatusTone
import com.resukisu.resukisu.ui.wear.component.WearSubPage
import com.resukisu.resukisu.ui.wear.component.WearTextInputPage
import com.resukisu.resukisu.ui.wear.component.wearGroupGap
import com.resukisu.resukisu.ui.viewmodel.WearFileEvent
import com.resukisu.resukisu.ui.viewmodel.WearFileViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * The built-in picker as a Wear list: the title, the current path as a caption, a low-emphasis
 * entry to the parent folder, then folders and the files matching [mimeTypes] as tonal buttons. In
 * save mode the file name follows and the edge button confirms; otherwise picking a file returns it.
 * [onPicked] receives the file as a content URI.
 */
@Composable
internal fun WearFilePage(mimeTypes: List<String>, saving: Boolean, initialName: String, onBack: () -> Unit,
    onPicked: (Uri) -> Unit) {
    val viewModel = koinViewModel<WearFileViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        if (viewModel.state.value.directory == null) {
            viewModel.setName(initialName)
            viewModel.load(null, mimeTypes, saving)
        }
    }
    LaunchedEffect(viewModel) { viewModel.events.collect { event ->
        if (event is WearFileEvent.Picked) onPicked(event.uri.toUri())
    } }
    var editingName by rememberSaveable { mutableStateOf(false) }
    if (editingName) {
        WearSubPage({ editingName = false }) {
            WearTextInputPage(stringResource(R.string.wear_file_name), state.name) {
                viewModel.setName(it); editingName = false
            }
        }
    } else {
        val error = state.error ?: if (state.failed) stringResource(R.string.operation_failed) else null
        val directory = state.directory
        WearList(isLoading = state.loading || directory == null && !state.failed, onBack = onBack, snap = true,
            onConfirm = if (saving && directory?.writable == true) ({ viewModel.create() }) else null,
        ) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.wear_picker_mode)) }
            directory?.let { item {
                WearScaledItem(spec) {
                    Text(it.path, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyExtraSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.StartEllipsis)
                }
            } }
            error?.let { item { WearStatusItem(spec, Icons.TwoTone.Error, it, tone = WearStatusTone.ERROR) } }
            directory?.parent?.let { parent -> item {
                WearChip(spec, stringResource(R.string.wear_parent_directory), icon = Icons.TwoTone.SubdirectoryArrowLeft, emphasis = WearChipEmphasis.HIGH,
                    onClick = { viewModel.load(parent, mimeTypes, saving) })
            } }
            if (directory != null && directory.entries.isEmpty()) item {
                WearStatusItem(spec, Icons.TwoTone.FolderOff, stringResource(R.string.wear_directory_empty))
            }
            items(directory?.entries.orEmpty(), key = { it.path }) { entry ->
                val mimeType = wearFileMimeType(entry.name)
                WearActionButton(spec,
                    icon = when {
                        entry.directory -> Icons.TwoTone.Folder
                        matchesMimeType(mimeType, listOf("image/*")) -> Icons.TwoTone.Image
                        mimeType == "application/zip" -> Icons.TwoTone.FolderZip
                        else -> Icons.TwoTone.FilePresent
                    },
                    label = entry.name,
                    onClick = { if (entry.directory) viewModel.load(entry.path, mimeTypes, saving) else viewModel.select(entry.path) },
                    secondaryText = if (entry.directory) null else Formatter.formatShortFileSize(context, entry.size),
                    colors = ButtonDefaults.filledTonalButtonColors())
            }
            if (saving) {
                wearGroupGap("save-gap")
                if (directory?.writable == false) item {
                    WearStatusItem(spec, Icons.TwoTone.Error, stringResource(R.string.wear_directory_unavailable), tone = WearStatusTone.ERROR)
                }
                item {
                    WearActionButton(spec, Icons.TwoTone.DriveFileRenameOutline, state.name, { editingName = true },
                        secondaryText = stringResource(R.string.wear_file_name), colors = ButtonDefaults.filledTonalButtonColors())
                }
            }
        }
    }
}
