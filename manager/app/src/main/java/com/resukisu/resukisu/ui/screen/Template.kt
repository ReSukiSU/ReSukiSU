package com.resukisu.resukisu.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.TemplateEditorScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultRecipient
import com.ramcosta.composedestinations.result.getOr
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.splicedLazyColumnGroup
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.viewmodel.TemplateViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * @author weishu
 * @date 2023/10/20.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun AppProfileTemplateScreen(
    navigator: DestinationsNavigator,
    resultRecipient: ResultRecipient<TemplateEditorScreenDestination, Boolean>
) {
    val pullRefreshState = rememberPullToRefreshState()
    val viewModel = viewModel<TemplateViewModel>()
    val scope = rememberCoroutineScope()
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val hazeState = if (CardConfig.isCustomBackgroundEnabled) rememberHazeState() else null
    LaunchedEffect(Unit) {
        if (viewModel.templateList.isEmpty()) {
            viewModel.fetchTemplates()
        }
    }

    // handle result from TemplateEditorScreen, refresh if needed
    resultRecipient.onNavResult { result ->
        if (result.getOr { false }) {
            scope.launch { viewModel.fetchTemplates() }
        }
    }

    Scaffold(
        topBar = {
            val context = LocalContext.current
            val clipboardManager = context.getSystemService<ClipboardManager>()
            val showToast = fun(msg: String) {
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
            TopBar(
                onBack = dropUnlessResumed { navigator.popBackStack() },
                onSync = {
                    scope.launch { viewModel.fetchTemplates(true) }
                },
                onImport = {
                    scope.launch {
                        val clipboardText = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
                        if (clipboardText.isNullOrEmpty()) {
                            showToast(context.getString(R.string.app_profile_template_import_empty))
                            return@launch
                        }
                        viewModel.importTemplates(
                            clipboardText,
                            {
                                showToast(context.getString(R.string.app_profile_template_import_success))
                                viewModel.fetchTemplates(false)
                            },
                            showToast
                        )
                    }
                },
                onExport = {
                    scope.launch {
                        viewModel.exportTemplates(
                            {
                                showToast(context.getString(R.string.app_profile_template_export_empty))
                            }
                        ) { text ->
                            clipboardManager?.setPrimaryClip(ClipData.newPlainText("", text))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    navigator.navigate(
                        TemplateEditorScreenDestination(
                            TemplateViewModel.TemplateInfo(),
                            false
                        )
                    )
                },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(id = R.string.app_profile_template_create)) },
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        PullToRefreshBox(
            state = pullRefreshState,
            modifier = (if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier).nestedScroll(
                scrollBehavior.nestedScrollConnection
            ),
            isRefreshing = viewModel.isRefreshing,
            onRefresh = {
                scope.launch { viewModel.fetchTemplates() }
            },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = viewModel.isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding()),
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = remember {
                    PaddingValues(bottom = 16.dp + 56.dp + 16.dp /* Scaffold Fab Spacing + Fab container height */)
                }
            ) {
                item {
                    Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
                }

                splicedLazyColumnGroup(
                    items = viewModel.templateList,
                    key = { _, app -> app.id }) { _, app ->
                    TemplateItem(navigator, app)
                }

                item {
                    Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateItem(
    navigator: DestinationsNavigator,
    template: TemplateViewModel.TemplateInfo
) {
    SettingsJumpPageWidget(
        title = template.name,
        iconPlaceholder = false,
        onClick = {
            navigator.navigate(TemplateEditorScreenDestination(template, !template.local))
        },
        descriptionColumnContent = {
            Text(
                text = "${template.id}${if (template.author.isEmpty()) "" else "@${template.author}"}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
            )
            Text(template.description)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
            ) {
                LabelText("UID: ${template.uid}")
                LabelText(
                    label = "GID: ${template.gid}",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                LabelText(
                    label = template.context,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (template.local) {
                    LabelText(
                        label = stringResource(R.string.local),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    LabelText(
                        label = stringResource(R.string.remote),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    onBack: () -> Unit,
    onSync: () -> Unit = {},
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    hazeState: HazeState?
) {
    MaterialTheme.colorScheme

    val hazeStyle = if (ThemeConfig.backgroundImageLoaded) HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = 0.8f
        ),
        tint = HazeTint(Color.Transparent)
    ) else null

    val collapsedFraction = scrollBehavior?.state?.collapsedFraction ?: 0f
    val modifier =
        if (ThemeConfig.backgroundImageLoaded && hazeStyle != null && hazeState != null) {
            Modifier.hazeEffect(hazeState) {
                style = hazeStyle
                noiseFactor = 0f
                blurRadius = 30.dp
                alpha = collapsedFraction
            }
        } else Modifier

    LargeFlexibleTopAppBar(
        modifier = modifier,
        title = {
            Text(stringResource(R.string.settings_profile_template))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =
                if (ThemeConfig.backgroundImageLoaded) Color.Transparent
                else MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor =
                if (ThemeConfig.backgroundImageLoaded) Color.Transparent
                else MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationIcon = {
            AppBackButton(
                onClick = onBack
            )
        },
        actions = {
            IconButton(onClick = onSync) {
                Icon(
                    Icons.Filled.Sync,
                    contentDescription = stringResource(id = R.string.app_profile_template_sync)
                )
            }

            var showDropdown by remember { mutableStateOf(false) }
            IconButton(onClick = {
                showDropdown = true
            }) {
                Icon(
                    imageVector = Icons.Filled.ImportExport,
                    contentDescription = stringResource(id = R.string.app_profile_import_export)
                )

                DropdownMenu(expanded = showDropdown, onDismissRequest = {
                    showDropdown = false
                }) {
                    DropdownMenuItem(text = {
                        Text(stringResource(id = R.string.app_profile_import_from_clipboard))
                    }, onClick = {
                        onImport()
                        showDropdown = false
                    })
                    DropdownMenuItem(text = {
                        Text(stringResource(id = R.string.app_profile_export_to_clipboard))
                    }, onClick = {
                        onExport()
                        showDropdown = false
                    })
                }
            }
        },
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun LabelText(
    label: String,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
