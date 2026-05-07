package com.resukisu.resukisu.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
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
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.profile.rootProfileConfig
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SettingsTextFieldWidget
import com.resukisu.resukisu.ui.component.settings.SplicedColumnGroup
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.deleteAppProfileTemplate
import com.resukisu.resukisu.ui.util.getAppProfileTemplate
import com.resukisu.resukisu.ui.util.setAppProfileTemplate
import com.resukisu.resukisu.ui.viewmodel.TemplateViewModel
import com.resukisu.resukisu.ui.viewmodel.toJSON

/**
 * @author weishu
 * @date 2023/10/20.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    initialTemplate: TemplateViewModel.TemplateInfo,
    readOnly: Boolean = true,
) {
    val navigator = LocalNavigator.current
    val isCreation = initialTemplate.id.isBlank()
    val autoSave = !isCreation

    var template by rememberSaveable {
        mutableStateOf(initialTemplate)
    }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    Scaffold(
        topBar = {
            val author =
                if (initialTemplate.author.isNotEmpty()) "@${initialTemplate.author}" else ""
            val readOnlyHint = if (readOnly) {
                " - ${stringResource(id = R.string.app_profile_template_readonly)}"
            } else {
                ""
            }
            val titleSummary = "${initialTemplate.id}$author$readOnlyHint"
            val saveTemplateFailed = stringResource(id = R.string.app_profile_template_save_failed)
            val context = LocalContext.current

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
                    if (deleteAppProfileTemplate(template.id)) {
                        navigator.setResult("template_edit", true)
                    }
                },
                onSave = {
                    if (saveTemplate(template, isCreation)) {
                        navigator.setResult("template_edit", true)
                    } else {
                        Toast.makeText(context, saveTemplateFailed, Toast.LENGTH_SHORT).show()
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .pointerInteropFilter {
                    // disable click and ripple if readOnly
                    readOnly
                }
                .blurSource()
        ) {
            SplicedColumnGroup {
                if (isCreation) {
                    item {
                        var errorHint by remember {
                            mutableStateOf("")
                        }
                        val idConflictError =
                            stringResource(id = R.string.app_profile_template_id_exist)
                        val idInvalidError =
                            stringResource(id = R.string.app_profile_template_id_invalid)
                        TextEdit(
                            label = stringResource(id = R.string.app_profile_template_id),
                            text = template.id,
                            errorHint = errorHint,
                        ) { value ->
                            errorHint = if (isTemplateExist(value)) {
                                idConflictError
                            } else if (!isValidTemplateId(value)) {
                                idInvalidError
                            } else {
                                ""
                            }
                            template = template.copy(id = value)
                        }
                    }
                }

                item {
                    TextEdit(
                        label = stringResource(id = R.string.app_profile_template_name),
                        text = template.name
                    ) { value ->
                        template.copy(name = value).run {
                            if (autoSave) {
                                if (!saveTemplate(this)) {
                                    // failed
                                    return@run
                                }
                            }
                            template = this
                        }
                    }
                }

                item {
                    TextEdit(
                        label = stringResource(id = R.string.app_profile_template_description),
                        text = template.description
                    ) { value ->
                        template.copy(description = value).run {
                            if (autoSave) {
                                if (!saveTemplate(this)) {
                                    // failed
                                    return@run
                                }
                            }
                            template = this
                        }
                    }
                }

                rootProfileConfig(
                    profile = toNativeProfile(template)
                ) {
                    template.copy(
                        uid = it.uid,
                        gid = it.gid,
                        groups = it.groups,
                        capabilities = it.capabilities,
                        context = it.context,
                        namespace = it.namespace,
                        rules = it.rules.split("\n")
                    ).run {
                        if (autoSave) {
                            if (!saveTemplate(this)) {
                                // failed
                                return@run
                            }
                        }
                        template = this
                    }
                }
            }
        }
    }
}

fun toNativeProfile(templateInfo: TemplateViewModel.TemplateInfo): Natives.Profile {
    return Natives.Profile().copy(rootTemplate = templateInfo.id,
        uid = templateInfo.uid,
        gid = templateInfo.gid,
        groups = templateInfo.groups,
        capabilities = templateInfo.capabilities,
        context = templateInfo.context,
        namespace = templateInfo.namespace,
        rules = templateInfo.rules.joinToString("\n").ifBlank { "" })
}

fun isTemplateValid(template: TemplateViewModel.TemplateInfo): Boolean {
    if (template.id.isBlank()) {
        return false
    }

    if (!isValidTemplateId(template.id)) {
        return false
    }

    return true
}

fun saveTemplate(template: TemplateViewModel.TemplateInfo, isCreation: Boolean = false): Boolean {
    if (!isTemplateValid(template)) {
        return false
    }

    if (isCreation && isTemplateExist(template.id)) {
        return false
    }

    val json = template.toJSON()
    json.put("local", true)
    return setAppProfileTemplate(template.id, json.toString())
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
        modifier = Modifier.blurEffect(
        ),
        title = {
            Text(
                text = title
            )
        },
        subtitle = {
            Text(
                text = summary,
            )
        },
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
                    Icons.Filled.DeleteForever,
                    contentDescription = stringResource(id = R.string.app_profile_template_delete)
                )
            }
            IconButton(onClick = onSave) {
                Icon(
                    imageVector = Icons.Filled.Save,
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

    LaunchedEffect(state.text) {
        onValueChange(state.text.toString())
    }
}

private fun isValidTemplateId(id: String): Boolean {
    return Regex("""^([A-Za-z][A-Za-z\d_]*\.)*[A-Za-z][A-Za-z\d_]*$""").matches(id)
}

private fun isTemplateExist(id: String): Boolean {
    return getAppProfileTemplate(id).isNotBlank()
}