package com.resukisu.resukisu.ui.component.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.FolderDelete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget

@Composable
fun AppProfileConfig(
    enabled: Boolean,
    profile: Natives.Profile,
    onProfileChange: (Natives.Profile) -> Unit,
) {
    SettingsSwitchWidget(
        icon = Icons.TwoTone.FolderDelete,
        title = stringResource(R.string.profile_umount_modules),
        description = stringResource(R.string.profile_umount_modules_summary),
        checked = if (enabled) {
            profile.umountModules
        } else {
            Natives.isDefaultUmountModules()
        },
        enabled = enabled,
        onCheckedChange = {
            onProfileChange(
                profile.copy(
                    umountModules = it,
                    nonRootUseDefault = false
                )
            )
        }
    )
}

@Preview
@Composable
private fun AppProfileConfigPreview() {
    var profile by remember { mutableStateOf(Natives.Profile("")) }
    AppProfileConfig(enabled = false, profile = profile) {
        profile = it
    }
}
