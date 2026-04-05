package com.resukisu.resukisu.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.SearchAppBar
import com.resukisu.resukisu.ui.component.WarningCard
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsDropdownWidget
import com.resukisu.resukisu.ui.component.settings.splicedLazyColumnGroup
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.hazeSource
import com.resukisu.resukisu.ui.util.LocalHazeState
import com.resukisu.resukisu.ui.util.SulogEntry
import com.resukisu.resukisu.ui.util.SulogEventFilter
import com.resukisu.resukisu.ui.util.SulogEventType
import com.resukisu.resukisu.ui.util.SulogFile
import com.resukisu.resukisu.ui.util.toSulogDisplayName
import com.resukisu.resukisu.ui.viewmodel.SulogActions
import com.resukisu.resukisu.ui.viewmodel.SulogFileSelector
import com.resukisu.resukisu.ui.viewmodel.SulogScreenState
import com.resukisu.resukisu.ui.viewmodel.SulogViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SulogScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<SulogViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshLatest()
    }

    val state = SulogScreenState(
        isLoading = uiState.isLoading,
        isRefreshing = uiState.isRefreshing,
        sulogStatus = uiState.sulogStatus,
        isSulogEnabled = uiState.isSulogEnabled,
        searchText = uiState.searchText,
        selectedFilters = uiState.selectedFilters,
        files = uiState.files,
        selectedFilePath = uiState.selectedFilePath,
        entries = uiState.entries,
        visibleEntries = uiState.visibleEntries,
        errorMessage = uiState.errorMessage,
    )
    val actions = SulogActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onRefresh = viewModel::refreshLatest,
        onEnableSulog = viewModel::enableSulog,
        onCleanFile = viewModel::cleanFile,
        onSearchTextChange = viewModel::setSearchText,
        onToggleFilter = viewModel::toggleFilter,
        onSelectFile = viewModel::refresh,
    )

    SulogScreenContent(
        state,
        actions
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SulogScreenContent(
    state: SulogScreenState,
    actions: SulogActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(
            initialHeightOffset = -154f,
            initialHeightOffsetLimit = -154f // from debugger
        )
    )
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val fileSelector = buildSulogFileSelector(state.files, state.selectedFilePath)
    var selectedEntry by remember { mutableStateOf<SulogEntry?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var localSearchText by remember { mutableStateOf(state.searchText) }

    LaunchedEffect(state.searchText) {
        localSearchText = state.searchText
    }

    if (selectedEntry != null) {
        SulogDetailDialog(
            entry = selectedEntry!!,
            onDismiss = { selectedEntry = null },
        )
    }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = stringResource(R.string.settings_sulog),
                searchText = localSearchText,
                onSearchTextChange = {
                    actions.onSearchTextChange(it)
                    scope.launch { searchListState.scrollToItem(0) }
                },
                onBackClick = actions.onBack,
                dropdownContent = {
                    IconButton(onClick = actions.onCleanFile) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.sulog_clean_title),
                        )
                    }
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.sulog_filter_title),
                        )
                    }
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false },
                    ) {
                        SulogEventFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(sulogFilterLabel(filter)) },
                                trailingIcon = {
                                    Checkbox(
                                        checked = filter in state.selectedFilters,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    actions.onToggleFilter(filter)
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.sulog_search_placeholder)
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { innerPadding ->
        PullToRefreshBox(
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(),
            isRefreshing = state.isRefreshing,
            onRefresh = {
                actions.onRefresh()
            },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .align(Alignment.TopCenter),
                    state = pullToRefreshState,
                    isRefreshing = state.isRefreshing,
                )
            }
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                ) {
                    item {
                        Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
                    }

                    item {
                        SulogStatusSection(state, actions)
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .padding(horizontal = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceBright.copy(
                                            alpha = CardConfig.cardAlpha
                                        )
                                    )
                            ) {
                                SettingsDropdownWidget(
                                    iconPlaceholder = false,
                                    title = stringResource(R.string.sulog_log_files),
                                    items = fileSelector.items,
                                    enabled = fileSelector.items.isNotEmpty(),
                                    selectedIndex = fileSelector.selectedIndex,
                                    onSelectedIndexChange = { index ->
                                        state.files.getOrNull(index)?.let { file ->
                                            actions.onSelectFile(file.path)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    sulogEntriesSection(
                        entries = state.visibleEntries,
                        errorMessage = state.errorMessage,
                        onEntryClick = { selectedEntry = it },
                    )

                    item {
                        Spacer(
                            Modifier.height(
                                WindowInsets.navigationBars.asPaddingValues()
                                    .calculateBottomPadding() +
                                        WindowInsets.captionBar.asPaddingValues()
                                            .calculateBottomPadding() +
                                        16.dp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Preview(locale = "zh-rCN", name = "Sulog zh-rCN translation")
@Composable
fun SulogScreenTranslationPreview() {
    CompositionLocalProvider(
        LocalHazeState provides null
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            SulogScreenContent(
                state = SulogScreenState(
                    isLoading = false,
                    files = listOf(
                        SulogFile("test.log", "test.log"),
                        SulogFile("test2.log", "test2.log"),
                        SulogFile("test3.log", "test3.log"),
                    ),
                    isSulogEnabled = false,
                    visibleEntries = listOf(
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.RootExecve,
                            rawLine = "test",
                            timestampText = "test",
                            fields = emptyMap()
                        ),
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.RootExecve,
                            rawLine = "test",
                            timestampText = "test",
                            fields = mapOf(
                                "comm" to "comm",
                                "pid" to "pid",
                                "uid" to "uid",
                            )
                        ),
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.DaemonEvent,
                            rawLine = "test",
                            timestampText = "test",
                            fields = emptyMap()
                        ),
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.IoctlGrantRoot,
                            rawLine = "test",
                            timestampText = "test",
                            fields = emptyMap()
                        ),
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.SuCompat,
                            rawLine = "test",
                            timestampText = "test",
                            fields = emptyMap()
                        ),
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.Dropped,
                            rawLine = "test",
                            timestampText = "test",
                            fields = mapOf(
                                "dropped" to "1"
                            )
                        ),
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.Unknown,
                            rawLine = "test",
                            timestampText = "test",
                            fields = emptyMap()
                        ),
                    )
                ),
                actions = SulogActions(
                    onBack = {},
                    onRefresh = {},
                    onEnableSulog = {},
                    onCleanFile = {},
                    onSearchTextChange = {},
                    onToggleFilter = {},
                    onSelectFile = {}
                )
            )
        }
    }
}

