package com.resukisu.resukisu.ui.component.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.profile.Capabilities
import com.resukisu.resukisu.profile.Groups
import com.resukisu.resukisu.toRawFlags
import com.resukisu.resukisu.toRootProfileFlags
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SegmentedColumnScope
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsChooseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsTextFieldWidget
import com.resukisu.resukisu.ui.util.isSepolicyValid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootProfileConfig(
    profile: Natives.Profile,
    onProfileChange: (Natives.Profile) -> Unit,
) {
    SegmentedColumn {
        rootProfileConfig(
            profile,
            onProfileChange
        )
    }
}

fun SegmentedColumnScope.rootProfileConfig(
    profile: Natives.Profile,
    onProfileChange: (Natives.Profile) -> Unit,
) {
    item {
        UidPanel(uid = profile.uid, label = "uid", onUidChange = {
            onProfileChange(
                profile.copy(
                    uid = it,
                    rootUseDefault = false
                )
            )
        })
    }

    item {
        UidPanel(uid = profile.gid, label = "gid", onUidChange = {
            onProfileChange(
                profile.copy(
                    gid = it,
                    rootUseDefault = false
                )
            )
        })
    }

    item {
        val selectedGroups = profile.groups.ifEmpty { listOf(0) }.let { e ->
            e.mapNotNull { g ->
                Groups.entries.find { it.gid == g }
            }
        }
        GroupsPanel(selectedGroups) {
            onProfileChange(
                profile.copy(
                    groups = it.map { group -> group.gid }.ifEmpty { listOf(0) },
                    rootUseDefault = false
                )
            )
        }
    }

    item {
        val selectedCaps = profile.capabilities.mapNotNull { e ->
            Capabilities.entries.find { it.cap == e }
        }

        CapsPanel(selectedCaps) {
            onProfileChange(
                profile.copy(
                    capabilities = it.map { cap -> cap.cap },
                    rootUseDefault = false
                )
            )
        }
    }

    item {
        MountNameSpacePanel(profile = profile) {
            onProfileChange(
                profile.copy(
                    namespace = it,
                    rootUseDefault = false
                )
            )
        }
    }

    item {
        RootProfileFlagPanel(selected = profile.flags.toRootProfileFlags()) {
            onProfileChange(
                profile.copy(
                    flags = it.toRawFlags(),
                )
            )
        }
    }

    item {
        SELinuxPanel(profile = profile, onSELinuxChange = { domain, rules ->
            onProfileChange(
                profile.copy(
                    context = domain,
                    rules = rules,
                    rootUseDefault = false
                )
            )
        })
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GroupsPanel(selected: List<Groups>, closeSelection: (selection: Set<Groups>) -> Unit) {
    val groups = remember(selected) {
        Groups.entries.toTypedArray().sortedWith(
            compareBy<Groups> { if (selected.contains(it)) 0 else 1 }
                .then(compareBy {
                    when (it) {
                        Groups.ROOT -> 0
                        Groups.SYSTEM -> 1
                        Groups.SHELL -> 2
                        else -> Int.MAX_VALUE
                    }
                })
                .then(compareBy { it.name })
        )
    }
    val selectedIndices = remember(groups, selected) {
        groups.mapIndexedNotNull { index, group -> index.takeIf { group in selected } }.toSet()
    }

    SettingsChooseWidget(
        title = stringResource(R.string.profile_groups),
        iconPlaceholder = false,
        items = groups.map { it.display },
        itemDescriptions = groups.map { it.desc },
        selectedIndices = selectedIndices,
        maxSelected = 32,
        onSelectedIndicesChange = { indices ->
            closeSelection(indices.mapNotNull { index -> groups.getOrNull(index) }.toSet())
        },
        descriptionColumnContent = {
            FlowRow {
                selected.forEach { group ->
                    AssistChip(
                        modifier = Modifier.padding(3.dp),
                        onClick = {},
                        label = { Text(group.display) })
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CapsPanel(
    selected: Collection<Capabilities>,
    closeSelection: (selection: Set<Capabilities>) -> Unit
) {
    val caps = remember(selected) {
        Capabilities.entries.toTypedArray().sortedWith(
            compareBy<Capabilities> { if (selected.contains(it)) 0 else 1 }
                .then(compareBy { it.name })
        )
    }
    val selectedIndices = remember(caps, selected) {
        caps.mapIndexedNotNull { index, capability -> index.takeIf { capability in selected } }
            .toSet()
    }

    SettingsChooseWidget(
        title = stringResource(R.string.profile_capabilities),
        iconPlaceholder = false,
        items = caps.map { it.display },
        itemDescriptions = caps.map { it.desc },
        selectedIndices = selectedIndices,
        onSelectedIndicesChange = { indices ->
            closeSelection(indices.mapNotNull { index -> caps.getOrNull(index) }.toSet())
        },
        descriptionColumnContent = {
            FlowRow {
                selected.forEach { group ->
                    AssistChip(
                        modifier = Modifier.padding(3.dp),
                        onClick = {},
                        label = { Text(group.display) })
                }
            }
        }
    )
}

@Composable
private fun UidPanel(uid: Int, label: String, onUidChange: (Int) -> Unit) {
    var lastValidText by remember { mutableStateOf(uid.toString()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val state = rememberTextFieldState(initialText = uid.toString())

    SettingsTextFieldWidget(
        modifier = Modifier
            .fillMaxWidth(),
        labelColor = MaterialTheme.colorScheme.onSurface,
        title = label,
        state = state,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        onKeyboardAction = {
            keyboardController?.hide()
        },
    )

    LaunchedEffect(state.text) {
        val currentText = state.text.toString()

        if (currentText.isEmpty()) {
            lastValidText = ""
            onUidChange(0)
            return@LaunchedEffect
        }

        if (isTextValidUid(currentText)) {
            lastValidText = currentText
            onUidChange(currentText.toInt())
        } else {
            state.edit {
                replace(0, length, lastValidText)
            }
        }
    }
}
@Composable
fun MountNameSpacePanel(
    profile: Natives.Profile, onMntNamespaceChange: (namespaceType: Int) -> Unit
) {
    SettingsChooseWidget(
        iconPlaceholder = false,
        title = stringResource(id = R.string.profile_namespace), items = listOf(
            stringResource(id = R.string.profile_namespace_inherited),
            stringResource(id = R.string.profile_namespace_global),
            stringResource(id = R.string.profile_namespace_individual),
        ), selectedIndex = profile.namespace, onSelectedIndexChange = { index ->
            onMntNamespaceChange(index)
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootProfileFlagPanel(
    selected: List<Natives.Profile.RootProfileFlag>,
    onFlagChange: (flags: List<Natives.Profile.RootProfileFlag>) -> Unit
) {
    val caps = remember(selected) {
        Natives.Profile.RootProfileFlag.entries.toTypedArray().sortedWith(
            compareBy<Natives.Profile.RootProfileFlag> { if (selected.contains(it)) 0 else 1 }
                .then(compareBy { it.name })
        )
    }
    val selectedIndices = remember(caps, selected) {
        caps.mapIndexedNotNull { index, flag -> index.takeIf { flag in selected } }.toSet()
    }
    val descriptions = caps.map { stringResource(it.desc) }

    SettingsChooseWidget(
        title = stringResource(R.string.profile_flags),
        iconPlaceholder = false,
        items = caps.map { it.display },
        itemDescriptions = descriptions,
        selectedIndices = selectedIndices,
        onSelectedIndicesChange = { indices ->
            onFlagChange(indices.mapNotNull { index -> caps.getOrNull(index) })
        },
        descriptionColumnContent = {
            FlowRow {
                selected.forEach { group ->
                    AssistChip(
                        modifier = Modifier.padding(3.dp),
                        onClick = {},
                        label = { Text(group.display) })
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SELinuxPanel(
    profile: Natives.Profile,
    onSELinuxChange: (domain: String, rules: String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsBaseWidget(
        title = stringResource(R.string.profile_selinux_context),
        iconPlaceholder = false,
        description = profile.context,
        onClick = {
            showDialog = true
        },
    ) {}

    if (showDialog) {
        var domain by remember(profile.context) { mutableStateOf(profile.context) }
        var rules by remember(profile.rules) { mutableStateOf(profile.rules) }
        val canConfirm = isSELinuxDomainValid(domain) && isSepolicyValid(rules)

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(text = stringResource(R.string.profile_selinux_context))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text(text = stringResource(R.string.profile_selinux_domain)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        isError = domain.isNotEmpty() && !isSELinuxDomainValid(domain),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rules,
                        onValueChange = { rules = it },
                        label = { Text(text = stringResource(R.string.profile_selinux_rules)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                        ),
                        singleLine = false,
                        minLines = 4,
                        isError = !isSepolicyValid(rules),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canConfirm,
                    onClick = {
                        onSELinuxChange(domain, rules)
                        showDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Preview
@Composable
private fun RootProfileConfigPreview() {
    var profile by remember { mutableStateOf(Natives.Profile("")) }
    RootProfileConfig(profile = profile) {
        profile = it
    }
}

private fun isTextValidUid(text: String): Boolean {
    return try {
        text.isNotEmpty() && text.isDigitsOnly() && text.toInt() >= 0
    } catch (_: Throwable) {
        false
    }
}

private fun isSELinuxDomainValid(value: String): Boolean {
    return value.matches(Regex("^[a-z_]+:[a-z0-9_]+:[a-z0-9_]+(:[a-z0-9_]+)?$"))
}
