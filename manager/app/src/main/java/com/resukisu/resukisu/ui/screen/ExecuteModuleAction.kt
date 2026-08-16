package com.resukisu.resukisu.ui.screen

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.KeyEventBlocker
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.showReplacingSnackbar
import com.resukisu.resukisu.ui.viewmodel.ExecuteModuleActionUiAction
import com.resukisu.resukisu.ui.viewmodel.ExecuteModuleActionUiEvent
import com.resukisu.resukisu.ui.viewmodel.ExecuteModuleActionViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ExecuteModuleActionScreen(moduleId: String) {
    val viewModel = koinViewModel<ExecuteModuleActionViewModel>(
        parameters = { parametersOf(moduleId) },
    )
    val moduleActionState by viewModel.state.collectAsStateWithLifecycle()
    val snackBarHost = LocalSnackbarHost.current
    val state = rememberLazyListState()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    BackHandler(enabled = moduleActionState.running) {
        // Disable back button if action is running
    }

    val fromShortcut = remember(activity) {
        val intent = activity?.intent
        intent?.getStringExtra("shortcut_type") == "module_action"
    }

    LaunchedEffect(viewModel, fromShortcut) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ExecuteModuleActionUiEvent.Completed -> {
                    if (event.successful && fromShortcut) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.module_action_success),
                            Toast.LENGTH_SHORT,
                        ).show()
                        activity?.finishAndRemoveTask()
                    }
                }

                is ExecuteModuleActionUiEvent.LogSaved -> {
                    snackBarHost.showReplacingSnackbar("Log saved to ${event.path}")
                }

                is ExecuteModuleActionUiEvent.Error -> {
                    snackBarHost.showReplacingSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                isActionRunning = moduleActionState.running,
                onBack = {
                    navigator.pop()
                },
                onSave = {
                    viewModel.dispatch(ExecuteModuleActionUiAction.SaveLog)
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!moduleActionState.running) {
                val navigator = LocalNavigator.current
                ExtendedFloatingActionButton(
                    text = { Text(text = stringResource(R.string.close)) },
                    icon = { Icon(Icons.TwoTone.Close, contentDescription = null) },
                    onClick = {
                        navigator.pop()
                    }
                )
            }
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) }
    ) { innerPadding ->
        KeyEventBlocker {
            it.key == Key.VolumeDown || it.key == Key.VolumeUp
        }
        LaunchedEffect(moduleActionState.output) {
            state.animateScrollToItem(2) // Spacer(bottom)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(1f)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource(),
        ) {
            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            }
            item {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = moduleActionState.output,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                )
            }
            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    isActionRunning: Boolean,
    onBack: () -> Unit = {},
    onSave: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(
        ),
        title = { Text(stringResource(R.string.action)) },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            AppBackButton(
                onClick = onBack
            )
        },
        actions = {
            IconButton(
                onClick = onSave,
                enabled = !isActionRunning
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Save,
                    contentDescription = stringResource(id = R.string.save_log),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =
                if (themeConfig.isEnableBlur)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
            scrolledContainerColor =
                if (themeConfig.isEnableBlur)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
        ),
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp))
    )
}
