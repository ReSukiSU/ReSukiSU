package com.resukisu.resukisu.ui.screen.susfs.subpages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.data.susfs.SuSFSStatusInfo
import com.resukisu.resukisu.ui.component.WarningCard
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    dirtyGeneration: Int,
    configEnabled: Boolean,
    configEnabledLoaded: Boolean,
    onConfigEnabledChange: (Boolean) -> Unit,
) {
    var statusInfo by remember { mutableStateOf(SuSFSStatusInfo("", "", "")) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun loadStatus(forceRefresh: Boolean = false) {
        statusInfo = SuSFSConfigHelper.loadStatusInfo(forceRefresh)
    }

    LaunchedEffect(dirtyGeneration) {
        isLoading = true
        try {
            loadStatus(forceRefresh = dirtyGeneration > 0)
        } finally {
            isLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }

        item {
            SegmentedColumn {
                item {
                    SettingsSwitchWidget(
                        icon = Icons.TwoTone.Save,
                        title = stringResource(R.string.susfs_standard_config_enabled),
                        description = stringResource(R.string.susfs_standard_config_enabled_desc),
                        checked = configEnabled,
                        enabled = configEnabledLoaded,
                        onCheckedChange = onConfigEnabledChange,
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Info,
                        title = stringResource(R.string.susfs_status_version),
                        description = statusInfo.version.ifBlank {
                            stringResource(R.string.susfs_status_no_data)
                        }
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Settings,
                        title = stringResource(R.string.susfs_status_variant),
                        description = statusInfo.variant.ifBlank {
                            stringResource(R.string.susfs_status_no_data)
                        }
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.TaskAlt,
                        title = stringResource(R.string.susfs_status_enabled_features),
                        description = statusInfo.enabledFeatures.ifBlank {
                            stringResource(R.string.susfs_status_no_data)
                        }
                    )
                }
            }
        }

        if (configEnabledLoaded && !configEnabled) {
            item {
                WarningCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    message = stringResource(R.string.susfs_management_disabled_warning),
                )
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
        }
    }
}
