package com.resukisu.resukisu.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DeleteForever
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resukisu.resukisu.Natives.Profile.RootProfileFlag
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.AppProfile
import com.resukisu.resukisu.domain.model.ProfileTemplate
import com.resukisu.resukisu.toRawFlags
import com.resukisu.resukisu.toRootProfileFlags
import com.resukisu.resukisu.ui.component.profile.rootProfileConfig
import com.resukisu.resukisu.ui.component.NetworkRefreshContent
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsTextFieldWidget
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.viewmodel.TemplateEditorUiAction
import com.resukisu.resukisu.ui.viewmodel.TemplateEditorUiEvent
import com.resukisu.resukisu.ui.viewmodel.TemplateEditorViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @author weishu
 * @date 2023/10/20.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    templateId: String,
    readOnly: Boolean = true,
    isCreation: Boolean = false,
) {
    val navigator = LocalNavigator.current
    val viewModel = koinViewModel<TemplateEditorViewModel>(
        parameters = { parametersOf(templateId, readOnly, isCreation) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val template = state.template
    val autoSave = !isCreation && !readOnly
    val context = LocalContext.current
    val saveTemplateFailed = stringResource(id = R.string.app_profile_template_save_failed)

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                TemplateEditorUiEvent.Saved,
                TemplateEditorUiEvent.Deleted -> navigator.setResult("template_edit", true)

                is TemplateEditorUiEvent.Error -> Toast.makeText(
                    context,
                    saveTemplateFailed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            val author =
                if (template.author.isNotEmpty()) "@${template.author}" else ""
            val readOnlyHint = if (readOnly) {
                " - ${stringResource(id = R.string.app_profile_template_readonly)}"
            } else {
                ""
            }
            val titleSummary = "${template.id}$author$readOnlyHint"

            TopBar(
                title = if (isCreation) {
                    stringResource(R.string.app_profile_template_create)
                } else if (readOnly) {
                    stringResource(R.string.app_profile_template_view)
                } else {
                    stringResource(R.string.app_profile_template_edit)
                },
                readOnly = readOnly,
                summary = titleSummary,
                onBack = dropUnlessResumed {
                    if (readOnly) navigator.pop() else navigator.setResult("template_edit", true)
                },
                onDelete = {
                    viewModel.dispatch(TemplateEditorUiAction.Delete)
                },
                onSave = {
                    viewModel.dispatch(TemplateEditorUiAction.Save)
                },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = Color.Transparent,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .pointerInteropFilter {
                    // disable click and ripple if readOnly
                    readOnly
                }
                .blurSource()
        ) {
            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            }

            when {
                state.loading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.loadFailure != null -> item {
                    NetworkRefreshContent(
                        offline = true,
                        onRetry = { viewModel.dispatch(TemplateEditorUiAction.Load) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    )
                }

                else -> item {
                    SegmentedColumn {
                    if (isCreation) {
                        item {
                            var errorHint by remember {
                                mutableStateOf("")
                            }
                            val idInvalidError =
                                stringResource(id = R.string.app_profile_template_id_invalid)
                            TextEdit(
                                label = stringResource(id = R.string.app_profile_template_id),
                                text = template.id,
                                errorHint = errorHint,
                            ) { value ->
                                errorHint = if (!isValidTemplateId(value)) {
                                    idInvalidError
                                } else {
                                    ""
                                }
                                viewModel.dispatch(
                                    TemplateEditorUiAction.Update(template.copy(id = value))
                                )
                            }
                        }
                    }

                    item {
                        TextEdit(
                            label = stringResource(id = R.string.app_profile_template_name),
                            text = template.name
                        ) { value ->
                            viewModel.dispatch(
                                TemplateEditorUiAction.Update(
                                    template.copy(name = value),
                                    autoSave = autoSave,
                                )
                            )
                        }
                    }

                    item {
                        TextEdit(
                            label = stringResource(id = R.string.app_profile_template_description),
                            text = template.description
                        ) { value ->
                            viewModel.dispatch(
                                TemplateEditorUiAction.Update(
                                    template.copy(description = value),
                                    autoSave = autoSave,
                                )
                            )
                        }
                    }

                    rootProfileConfig(
                        profile = toAppProfile(template),
                        sepolicyValid = true,
                        onValidateSepolicy = {},
                    ) {
                        template.copy(
                            uid = it.uid,
                            gid = it.gid,
                            groups = it.groups,
                            capabilities = it.capabilities,
                            context = it.context,
                            namespace = it.namespace,
                            rules = it.rules.split("\n"),
                            flags = it.flags.toRootProfileFlags().map { flag -> flag.ordinal },
                        ).let { updated ->
                            viewModel.dispatch(
                                TemplateEditorUiAction.Update(updated, autoSave = autoSave)
                            )
                        }
                    }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
            }
        }
    }
}

fun toAppProfile(templateInfo: ProfileTemplate): AppProfile {
    val allFlags = RootProfileFlag.entries

    val mappedFlags = templateInfo.flags.mapNotNull { ordinal ->
        if (ordinal in allFlags.indices) allFlags[ordinal] else null
    }

    return AppProfile(
        name = templateInfo.id,
        rootTemplate = templateInfo.id,
        uid = templateInfo.uid,
        gid = templateInfo.gid,
        groups = templateInfo.groups,
        capabilities = templateInfo.capabilities,
        context = templateInfo.context,
        namespace = templateInfo.namespace,
        rules = templateInfo.rules.joinToString("\n").ifBlank { "" },
        flags = mappedFlags.toRawFlags(),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    title: String,
    readOnly: Boolean,
    summary: String = "",
    onBack: () -> Unit,
    onDelete: () -> Unit = {},
    onSave: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior
) {
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(),
        title = {
            Text(
                text = title
            )
        },
        subtitle = if (summary.isNotEmpty()) {
            {
                Text(
                    text = summary,
                )
            }
        } else null,
        navigationIcon = {
            AppBackButton(
                onClick = onBack
            )
        },
        actions = {
            if (readOnly) {
                return@LargeFlexibleTopAppBar
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.TwoTone.DeleteForever,
                    contentDescription = stringResource(id = R.string.app_profile_template_delete)
                )
            }
            IconButton(onClick = onSave) {
                Icon(
                    imageVector = Icons.TwoTone.Save,
                    contentDescription = stringResource(id = R.string.app_profile_template_save)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors().copy(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun TextEdit(
    label: String,
    text: String,
    errorHint: String = "",
    onValueChange: (String) -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val state = rememberTextFieldState(initialText = text)
    var lastEmittedText by remember { mutableStateOf(text) }

    SettingsTextFieldWidget(
        modifier = Modifier.fillMaxWidth(),
        state = state,
        title = label,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next
        ),
        onKeyboardAction = {
            keyboardController?.hide()
        },
        error = errorHint,
    )

    LaunchedEffect(text) {
        if (state.text.toString() != text) {
            lastEmittedText = text
            state.edit { replace(0, length, text) }
        }
    }

    LaunchedEffect(state.text) {
        val value = state.text.toString()
        if (value != lastEmittedText) {
            lastEmittedText = value
            onValueChange(value)
        }
    }
}

private fun isValidTemplateId(id: String): Boolean {
    return Regex("""^([A-Za-z][A-Za-z\d_]*\.)*[A-Za-z][A-Za-z\d_]*$""").matches(id)
}
