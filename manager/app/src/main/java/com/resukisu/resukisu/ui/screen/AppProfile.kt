package com.resukisu.resukisu.ui.screen

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AccountCircle
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.AppControlAction
import com.resukisu.resukisu.domain.model.AppProfile
import com.resukisu.resukisu.domain.model.InstalledApp
import com.resukisu.resukisu.domain.model.InstalledAppGroup
import com.resukisu.resukisu.ui.component.PackageIcon
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.profile.AppProfileConfig
import com.resukisu.resukisu.ui.component.profile.RootProfileConfig
import com.resukisu.resukisu.ui.component.profile.TemplateConfig
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsDropdownWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.component.settings.lazySegmentColumn
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.theme.renderBackgroundBlur
import com.resukisu.resukisu.ui.util.ActivityResumeEffect
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.showReplacingSnackbar
import com.resukisu.resukisu.ui.viewmodel.AppProfileUiAction
import com.resukisu.resukisu.ui.viewmodel.AppProfileUiEvent
import com.resukisu.resukisu.ui.viewmodel.AppProfileViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @author weishu
 * @date 2023/5/16.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppProfileScreen(
    uid: Int,
    packageName: String,
) {
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val viewModel =
        koinViewModel<AppProfileViewModel>(parameters = { parametersOf(uid, packageName) })
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val appGroup = uiState.appGroup
    val appLabel = appGroup?.mainApp?.label ?: packageName
    val failToUpdateAppProfile = stringResource(R.string.failed_to_update_app_profile).format(
        appLabel
    )
    val failToUpdateSepolicy =
        stringResource(R.string.failed_to_update_sepolicy).format(appLabel)
    val suNotAllowed = stringResource(R.string.su_not_allowed).format(appLabel)

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AppProfileUiEvent.Error -> snackBarHost.showReplacingSnackbar(
                    failToUpdateAppProfile
                )

                AppProfileUiEvent.SepolicyUpdateFailed ->
                    snackBarHost.showReplacingSnackbar(failToUpdateSepolicy)

                AppProfileUiEvent.Saved -> Unit
            }
        }
    }

    ActivityResumeEffect(packageName, uid) {
        viewModel.dispatch(AppProfileUiAction.Load)
    }

    val profile = uiState.profile
    if (appGroup == null || profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    val cardColor = if (cardConfig.isCustomBackgroundEnabled) {
        Color.Transparent
    } else {
        colorScheme.surfaceContainer
    }

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }
    
    Scaffold(
        topBar = {
            TopBar(
                title = appGroup.mainApp.label,
                packageName = packageName,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cardColor,
                    scrolledContainerColor = cardColor
                ),
                onBack = dropUnlessResumed { navigator.pop() },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        AppProfileInner(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource(),
            topPadding = paddingValues.calculateTopPadding(),
            appGroup = appGroup,
            appIcon = {
                PackageIcon(
                    packageName = appGroup.mainApp.packageName,
                    contentDescription = appGroup.mainApp.label,
                    modifier = Modifier
                        .padding(4.dp)
                        .width(48.dp)
                        .height(48.dp),
                )
            },
            profile = profile,
            defaultUmountModules = uiState.defaultUmountModules,
            sepolicyValid = uiState.sepolicyValid,
            onValidateSepolicy = {
                viewModel.dispatch(AppProfileUiAction.ValidateSepolicy(it))
            },
            onViewTemplate = {
                navigator.push(Route.TemplateEditor(it, true))
            },
            onManageTemplate = {
                navigator.push(Route.AppProfileTemplate)
            },
            onProfileChange = {
                scope.launch {
                    if (it.allowSu) {
                        // sync with allowlist.c - forbid_system_uid
                        if (uid < 2000 && uid != 1000) {
                            snackBarHost.showReplacingSnackbar(suNotAllowed)
                            return@launch
                        }
                    }
                    viewModel.dispatch(AppProfileUiAction.Save(it))
                }
            },
            onControlApp = { viewModel.dispatch(AppProfileUiAction.ControlApp(it)) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppProfileInner(
    modifier: Modifier = Modifier,
    topPadding: Dp,
    appGroup: InstalledAppGroup,
    appIcon: @Composable () -> Unit,
    profile: AppProfile,
    defaultUmountModules: Boolean = profile.umountModules,
    sepolicyValid: Boolean = true,
    onValidateSepolicy: (String) -> Unit = {},
    onViewTemplate: (id: String) -> Unit = {},
    onManageTemplate: () -> Unit = {},
    onControlApp: (AppControlAction) -> Unit,
    onProfileChange: (AppProfile) -> Unit,
) {
    val cardConfig: CardConfig = koinInject()
    val themeConfig: ThemeConfig = koinInject()
    val isRootGranted = profile.allowSu
    val affectedApplicationsTitle = stringResource(R.string.affected_applications)

    LazyColumn(modifier = modifier) {
        item {
            Spacer(modifier = Modifier.height(topPadding))
        }

        item {
            SettingsDropdownWidget(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                title = appGroup.mainApp.label,
                description = appGroup.mainApp.packageName,
                iconPlaceholder = false,
                leadingContent = {
                    appIcon()
                },
                choice = -1,
                data = listOf(
                    stringResource(id = R.string.launch_app),
                    stringResource(id = R.string.force_stop_app),
                    stringResource(id = R.string.restart_app)
                )
            ) { choice ->
                when (choice) {
                    0 -> onControlApp(AppControlAction.LAUNCH)
                    1 -> onControlApp(AppControlAction.FORCE_STOP)
                    2 -> onControlApp(AppControlAction.RESTART)
                    else -> throw IllegalStateException("Illegal choice: $choice")
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceBright.copy(
                    alpha = cardConfig.cardAlpha
                ),
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            {
                SettingsSwitchWidget(
                    icon = Icons.TwoTone.Security,
                    title = stringResource(id = R.string.superuser),
                    checked = isRootGranted,
                    onCheckedChange = { onProfileChange(profile.copy(allowSu = it)) },
                )
            }
        }

        item {
            Crossfade(
                targetState = isRootGranted,
                label = "RootAccess"
            )
            { current ->
                Column {
                    if (current) {
                        val initialMode = if (profile.rootUseDefault) {
                            Mode.Default
                        } else if (profile.rootTemplate != null) {
                            Mode.Template
                        } else {
                            Mode.Custom
                        }
                        var mode by rememberSaveable {
                            mutableStateOf(initialMode)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .renderBackgroundBlur(MaterialTheme.colorScheme.surfaceBright),
                            shape = RoundedCornerShape(16.dp),
                            color = if (themeConfig.isEnableBlurExp) Color.Transparent else {
                                MaterialTheme.colorScheme.surfaceBright.copy(
                                    alpha = cardConfig.cardAlpha
                                )
                            },
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            ProfileBox(mode, true) {
                                // template mode shouldn't change profile here!
                                if (it == Mode.Default || it == Mode.Custom) {
                                    onProfileChange(
                                        profile.copy(
                                            rootUseDefault = it == Mode.Default,
                                            rootTemplate = null
                                        )
                                    )
                                }
                                mode = it
                            }
                        }

                        AnimatedVisibility(
                            visible = mode != Mode.Default,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Crossfade(
                                targetState = mode,
                                label = "ProfileMode"
                            ) { currentMode ->
                                when (currentMode) {
                                    Mode.Template -> {
                                        SegmentedColumn {
                                            item {
                                                TemplateConfig(
                                                    profile = profile,
                                                    onViewTemplate = onViewTemplate,
                                                    onProfileChange = onProfileChange
                                                )
                                            }

                                            item {
                                                SettingsJumpPageWidget(
                                                    icon = Icons.TwoTone.Edit,
                                                    title = stringResource(R.string.manage_app_profile),
                                                    description = stringResource(R.string.settings_profile_template_summary),
                                                    onClick = {
                                                        onManageTemplate()
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Mode.Custom -> {
                                        RootProfileConfig(
                                            profile = profile,
                                            sepolicyValid = sepolicyValid,
                                            onValidateSepolicy = onValidateSepolicy,
                                            onProfileChange = onProfileChange
                                        )
                                    }

                                    else -> {}
                                }
                            }
                        }
                    } else {
                        val mode = if (profile.nonRootUseDefault) Mode.Default else Mode.Custom

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .renderBackgroundBlur(MaterialTheme.colorScheme.surfaceBright),
                            shape = RoundedCornerShape(16.dp),
                            color = if (themeConfig.isEnableBlurExp) Color.Transparent else {
                                MaterialTheme.colorScheme.surfaceBright.copy(
                                    alpha = cardConfig.cardAlpha
                                )
                            },
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            ProfileBox(mode, false) {
                                onProfileChange(profile.copy(nonRootUseDefault = (it == Mode.Default)))
                            }
                        }

                        AnimatedVisibility(
                            visible = mode == Mode.Custom,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceBright.copy(
                                    alpha = cardConfig.cardAlpha
                                ),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ) {
                                AppProfileConfig(
                                    enabled = mode == Mode.Custom,
                                    profile = profile,
                                    defaultUmountModules = defaultUmountModules,
                                    onProfileChange = onProfileChange
                                )
                            }
                        }
                    }
                }
            }
        }

        if (appGroup.apps.size > 1) {
            lazySegmentColumn(
                items = appGroup.apps,
                title = affectedApplicationsTitle,
                key = { _, app -> app.packageName },
            ) { _, app ->
                SettingsBaseWidget(
                    title = app.label,
                    description = app.packageName,
                    enabled = false,
                    iconPlaceholder = false,
                    leadingContent = {
                        PackageIcon(
                            packageName = app.packageName,
                            contentDescription = app.label,
                            modifier = Modifier
                                .padding(4.dp)
                                .width(48.dp)
                                .height(48.dp),
                        )
                    },
                ) {}
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp + 48.dp + 6.dp /* SnackBar height */))
        }
    }
}

private enum class Mode(@param:StringRes private val res: Int) {
    Default(R.string.profile_default), Template(R.string.profile_template), Custom(R.string.profile_custom);

    val text: String
        @Composable get() = stringResource(res)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    title: String,
    packageName: String,
    onBack: () -> Unit,
    colors: TopAppBarColors,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(
        ),
        title = {
            Text(
                text = title,
            )
        },
        subtitle = {
            Text(
                text = packageName
            )
        },
        colors = colors,
        navigationIcon = {
            AppBackButton(
                onClick = onBack
            )
        },
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun ProfileBox(
    mode: Mode,
    hasTemplate: Boolean,
    onModeChange: (Mode) -> Unit,
) {
    Column {
        SettingsBaseWidget(
            icon = Icons.TwoTone.AccountCircle,
            title = stringResource(R.string.profile),
            description = mode.text,
            isOnBackground = false,
            containerColor = Color.Transparent,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        )
        {
            FilterChip(
                selected = mode == Mode.Default,
                onClick = { onModeChange(Mode.Default) },
                label = {
                    Text(
                        text = stringResource(R.string.profile_default),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                shape = MaterialTheme.shapes.medium,
            )

            if (hasTemplate) {
                FilterChip(
                    selected = mode == Mode.Template,
                    onClick = { onModeChange(Mode.Template) },
                    label = {
                        Text(
                            text = stringResource(R.string.profile_template),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                )
            }

            FilterChip(
                selected = mode == Mode.Custom,
                onClick = { onModeChange(Mode.Custom) },
                label = {
                    Text(
                        text = stringResource(R.string.profile_custom),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}

@Preview
@Composable
private fun AppProfilePreview() {
    val cardConfig: CardConfig = koinInject()
    var profile by remember { mutableStateOf(AppProfile("")) }

    Surface(
        color = if (cardConfig.isCustomBackgroundEnabled) Color.Transparent else MaterialTheme.colorScheme.surfaceBright
    ) {
        AppProfileInner(
            appGroup = InstalledAppGroup(
                uid = 0,
                primaryPackageName = "preview",
                apps = listOf(InstalledApp("preview", "Preview", 0)),
            ),
            appIcon = {
                Icon(
                    imageVector = Icons.TwoTone.Android,
                    contentDescription = null,
                )
            },
            profile = profile,
            topPadding = 0.dp,
            onControlApp = {},
            onProfileChange = {
                profile = it
            },
        )
    }
}
