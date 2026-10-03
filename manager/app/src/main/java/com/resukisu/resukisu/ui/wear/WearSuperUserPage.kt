package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.twotone.Error
import com.resukisu.resukisu.ui.wear.component.WearStatusTone
import com.resukisu.resukisu.ui.wear.component.wearGroupGap
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.InstalledAppGroup
import com.resukisu.resukisu.ui.component.PackageIcon
import com.resukisu.resukisu.ui.wear.component.WearIconAction
import com.resukisu.resukisu.ui.wear.component.WearIconButtonGroup
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearStatusItem
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiState

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearSuperUserPage(
    state: SuperUserUiState,
    error: String?,
    onRefresh: () -> Unit,
    onAppClick: (Int, String) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onLogs: () -> Unit,
    onFilter: () -> Unit,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    backToTop: Boolean = false,
) {
    // As on the phone: an empty list shows the loading indicator in place of the page; once it has
    // content, refreshing shows at the pull refresh, in the edge button.
    val loading = state.isRefreshing && state.appGroupList.isEmpty() && state.search.isEmpty()
    WearList(isLoading = loading, isRefreshing = state.isRefreshing && !loading, onRefresh = onRefresh,
        onBack = onBack, onOpenPanel = onFilter, panelLabel = stringResource(R.string.wear_filter_sort),
        snap = true, listState = listState, backToTop = backToTop,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.superuser)) }
        // Search and SU log as one compact button group, matching the Modules page actions.
        item {
            WearIconButtonGroup(spec, listOf(
                WearIconAction(Icons.TwoTone.Search, stringResource(R.string.search_apps), onSearch),
                WearIconAction(Icons.AutoMirrored.TwoTone.Article, stringResource(R.string.sulog), onLogs),
            ))
        }
        // The app list follows the actions directly, separated by the 8dp group gap of the Wear list guidance.
        wearGroupGap("apps-gap")
        if (!error.isNullOrBlank()) {
            item { WearStatusItem(spec, Icons.TwoTone.Error, error, tone = WearStatusTone.ERROR) }
        }
        if (state.appGroupList.isEmpty() && error.isNullOrBlank()) item {
            WearStatusItem(spec, Icons.TwoTone.Apps, stringResource(R.string.no_apps_in_category))
        }
        // An app button list: 32dp app icon, one-line name and a one-line authorization status.
        items(state.appGroupList, key = { "${it.uid}:${it.primaryPackageName}" }) { group ->
            WearAppItem(spec, group, onAppClick)
        }
    }
}

/** One app group: its icon and name, with the authorization as icon and text, not color alone. */
@Composable
internal fun TransformingLazyColumnItemScope.WearAppItem(
    spec: TransformationSpec,
    group: InstalledAppGroup,
    onAppClick: (Int, String) -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        transformation = SurfaceTransformation(spec),
        onClick = { onAppClick(group.uid, group.primaryPackageName) },
        // Granted apps use the system accent; the others keep the neutral tonal surface.
        colors = if (group.allowSu) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        icon = {
            PackageIcon(
                packageName = if (group.isWebViewZygote) "android" else group.mainApp.packageName,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.LargeIconSize),
            )
        },
        secondaryLabel = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (group.allowSu) Icons.Default.CheckCircle else Icons.Default.Block,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    // On the primary container of an authorized app the mark uses its content color.
                    tint = if (group.allowSu) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(if (group.allowSu) R.string.wear_allowed else R.string.wear_denied),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) {
        Text(group.mainApp.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
