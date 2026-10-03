package com.resukisu.resukisu.ui.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Copyright
import androidx.compose.material.icons.twotone.Group
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.PackageIcon
import com.resukisu.resukisu.ui.wear.component.WearChip
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearScaledItem
import com.resukisu.resukisu.ui.wear.component.wearGroupGap

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * Title, app identity (icon, name, version), then the project, community and license entries with
 * the phone's descriptions, and the sticker attribution as a footnote; the edge button returns.
 */
@Composable
internal fun WearAboutDetail(onBack: () -> Unit, onOpenLink: (String) -> Unit, onLicenses: () -> Unit) {
    val context = LocalContext.current
    val projectUrl = stringResource(R.string.wear_project_url)
    WearList(onBack = onBack, snap = true) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.about)) }
        item {
            WearScaledItem(spec) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PackageIcon(context.packageName, null, Modifier.size(52.dp))
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        wearAppVersion(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        wearGroupGap("identity-gap")
        item {
            WearChip(spec, stringResource(R.string.get_source_code),
                secondaryLabel = stringResource(R.string.get_source_code_detail),
                icon = Icons.TwoTone.Code, onClick = { onOpenLink(projectUrl) })
        }
        item {
            WearChip(spec, stringResource(R.string.join_telegram_group),
                secondaryLabel = stringResource(R.string.join_telegram_group_detail),
                icon = Icons.TwoTone.Group, onClick = { onOpenLink("https://t.me/ReSukiSU") })
        }
        item {
            WearChip(spec, stringResource(R.string.open_source_license),
                secondaryLabel = stringResource(R.string.license, stringResource(R.string.wear_project_license)),
                icon = Icons.TwoTone.Copyright, onClick = onLicenses)
        }
        item {
            WearScaledItem(spec) {
                Text(
                    stringResource(R.string.about_anime_character_sticker),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The manager version as shown on the phone About page; commit-hash version names are shortened. */
@Composable
internal fun wearAppVersion(): String = stringResource(
    R.string.wear_app_version,
    BuildConfig.VERSION_NAME.let { if (it.matches(Regex("[0-9a-fA-F]{40}"))) it.take(7) else it },
    BuildConfig.VERSION_CODE,
)