@Preview(name = "Sulog Screen")
@Composable
fun SulogScreenPreview() {
    CompositionLocalProvider(
        LocalHazeState provides null
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            SulogScreenContent(
                state = SulogScreenState(
                    isLoading = false,
                    files = listOf(
                        SulogFile("test.log", "test.log"),
                        SulogFile("test2.log", "test2.log"),
                        SulogFile("test3.log", "test3.log"),
                    ),
                    isSulogEnabled = false,
                    visibleEntries = listOf(
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.RootExecve,
                            rawLine = "test",
                            timestampText = "test",
                            fields = emptyMap()
                        ),
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.RootExecve,
                            rawLine = "test",
                            timestampText = "test",
                            fields = mapOf(
                                "comm" to "comm",
                                "pid" to "pid",
                                "uid" to "uid",
                            )
                        ),
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.DaemonEvent,
                            rawLine = "test",
                            timestampText = "test",
                            fields = emptyMap()
                        ),
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.IoctlGrantRoot,
                            rawLine = "test",
                            timestampText = "test",
                            fields = emptyMap()
                        ),
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.SuCompat,
                            rawLine = "test",
                            timestampText = "test",
                            fields = emptyMap()
                        ),
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.Dropped,
                            rawLine = "test",
                            timestampText = "test",
                            fields = mapOf(
                                "dropped" to "1"
                            )
                        ),
                        SulogEntry(
                            key = "key",
                            eventType = SulogEventType.Unknown,
                            rawLine = "test",
                            timestampText = "test",
                            fields = emptyMap()
                        ),
                    )
                ),
                actions = SulogActions(
                    onBack = {},
                    onRefresh = {},
                    onEnableSulog = {},
                    onCleanFile = {},
                    onSearchTextChange = {},
                    onToggleFilter = {},
                    onSelectFile = {}
                )
            )
        }
    }
}

