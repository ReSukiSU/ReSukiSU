package com.resukisu.resukisu.ui.screen

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.profile.AppProfileConfig
import com.resukisu.resukisu.ui.component.profile.RootProfileConfig
import com.resukisu.resukisu.ui.component.profile.TemplateConfig
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.component.settings.SplicedColumnGroup
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.forceStopApp
import com.resukisu.resukisu.ui.util.getSepolicy
import com.resukisu.resukisu.ui.util.launchApp
import com.resukisu.resukisu.ui.util.restartApp
import com.resukisu.resukisu.ui.util.setSepolicy
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import com.resukisu.resukisu.ui.viewmodel.getTemplateInfoById
import kotlinx.coroutines.launch

/**
 * @author weishu
 * @date 2023/5/16.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppProfileScreen(
    appGroup: SuperUserViewModel.AppGroup,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackBarHost = LocalSnackbarHost.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val failToUpdateAppProfile = stringResource(R.string.failed_to_update_app_profile).format(
        appGroup.mainApp.label
    )
    val failToUpdateSepolicy =
        stringResource(R.string.failed_to_update_sepolicy).format(appGroup.mainApp.label)
    val suNotAllowed = stringResource(R.string.su_not_allowed).format(appGroup.mainApp.label)

    val packageName = appGroup.mainApp.packageName
    val initialProfile = Natives.getAppProfile(packageName, appGroup.uid)
    if (initialProfile.allowSu) {
        initialProfile.rules = getSepolicy(packageName)
    }
    var profile by rememberSaveable {
        mutableStateOf(initialProfile)
    }

    val colorScheme = MaterialTheme.colorScheme
    val cardColor = if (CardConfig.isCustomBackgroundEnabled) {
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
                AsyncImage(
                    model = ImageRequest.Builder(context).data(appGroup.mainApp.packageInfo)
                        .crossfade(true).build(),
                    contentDescription = appGroup.mainApp.label,
                    modifier = Modifier
                        .padding(4.dp)
                        .width(48.dp)
                        .height(48.dp)
                )
            },
            profile = profile,
            onViewTemplate = {
                getTemplateInfoById(it)?.let { info ->
                    navigator.push(Route.TemplateEditor(info, true))
                }
            },
            onManageTemplate = {
                navigator.push(Route.AppProfileTemplate)
            },
            onProfileChange = {
                scope.launch {
                    if (it.allowSu) {
                        // sync with allowlist.c - forbid_system_uid
                        if (appGroup.uid < 2000 && appGroup.uid != 1000) {
                            snackBarHost.showSnackbar(suNotAllowed)
                            return@launch
                        }
                        if (!it.rootUseDefault && it.rules.isNotEmpty() && !setSepolicy(profile.name, it.rules)) {
                            snackBarHost.showSnackbar(failToUpdateSepolicy)
                            return@launch
                        }
                    }
                    if (!Natives.setAppProfile(it)) {
                        snackBarHost.showSnackbar(failToUpdateAppProfile.format(appGroup.uid))
                    } else {
                        profile = it
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppProfileInner(
    modifier: Modifier = Modifier,
    topPadding: Dp,
    appGroup: SuperUserViewModel.AppGroup,
    appIcon: @Composable () -> Unit,
    profile: Natives.Profile,
    onViewTemplate: (id: String) -> Unit = {},
    onManageTemplate: () -> Unit = {},
    onProfileChange: (Natives.Profile) -> Unit,
) {
    val isRootGranted = profile.allowSu

    LazyColumn(modifier = modifier) {
        item {
            Spacer(modifier = Modifier.height(topPadding))
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                    alpha = CardConfig.cardAlpha
                ),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                var expanded by remember { mutableStateOf(false) }
                var touchPoint: Offset by remember { mutableStateOf(Offset.Zero) }
                val density = LocalDensity.current

                SettingsBaseWidget(
                    title = appGroup.mainApp.label,
                    description = appGroup.mainApp.packageName,
                    iconPlaceholder = false,
                    rowHeader = {
                        appIcon()
                    },
                    onClick = {
                        touchPoint = it
                        expanded = true
                    }
                ) {}

                val (offsetX, offsetY) = with(density) {
                    (touchPoint.x.toDp()) to (-touchPoint.y.toDp())
                }
                DropdownMenu(
                    expanded = expanded,
                    offset = DpOffset(offsetX, offsetY),
                    onDismissRequest = {
                        expanded = false
                    }
                ) {
                    AppMenuOption(
                        text = stringResource(id = R.string.launch_app),
                        onClick = {
                            expanded = false
                            launchApp(appGroup.mainApp.packageName)
                        }
                    )

                    AppMenuOption(
                        text = stringResource(id = R.string.force_stop_app),
                        onClick = {
                            expanded = false
                            forceStopApp(appGroup.mainApp.packageName)
                        }
                    )

                    AppMenuOption(
                        text = stringResource(id = R.string.restart_app),
                        onClick = {
                            expanded = false
                            restartApp(appGroup.mainApp.packageName)
                        }
                    )
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                    alpha = CardConfig.cardAlpha
                ),
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            {
                SettingsSwitchWidget(
                    icon = Icons.Filled.Security,
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
                                .padding(top = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                alpha = CardConfig.cardAlpha
                            ),
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
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                                alpha = CardConfig.cardAlpha
                                            ),
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ) {
                                            Column {
                                                TemplateConfig(
                                                    profile = profile,
                                                    onViewTemplate = onViewTemplate,
                                                    onManageTemplate = onManageTemplate,
                                                    onProfileChange = onProfileChange
                                                )
                                            }
                                        }
                                    }

                                    Mode.Custom -> {
                                        RootProfileConfig(
                                            profile = profile,
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
                                .padding(top = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                alpha = CardConfig.cardAlpha
                            ),
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
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                    alpha = CardConfig.cardAlpha
                                ),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ) {
                                AppProfileConfig(
                                    enabled = mode == Mode.Custom,
                                    profile = profile,
                                    onProfileChange = onProfileChange
                                )
                            }
                        }
                    }
                }
            }
        }

        if (appGroup.apps.size > 1) {
            item {
                SplicedColumnGroup(
                    title = stringResource(R.string.affected_applications)
                ) {
                    appGroup.apps.forEach { app ->
                        item {
                            val context = LocalContext.current

                            SettingsBaseWidget(
                                modifier = Modifier.padding(vertical = 5.dp),
                                title = app.label,
                                description = app.packageName,
                                enabled = false,
                                iconPlaceholder = false,
                                noVerticalPadding = true,
                                rowHeader = {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(app.packageInfo)
                                            .crossfade(true).build(),
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .width(48.dp)
                                            .height(48.dp)
                                    )
                                },
                            ) {}
                        }
                    }
                }
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
            icon = Icons.Filled.AccountCircle,
            title = stringResource(R.string.profile),
            description = mode.text,
        ) {}

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

@Composable
private fun AppMenuOption(text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        onClick = onClick
    )
}

@Preview
@Composable
private fun AppProfilePreview() {
    var profile by remember { mutableStateOf(Natives.Profile("")) }

    Surface(
        color = if (CardConfig.isCustomBackgroundEnabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        AppProfileInner(
            appGroup = SuperUserViewModel.AppGroup(0, emptyList(), null),
            appIcon = {
                Icon(
                    imageVector = Icons.Filled.Android,
                    contentDescription = null,
                )
            },
            profile = profile,
            topPadding = 0.dp,
            onProfileChange = {
                profile = it
            },
        )
    }
}