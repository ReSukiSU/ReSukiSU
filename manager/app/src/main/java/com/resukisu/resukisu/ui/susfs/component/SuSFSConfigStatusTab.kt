package com.resukisu.resukisu.ui.susfs.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget

/**
 * 状态总览标签页
 *
 * 展示 SUSFS 的版本、变体与已启用功能。
 * 状态信息通过 SuSFSConfigHelper 缓存，页面级刷新会强制重新读取。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    refreshToken: Int
) {
    var statusInfo by remember { mutableStateOf(SuSFSStatusInfo("", "", "")) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun loadStatus(forceRefresh: Boolean = false) {
        statusInfo = SuSFSConfigHelper.loadStatusInfo(forceRefresh)
    }

    LaunchedEffect(refreshToken) {
        isLoading = true
        try {
            loadStatus(forceRefresh = refreshToken > 0)
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
        } else {
            item {
                SegmentedColumn {
                    item {
                        SettingsBaseWidget(
                            iconPlaceholder = false,
                            title = stringResource(R.string.susfs_status_version),
                            description = statusInfo.version.ifBlank {
                                stringResource(R.string.susfs_status_no_data)
                            }
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            iconPlaceholder = false,
                            title = stringResource(R.string.susfs_status_variant),
                            description = statusInfo.variant.ifBlank {
                                stringResource(R.string.susfs_status_no_data)
                            }
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            iconPlaceholder = false,
                            title = stringResource(R.string.susfs_status_enabled_features),
                            description = statusInfo.enabledFeatures.ifBlank {
                                stringResource(R.string.susfs_status_no_data)
                            }
                        )
                    }
                }
            }
        }
    }
}