@Preview(name = "Sulog Warning Cards")
@Composable
fun SulogWarningCardPreview() {
    val actions = SulogActions(
        onBack = {},
        onRefresh = {},
        onEnableSulog = {},
        onCleanFile = {},
        onSearchTextChange = {},
        onToggleFilter = {},
        onSelectFile = {}
    )

    Column {
        SulogStatusSection(
            state = SulogScreenState(
                sulogStatus = "unsupported"
            ),
            actions = actions
        )
        SulogStatusSection(
            state = SulogScreenState(
                sulogStatus = "managed"
            ),
            actions = actions
        )
        SulogStatusSection(
            state = SulogScreenState(
                sulogStatus = "supported",
                isSulogEnabled = false
            ),
            actions = actions
        )
    }
}

@Preview(name = "Sulog Warning Cards with Translations", locale = "zh-rCN")
@Composable
fun SulogWarningCardTranslationPreview() {
    val actions = SulogActions(
        onBack = {},
        onRefresh = {},
        onEnableSulog = {},
        onCleanFile = {},
        onSearchTextChange = {},
        onToggleFilter = {},
        onSelectFile = {}
    )

    Column {
        SulogStatusSection(
            state = SulogScreenState(
                sulogStatus = "unsupported"
            ),
            actions = actions
        )
        SulogStatusSection(
            state = SulogScreenState(
                sulogStatus = "managed"
            ),
            actions = actions
        )
        SulogStatusSection(
            state = SulogScreenState(
                sulogStatus = "supported",
                isSulogEnabled = false
            ),
            actions = actions
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.sulogEntriesSection(
    entries: List<SulogEntry>,
    errorMessage: String?,
    onEntryClick: (SulogEntry) -> Unit,
) {
    when {
        errorMessage != null -> item {
            SulogMessageCard(
                modifier = Modifier.fillParentMaxSize(),
                title = stringResource(R.string.sulog_failed_to_load),
                summary = errorMessage,
            )
        }

        else -> {
            splicedLazyColumnGroup(
                entries,
                key = { index, entry -> "$index-${entry.key}" }) { index, entry ->
                SettingsBaseWidget(
                    modifier = if (index < entries.lastIndex) {
                        Modifier.padding(bottom = 2.dp)
                    } else {
                        Modifier
                    },
                    onClick = { onEntryClick(entry) },
                    title = sulogEntryTitle(entry),
                    iconPlaceholder = false,
                    descriptionColumnContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            sulogEntryDescription(entry)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            entry.timestampText?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMediumEmphasized,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                val colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiary,
                                )
                                sulogEntrySummaryTags(entry).forEachIndexed { index, tag ->
                                    LabelText(
                                        label = tag,
                                        containerColor = colors.getOrElse(index) { colors.last() }
                                    )
                                }
                            }
                        }
                    },
                ) {
                    sulogEntryStatus(entry)?.let { Text(it) }
                }
            }
        }
    }
}

