package com.resukisu.resukisu.ui.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.ksuIsValid
import com.resukisu.resukisu.ui.screen.main.HomePage
import com.resukisu.resukisu.ui.screen.main.ModulePage
import com.resukisu.resukisu.ui.screen.main.SettingsPage
import com.resukisu.resukisu.ui.screen.main.SuperUserPage

enum class BottomBarDestination(
    val direction: @Composable (bottomPadding: Dp) -> Unit,
    @param:StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
    val rootRequired: Boolean,
) {
    Home(
        { bottomPadding -> HomePage(bottomPadding) },
        R.string.home,
        Icons.Filled.Home,
        Icons.Outlined.Home,
        false
    ),
    SuperUser(
        { bottomPadding -> SuperUserPage(bottomPadding) },
        R.string.superuser,
        Icons.Filled.AdminPanelSettings,
        Icons.Outlined.AdminPanelSettings,
        true
    ),
    Module(
        { bottomPadding -> ModulePage(bottomPadding) },
        R.string.module,
        Icons.Filled.Extension,
        Icons.Outlined.Extension,
        true
    ),
    Settings(
        { bottomPadding -> SettingsPage(bottomPadding) },
        R.string.settings,
        Icons.Filled.Settings,
        Icons.Outlined.Settings,
        false
    );

    companion object {
        fun getPages(): List<BottomBarDestination> {
            return if (ksuIsValid()) {
                // 全功能管理器
                BottomBarDestination.entries.toList()
            } else {
                BottomBarDestination.entries.filter {
                    !it.rootRequired
                }
            }
        }
    }
}
