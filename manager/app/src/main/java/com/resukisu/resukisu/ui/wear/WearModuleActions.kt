package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.DownloadStatus
import com.resukisu.resukisu.domain.model.InstalledModule
import com.resukisu.resukisu.domain.usecase.EnqueueDownloadUseCase
import com.resukisu.resukisu.domain.usecase.FetchRemoteTextUseCase
import com.resukisu.resukisu.domain.usecase.ObserveDownloadUseCase
import com.resukisu.resukisu.ui.wear.component.WearActionButton
import com.resukisu.resukisu.ui.wear.component.WearFlashProgress
import com.resukisu.resukisu.ui.wear.component.WearFlashStatus
import com.resukisu.resukisu.ui.wear.component.WearFlashStatusChip
import com.resukisu.resukisu.ui.wear.component.WearFollowLog
import com.resukisu.resukisu.ui.wear.component.WearInfoCard
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearScaledItem
import com.resukisu.resukisu.ui.wear.component.WearSectionHeader
import com.resukisu.resukisu.ui.wear.component.WearStatusItem
import com.resukisu.resukisu.ui.wear.component.WearStatusTone
import com.resukisu.resukisu.ui.wear.component.toLogLines
import com.resukisu.resukisu.ui.wear.component.wearLogLines
import com.resukisu.resukisu.ui.viewmodel.ExecuteModuleActionUiEvent
import com.resukisu.resukisu.ui.viewmodel.ExecuteModuleActionViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A module update with the phone's use cases: the changelog is read first, then the ZIP downloads
 * and [onReady] installs it.
 */
@Composable
internal fun WearModuleUpdatePage(module: InstalledModule?, onBack: () -> Unit, onReady: (String) -> Unit) {
    val fetchRemoteText = koinInject<FetchRemoteTextUseCase>()
    val enqueueDownload = koinInject<EnqueueDownloadUseCase>()
    val observeDownload = koinInject<ObserveDownloadUseCase>()
    val scope = rememberCoroutineScope()
    var changelog by rememberSaveable { mutableStateOf<String?>(null) }
    var changelogError by rememberSaveable { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    val update = module?.moduleUpdate
    LaunchedEffect(update?.changelog) {
        val url = update?.changelog ?: return@LaunchedEffect
        if (changelog == null) fetchRemoteText(url).fold(
            onSuccess = { changelog = it },
            onFailure = { changelogError = it.message.orEmpty() },
        )
    }
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.module_update)) }
        if (module != null && update != null) {
            item { WearInfoCard(spec) { Text(module.name); Text(update.version) } }
            item { WearSectionHeader(spec, stringResource(R.string.module_changelog)) }
            when {
                changelogError != null -> item {
                    WearStatusItem(spec, Icons.TwoTone.Error, stringResource(R.string.module_changelog_failed, changelogError.orEmpty()),
                        tone = WearStatusTone.ERROR)
                }
                changelog == null -> item { WearScaledItem(spec) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                else -> changelog.orEmpty().chunked(350).forEach { chunk -> item { WearInfoCard(spec) { Text(chunk) } } }
            }
            progress?.let { percent ->
                item {
                    WearInfoCard(spec) {
                        CircularProgressIndicator(progress = { percent / 100f }, modifier = Modifier.size(24.dp))
                        Text(stringResource(R.string.module_downloading, module.name))
                    }
                }
            }
            if (failed) item { WearStatusItem(spec, Icons.TwoTone.Error, stringResource(R.string.operation_failed), tone = WearStatusTone.ERROR) }
            // Like the phone, the update is offered only after its changelog has been read.
            item {
                WearActionButton(spec, Icons.TwoTone.Update, stringResource(R.string.module_update), {
                    failed = false
                    progress = 0
                    scope.launch {
                        val id = enqueueDownload(update.zipUrl, "${module.name}-${update.version}.zip")
                        val result = observeDownload(id).filterNotNull().onEach { progress = it.progress }
                            .first { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }
                        progress = null
                        result.resultUri?.let(onReady) ?: run { failed = true }
                    }
                }, progress == null && changelog != null)
            }
        }
    }
}

@Composable
internal fun WearExecuteModulePage(moduleId: String, onBack: () -> Unit, onCompleted: () -> Unit) {
    val viewModel = koinViewModel<ExecuteModuleActionViewModel>(parameters = { parametersOf(moduleId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    var successful by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is ExecuteModuleActionUiEvent.Completed) {
                successful = event.successful
                onCompleted()
            }
        }
    }
    val status = when (successful) {
        true -> WearFlashStatus.SUCCESS
        false -> WearFlashStatus.FAILED
        null -> WearFlashStatus.RUNNING
    }
    val lines = remember(state.output) { state.output.toLogLines() }
    val listState = rememberTransformingLazyColumnState()
    WearFollowLog(listState, lines.size, following = state.running)
    WearList(onBack = onBack, listState = listState) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.action)) }
        item {
            WearFlashStatusChip(spec, status, stringResource(when (status) {
                WearFlashStatus.RUNNING -> R.string.action
                WearFlashStatus.SUCCESS -> R.string.module_action_success
                WearFlashStatus.FAILED -> R.string.operation_failed
            }))
        }
        if (state.running) item { WearFlashProgress(spec) }
        wearLogLines(spec, lines)
    }
}
