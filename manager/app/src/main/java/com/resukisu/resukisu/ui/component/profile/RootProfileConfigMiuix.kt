package com.resukisu.resukisu.ui.component.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.profile.Capabilities
import com.resukisu.resukisu.profile.Groups
import com.resukisu.resukisu.toRawFlags
import com.resukisu.resukisu.toRootProfileFlags
import com.resukisu.resukisu.ui.component.miuix.SuperEditArrow
import com.resukisu.resukisu.ui.util.isSepolicyValid
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun RootProfileConfigMiuix(
    profile: Natives.Profile,
    onProfileChange: (Natives.Profile) -> Unit,
    enabled: Boolean = true,
) {
    Column {
        SuperEditArrow(
            enabled = enabled,
            title = "UID",
            defaultValue = profile.uid,
        ) {
            onProfileChange(profile.copy(uid = it, rootUseDefault = false))
        }

        SuperEditArrow(
            enabled = enabled,
            title = "GID",
            defaultValue = profile.gid,
        ) {
            onProfileChange(profile.copy(gid = it, rootUseDefault = false))
        }

        val selectedGroups = profile.groups.mapNotNull { g -> Groups.entries.find { it.gid == g } }
        GroupsPanel(enabled, selectedGroups) {
            onProfileChange(
                profile.copy(
                    groups = it.map { group -> group.gid },
                    rootUseDefault = false
                )
            )
        }

        val selectedCaps = profile.capabilities.mapNotNull { e -> Capabilities.entries.find { it.cap == e } }
        CapsPanel(enabled, selectedCaps) {
            onProfileChange(
                profile.copy(
                    capabilities = it.map { cap -> cap.cap },
                    rootUseDefault = false
                )
            )
        }

        MountNameSpacePanel(enabled = enabled, profile = profile) {
            onProfileChange(profile.copy(namespace = it, rootUseDefault = false))
        }

        RootProfileFlagPanel(enabled = enabled, selected = profile.flags.toRootProfileFlags()) {
            onProfileChange(profile.copy(flags = it.toRawFlags()))
        }

        SELinuxPanelMiuix(enabled = enabled, profile = profile, onSELinuxChange = { domain, rules ->
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

@Composable
private fun GroupsPanel(
    enabled: Boolean,
    selected: List<Groups>,
    closeSelection: (selection: Set<Groups>) -> Unit
) {
    val showDialog = remember { mutableStateOf(false) }
    val groups = remember {
        Groups.entries.toTypedArray().sortedWith(
            compareBy<Groups> {
                when (it) {
                    Groups.ROOT -> 0
                    Groups.SYSTEM -> 1
                    Groups.SHELL -> 2
                    else -> Int.MAX_VALUE
                }
            }.then(compareBy { it.name })
        )
    }
    val currentSelection = remember(selected) { mutableStateOf(selected.toSet()) }

    OverlayDialog(
        show = showDialog.value,
        title = stringResource(R.string.profile_groups),
        summary = "${currentSelection.value.size} / 32",
        onDismissRequest = { showDialog.value = false },
        insideMargin = DpSize(0.dp, 24.dp),
        content = {
            SelectionDialogBody(
                items = groups,
                itemTitle = { it.display },
                itemSummary = { it.desc },
                isChecked = { currentSelection.value.contains(it) },
                onToggle = { group, isChecked ->
                    val newSelection = currentSelection.value.toMutableSet()
                    if (isChecked) {
                        if (newSelection.size < 32) newSelection.add(group)
                    } else {
                        newSelection.remove(group)
                    }
                    currentSelection.value = newSelection
                },
                onCancel = {
                    currentSelection.value = selected.toSet()
                    showDialog.value = false
                },
                onConfirm = {
                    closeSelection(currentSelection.value)
                    showDialog.value = false
                }
            )
        }
    )

    ArrowPreference(
        enabled = enabled,
        title = stringResource(R.string.profile_groups),
        summary = if (selected.isEmpty()) "None" else selected.joinToString(",") { it.display },
        onClick = { showDialog.value = true }
    )
}

@Composable
private fun CapsPanel(
    enabled: Boolean,
    selected: Collection<Capabilities>,
    closeSelection: (selection: Set<Capabilities>) -> Unit
) {
    val showDialog = remember { mutableStateOf(false) }
    val caps = remember { Capabilities.entries.toTypedArray().sortedBy { it.display } }
    val currentSelection = remember(selected) { mutableStateOf(selected.toSet()) }

    OverlayDialog(
        show = showDialog.value,
        title = stringResource(R.string.profile_capabilities),
        onDismissRequest = { showDialog.value = false },
        insideMargin = DpSize(0.dp, 24.dp),
        content = {
            SelectionDialogBody(
                items = caps,
                itemTitle = { it.display },
                itemSummary = { it.desc },
                isChecked = { currentSelection.value.contains(it) },
                onToggle = { cap, isChecked ->
                    val newSelection = currentSelection.value.toMutableSet()
                    if (isChecked) newSelection.add(cap) else newSelection.remove(cap)
                    currentSelection.value = newSelection
                },
                onCancel = {
                    currentSelection.value = selected.toSet()
                    showDialog.value = false
                },
                onConfirm = {
                    closeSelection(currentSelection.value)
                    showDialog.value = false
                }
            )
        }
    )

    ArrowPreference(
        enabled = enabled,
        title = stringResource(R.string.profile_capabilities),
        summary = if (selected.isEmpty()) "None" else selected.joinToString(",") { it.display },
        onClick = { showDialog.value = true }
    )
}

@Composable
private fun MountNameSpacePanel(
    enabled: Boolean,
    profile: Natives.Profile,
    onMntNamespaceChange: (namespaceType: Int) -> Unit
) {
    OverlayDropdownPreference(
        enabled = enabled,
        title = stringResource(id = R.string.profile_namespace),
        items = listOf(
            stringResource(id = R.string.profile_namespace_inherited),
            stringResource(id = R.string.profile_namespace_global),
            stringResource(id = R.string.profile_namespace_individual),
        ),
        selectedIndex = profile.namespace,
        onSelectedIndexChange = { index -> onMntNamespaceChange(index) }
    )
}

@Composable
private fun RootProfileFlagPanel(
    enabled: Boolean,
    selected: List<Natives.Profile.RootProfileFlag>,
    closeSelection: (selection: List<Natives.Profile.RootProfileFlag>) -> Unit
) {
    val showDialog = remember { mutableStateOf(false) }
    val flags = remember { Natives.Profile.RootProfileFlag.entries.toTypedArray().sortedBy { it.display } }
    val currentSelection = remember(selected) { mutableStateOf(selected.toSet()) }

    OverlayDialog(
        show = showDialog.value,
        title = stringResource(R.string.profile_flags),
        onDismissRequest = { showDialog.value = false },
        insideMargin = DpSize(0.dp, 24.dp),
        content = {
            SelectionDialogBody(
                items = flags,
                itemTitle = { it.display },
                itemSummary = { stringResource(it.desc) },
                isChecked = { currentSelection.value.contains(it) },
                onToggle = { flag, isChecked ->
                    val newSelection = currentSelection.value.toMutableSet()
                    if (isChecked) newSelection.add(flag) else newSelection.remove(flag)
                    currentSelection.value = newSelection
                },
                onCancel = {
                    currentSelection.value = selected.toSet()
                    showDialog.value = false
                },
                onConfirm = {
                    closeSelection(currentSelection.value.toList())
                    showDialog.value = false
                }
            )
        }
    )

    ArrowPreference(
        enabled = enabled,
        title = stringResource(R.string.profile_flags),
        summary = if (selected.isEmpty()) "None" else selected.joinToString(",") { it.display },
        onClick = { showDialog.value = true }
    )
}

@Composable
private fun <T> SelectionDialogBody(
    items: List<T>,
    itemTitle: @Composable (T) -> String,
    itemSummary: @Composable (T) -> String?,
    isChecked: (T) -> Boolean,
    onToggle: (T, Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.heightIn(max = 500.dp)) {
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(items) { item ->
                val checked = isChecked(item)
                CheckboxPreference(
                    title = itemTitle(item),
                    summary = itemSummary(item),
                    insideMargin = PaddingValues(horizontal = 30.dp, vertical = 16.dp),
                    checkboxLocation = CheckboxLocation.End,
                    checked = checked,
                    holdDownState = checked,
                    onCheckedChange = { onToggle(item, it) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = onCancel,
                text = stringResource(android.R.string.cancel),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(20.dp))
            TextButton(
                onClick = onConfirm,
                text = stringResource(R.string.confirm),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
private fun SELinuxPanelMiuix(
    enabled: Boolean,
    profile: Natives.Profile,
    onSELinuxChange: (domain: String, rules: String) -> Unit
) {
    val showDialog = remember { mutableStateOf(false) }
    var domain by remember(profile.context) { mutableStateOf(profile.context) }
    var rules by remember(profile.rules) { mutableStateOf(profile.rules) }

    SELinuxContextDialogMiuix(
        show = showDialog.value,
        domain = domain,
        rules = rules,
        onDomainChange = { domain = it },
        onRulesChange = { rules = it },
        domainError = domain.isNotEmpty() && !isSELinuxDomainValid(domain),
        rulesError = !isSepolicyValid(rules),
        canConfirm = isSELinuxDomainValid(domain) && isSepolicyValid(rules),
        onConfirm = {
            onSELinuxChange(domain, rules)
            showDialog.value = false
        },
        onDismiss = { showDialog.value = false },
    )

    ArrowPreference(
        enabled = enabled,
        title = stringResource(R.string.profile_selinux_context),
        summary = profile.context,
        onClick = { showDialog.value = true }
    )
}
