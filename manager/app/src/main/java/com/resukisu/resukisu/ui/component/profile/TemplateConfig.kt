package com.resukisu.resukisu.ui.component.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReadMore
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.settings.SettingsChooseWidget
import com.resukisu.resukisu.ui.util.listAppProfileTemplates
import com.resukisu.resukisu.ui.util.setSepolicy
import com.resukisu.resukisu.ui.viewmodel.getTemplateInfoById

/**
 * @author weishu
 * @date 2023/10/21.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateConfig(
    profile: Natives.Profile,
    onViewTemplate: (id: String) -> Unit = {},
    onProfileChange: (Natives.Profile) -> Unit
) {
    var template by rememberSaveable {
        mutableStateOf(profile.rootTemplate ?: "")
    }
    val profileTemplates = listOf("None") + listAppProfileTemplates()
    val currentIndex = profileTemplates.indexOf(template).let { if (it == -1) 0 else it }

    SettingsChooseWidget(
        icon = Icons.AutoMirrored.Rounded.Article,
        title = stringResource(R.string.profile_template),
        items = profileTemplates,
        selectedIndex = currentIndex,
        afterContent = { index ->
            if (index == 0) return@SettingsChooseWidget
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReadMore,
                contentDescription = null,
                modifier = Modifier
                    .size(35.dp)
                    .clip(CircleShape)
                    .clickable {
                        onViewTemplate(profileTemplates[index])
                    }
                    .padding(5.dp)
            )
        }
    ) { index ->
        if (index == 0) {
            template = ""
            return@SettingsChooseWidget
        }

        template = profileTemplates[index]

        val templateInfo =
            getTemplateInfoById(template) ?: return@SettingsChooseWidget

        if (setSepolicy(template, templateInfo.rules.joinToString("\n"))) {
            onProfileChange(
                profile.copy(
                    rootTemplate = template,
                    rootUseDefault = false,
                    uid = templateInfo.uid,
                    gid = templateInfo.gid,
                    groups = templateInfo.groups,
                    capabilities = templateInfo.capabilities,
                    context = templateInfo.context,
                    namespace = templateInfo.namespace,
                )
            )
        }
    }
}