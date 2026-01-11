package com.resukisu.resukisu.ui.activity.component

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.ui.MainActivity
import com.resukisu.resukisu.ui.screen.BottomBarDestination
import com.resukisu.resukisu.ui.theme.CardConfig.cardAlpha
import com.resukisu.resukisu.ui.util.LocalHandlePageChange
import com.resukisu.resukisu.ui.util.LocalSelectedPage
import com.resukisu.resukisu.ui.util.getKpmModuleCount
import com.resukisu.resukisu.ui.util.getModuleCount
import com.resukisu.resukisu.ui.util.getSuperuserCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomBar(destinations: List<BottomBarDestination>) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val activity = LocalContext.current as MainActivity

    // 是否隐藏 badge
    val isHideOtherInfo by activity.settingsStateFlow
        .map { it.isHideOtherInfo }
        .collectAsState(initial = false)

    // 翻页处理
    val page = LocalSelectedPage.current
    val handlePageChange = LocalHandlePageChange.current

    // 收集计数数据
    val superuserCount by produceState(initialValue = 0) {
        withContext(Dispatchers.IO) {
            value = getSuperuserCount()
        }
    }
    val moduleCount by produceState(initialValue = 0) {
        withContext(Dispatchers.IO) {
            value = getModuleCount()
        }
    }
    val kpmModuleCount by produceState(initialValue = 0) {
        withContext(Dispatchers.IO) {
            value = getKpmModuleCount()
        }
    }

    FlexibleBottomAppBar(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
        ),
        expandedHeight = 72.dp,
        containerColor = TopAppBarDefaults.topAppBarColors(
            containerColor = cardColor.copy(alpha = cardAlpha),
            scrolledContainerColor = cardColor.copy(alpha = cardAlpha)
        ).containerColor
    ) {
        destinations.forEachIndexed { index, destination ->
            val pageSelected = index == page
            val badge : @Composable BoxScope.() -> Unit = {
                when (destination) {
                    BottomBarDestination.Kpm -> {
                        Row {
                            AnimatedVisibility(
                                visible = kpmModuleCount > 0 && !isHideOtherInfo,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ) {
                                    Text(
                                        text = kpmModuleCount.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    BottomBarDestination.SuperUser -> {
                        Row {
                            AnimatedVisibility(
                                visible = superuserCount > 0 && !isHideOtherInfo,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ) {
                                    Text(
                                        text = superuserCount.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    BottomBarDestination.Module -> {
                        Row {
                            AnimatedVisibility(
                                visible = moduleCount > 0 && !isHideOtherInfo,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ) {
                                    Text(
                                        text = moduleCount.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    else -> null
                }
            }

            NavigationBarItem(
                selected = pageSelected,
                onClick = {
                    handlePageChange(index)
                },
                icon = {
                    BadgedBox(
                        badge = badge
                    ) {
                        if (pageSelected) {
                            Icon(destination.iconSelected, stringResource(destination.label))
                        } else {
                            Icon(destination.iconNotSelected, stringResource(destination.label))
                        }
                    }
                },
                label = { Text(stringResource(destination.label),style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = false
            )
        }
    }
}