@Composable
private fun SulogStatusSection(
    state: SulogScreenState,
    actions: SulogActions,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        when (state.sulogStatus) {
            "unsupported" -> {
                WarningCard(message = stringResource(R.string.sulog_unsupported_title))
                Spacer(modifier = Modifier.height(16.dp))
            }

            "managed" -> {
                WarningCard(message = stringResource(R.string.feature_status_managed_summary))
                Spacer(modifier = Modifier.height(16.dp))
            }

            "supported" if !state.isSulogEnabled -> {
                WarningCard(
                    message = stringResource(R.string.sulog_disabled_title),
                    content = {
                        Button(
                            onClick = actions.onEnableSulog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text(stringResource(R.string.sulog_enable_action))
                        }
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            else -> Unit
        }
    }
}

@Composable
private fun SulogMessageCard(
    modifier: Modifier,
    title: String,
    summary: String? = null,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = MaterialTheme.colorScheme.outline)
            if (summary != null) {
                Text(
                    summary,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SulogDetailDialog(
    entry: SulogEntry,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(sulogEntryTitle(entry)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(
                        text = sulogEntryDetailText(entry),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
fun sulogFilterLabel(filter: SulogEventFilter): String {
    return when (filter) {
        SulogEventFilter.RootExecve -> stringResource(R.string.sulog_filter_root_execve)
        SulogEventFilter.SuCompat -> stringResource(R.string.sulog_filter_sucompat)
        SulogEventFilter.IoctlGrantRoot -> stringResource(R.string.sulog_filter_ioctl_grant_root)
        SulogEventFilter.DaemonEvent -> stringResource(R.string.sulog_filter_daemon_restart)
    }
}

@Composable
fun sulogEntryTitle(entry: SulogEntry): String {
    return when (entry.eventType) {
        SulogEventType.RootExecve -> entry.fields["comm"]
            ?: stringResource(R.string.sulog_filter_root_execve)

        SulogEventType.SuCompat -> stringResource(R.string.sulog_filter_sucompat)
        SulogEventType.IoctlGrantRoot -> stringResource(R.string.sulog_filter_ioctl_grant_root)
        SulogEventType.DaemonEvent -> stringResource(R.string.sulog_filter_daemon_restart)
        SulogEventType.Dropped -> stringResource(R.string.sulog_event_dropped)
        SulogEventType.Unknown -> entry.fields["type"]?.replace('_', ' ')
            ?.replaceFirstChar(Char::uppercase)
            ?: stringResource(R.string.sulog_entry_unknown_event)
    }
}

@Composable
fun sulogEntryDescription(entry: SulogEntry): String? {
    return when (entry.eventType) {
        SulogEventType.DaemonEvent -> entry.fields["boot_id"]?.let { "Boot ID: $it" }
        SulogEventType.Dropped -> entry.fields["ts_ns"]?.let { "Timestamp: $it" }
        else -> entry.fields["argv"] ?: entry.fields["file"]
    }
}

@Composable
fun sulogEntrySummaryTags(entry: SulogEntry): List<String> {
    val comm = entry.fields["comm"]
    val pid = entry.fields["pid"]
    val uid = entry.fields["uid"]
    return when (entry.eventType) {
        SulogEventType.DaemonEvent -> listOfNotNull(entry.fields["restart"]?.let {
            stringResource(
                R.string.sulog_daemon_restart_count,
                it
            )
        } ?: stringResource(R.string.sulog_filter_daemon_restart))

        SulogEventType.Dropped -> listOfNotNull(entry.fields["dropped"]?.let {
            stringResource(
                R.string.sulog_drop_count,
                it
            )
        })

        else -> listOfNotNull(
            comm?.takeIf { it.isNotBlank() },
            uid?.let { "UID $it" },
            pid?.let { "PID $it" })
    }
}

fun sulogEntryDetailText(entry: SulogEntry) = buildAnnotatedString {
    entry.fields.entries.forEachIndexed { index, (key, value) ->
        if (index > 0) append('\n')
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append("$key: ")
        }
        append(value)
    }
}

fun sulogEntryStatus(entry: SulogEntry): String? {
    return entry.fields["retval"]?.toIntOrNull()?.let(::formatSulogStatus)
}

private fun formatSulogStatus(retval: Int): String {
    return if (retval == 0) "Success" else "Exit $retval"
}

fun buildSulogFileSelector(
    files: List<SulogFile>,
    selectedFilePath: String?,
): SulogFileSelector {
    if (files.isEmpty()) {
        return SulogFileSelector(
            items = emptyList(),
            selectedIndex = -1,
        )
    }

    val selectedIndex = files.indexOfFirst { it.path == selectedFilePath }
        .takeIf { it >= 0 }
        ?: 0

    return SulogFileSelector(
        items = files.map { it.name.toSulogDisplayName() },
        selectedIndex = selectedIndex,
    )
}