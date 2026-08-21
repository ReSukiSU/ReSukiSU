package com.resukisu.resukisu.ui.activity.component

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailColors
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resukisu.resukisu.ui.component.FloatingBottomBar
import com.resukisu.resukisu.ui.component.FloatingBottomBarItem
import com.resukisu.resukisu.ui.screen.BottomBarDestination
import com.resukisu.resukisu.ui.theme.BottomBarStyle
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.util.LocalBlurState
import com.resukisu.resukisu.ui.util.LocalHandlePageChange
import com.resukisu.resukisu.ui.util.LocalPagerState
import com.resukisu.resukisu.ui.util.LocalSelectedPage
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    destinations: List<BottomBarDestination>,
    isBottomBar: Boolean,
    floatingBackdrop: Backdrop? = null
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val superuserCount = uiState.systemInfo.superuserCount
    val moduleCount = uiState.systemInfo.moduleCount
    val page = LocalSelectedPage.current
    val handlePageChange = LocalHandlePageChange.current
    val pagerState = LocalPagerState.current

    val backdrop = LocalBlurState.current
    val fallbackBackdrop = rememberLayerBackdrop()
    // The floating bar can publish its own backdrop layer, so its glass keeps working
    // when the global blur setting is off.
    val floatingBarBackdrop = backdrop ?: floatingBackdrop
    val isBlurEnabled = floatingBarBackdrop != null

    if (isBottomBar && themeConfig.bottomBarStyle == BottomBarStyle.FLOATING) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                )
                .padding(
                    bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding()
                ),
            contentAlignment = Alignment.Center
        ) {
            FloatingBottomBar(
                selectedIndex = { pagerState.targetPage },
                onSelected = { handlePageChange(it) },
                backdrop = floatingBarBackdrop ?: fallbackBackdrop,
                tabsCount = destinations.size,
                isBlurEnabled = isBlurEnabled,
            ) {
                destinations.forEachIndexed { index, destination ->
                    FloatingBottomBarItem(
                        onClick = { handlePageChange(index) },
                        modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                    ) {
                        val contentColor = LocalContentColor.current
                        val count = when (destination) {
                            BottomBarDestination.SuperUser -> superuserCount
                            BottomBarDestination.Module -> moduleCount
                            else -> 0
                        }
                        val icon: @Composable () -> Unit = {
                            Icon(
                                imageVector = destination.iconSelected,
                                contentDescription = stringResource(destination.label),
                                tint = contentColor
                            )
                        }
                        if (count > 0) {
                            BadgedBox(badge = { Badge { Text(count.toString()) } }) { icon() }
                        } else {
                            icon()
                        }
                        Text(
                            text = stringResource(destination.label),
                            color = contentColor,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible
                        )
                    }
                }
            }
        }
    } else if (isBottomBar) {
        FlexibleBottomAppBar(
            modifier = modifier
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                )
                .blurEffect(
                    compensateHorizontalOverscroll = true,
                    compensateVerticalOverscroll = true,
                    useFixedSurfaceBoundsForOverscroll = true,
                ),
            containerColor =
                if (themeConfig.isEnableBlur)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            destinations.forEachIndexed { index, destination ->
                BottomBarNavigationItem(
                    isSelected = index == page,
                    destination = destination,
                    onClick = {
                        handlePageChange(index)
                    },
                    superuserCount = superuserCount,
                    moduleCount = moduleCount,
                )
            }
        }
    } else {
        WideNavigationRail(
            modifier = modifier
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                )
                .blurEffect(
                    compensateHorizontalOverscroll = true,
                    compensateVerticalOverscroll = false,
                    useFixedSurfaceBoundsForOverscroll = true,
                ),
            colors = WideNavigationRailColors(
                containerColor =
                    if (themeConfig.isEnableBlur)
                        Color.Transparent
                    else
                        MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modalContainerColor = WideNavigationRailDefaults.colors().modalContainerColor,
                modalScrimColor = WideNavigationRailDefaults.colors().modalScrimColor,
                modalContentColor = WideNavigationRailDefaults.colors().modalContentColor,
            ),
        ) {
            destinations.forEachIndexed { index, destination ->
                NavigationRailItem(
                    isSelected = index == page,
                    destination = destination,
                    onClick = {
                        handlePageChange(index)
                    },
                    superuserCount = superuserCount,
                    moduleCount = moduleCount,
                )
            }
        }
    }
}

@Composable
private fun NavigationRailItem(
    isSelected: Boolean,
    destination: BottomBarDestination,
    onClick: () -> Unit,
    superuserCount: Int,
    moduleCount: Int,
) {
    WideNavigationRailItem(
        railExpanded = false,
        selected = isSelected,
        onClick = onClick,
        icon = {
            BadgedBox(
                badge = {
                    DestinationBadge(
                        dest = destination,
                        superUser = superuserCount,
                        module = moduleCount,
                    )
                }
            ) {
                if (isSelected) {
                    Icon(destination.iconSelected, stringResource(destination.label))
                } else {
                    Icon(destination.iconNotSelected, stringResource(destination.label))
                }
            }
        },
        label = {
            Text(
                stringResource(destination.label),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        },
    )
}

@Composable
private fun RowScope.BottomBarNavigationItem(
    isSelected: Boolean,
    destination: BottomBarDestination,
    onClick: () -> Unit,
    superuserCount: Int,
    moduleCount: Int,
) {
    NavigationBarItem(
        selected = isSelected,
        onClick = onClick,
        icon = {
            BadgedBox(
                badge = {
                    DestinationBadge(
                        dest = destination,
                        superUser = superuserCount,
                        module = moduleCount,
                    )
                }
            ) {
                if (isSelected) {
                    Icon(destination.iconSelected, stringResource(destination.label))
                } else {
                    Icon(destination.iconNotSelected, stringResource(destination.label))
                }
            }
        },
        label = {
            Text(
                stringResource(destination.label),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        },
        alwaysShowLabel = false
    )
}

@Composable
private fun DestinationBadge(
    dest: BottomBarDestination,
    superUser: Int,
    module: Int,
) {
    val count = when (dest) {
        BottomBarDestination.SuperUser -> superUser
        BottomBarDestination.Module -> module
        else -> 0
    }

    AnimatedVisibility(
        visible = count > 0,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Badge(
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Text(count.toString())
        }
    }
}
