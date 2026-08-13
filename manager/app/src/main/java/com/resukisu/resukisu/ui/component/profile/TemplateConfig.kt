package com.resukisu.resukisu.ui.component.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.automirrored.twotone.ReadMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.resukisu.resukisu.domain.model.AppProfile
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.NetworkRefreshContent
import com.resukisu.resukisu.ui.component.settings.SettingsChooseWidget
import com.resukisu.resukisu.ui.util.ActivityResumeEffect
import com.resukisu.resukisu.ui.viewmodel.TemplateViewModel
import com.resukisu.resukisu.ui.viewmodel.TemplateUiAction
import kotlinx.coroutines.launch

/**
 * @author weishu
 * @date 2023/10/21.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateConfig(
    profile: AppProfile,
    onViewTemplate: (id: String) -> Unit = {},
    onProfileChange: (AppProfile) -> Unit
) {
    val viewModel = koinViewModel<TemplateViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ActivityResumeEffect(viewModel) {
        viewModel.dispatch(TemplateUiAction.Refresh())
    }

    var template by rememberSaveable(profile.rootTemplate) {
        mutableStateOf(profile.rootTemplate ?: "")
    }
    val profileTemplates = listOf("None") + uiState.profileTemplates
    val profileTemplateNames = listOf("None") + uiState.profileTemplateNames
    val currentIndex = profileTemplates.indexOf(template).let { if (it == -1) 0 else it }

    SettingsChooseWidget(
        icon = Icons.AutoMirrored.TwoTone.Article,
        title = stringResource(R.string.profile_template),
        items = profileTemplateNames,
        selectedIndex = currentIndex,
        emptyDialogContent = if (profileTemplates.size == 1) {
            {
                NetworkRefreshContent(
                    offline = uiState.isOffline,
                    onRetry = {
                        scope.launch {
                            viewModel.dispatch(TemplateUiAction.Refresh(synchronize = true))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                )
            }
        } else {
            null
        },
        afterContent = { index ->
            if (index == 0) return@SettingsChooseWidget
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.ReadMore,
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

        val templateInfo = uiState.templateList.firstOrNull { it.id == template }
            ?: return@SettingsChooseWidget

        onProfileChange(
            profile.copy(
                rootTemplate = template,
                rootUseDefault = false,
                uid = templateInfo.uid,
                gid = templateInfo.gid,
                groups = templateInfo.groups,
                capabilities = templateInfo.capabilities,
                context = templateInfo.context,
                rules = templateInfo.rules.joinToString("\n"),
                namespace = templateInfo.namespace,
            )
        )
    }
}
