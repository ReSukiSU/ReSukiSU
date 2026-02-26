package com.resukisu.resukisu.ui.util

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import com.resukisu.resukisu.R

enum class InfoCardItem(
    val key: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    KERNEL_VERSION("kernel_version", R.string.home_kernel, Icons.Default.Memory),
    ANDROID_VERSION("android_version", R.string.home_android_version, Icons.Default.Android),
    DEVICE_MODEL("device_model", R.string.home_device_model, Icons.Default.PhoneAndroid),
    MANAGER_VERSION("manager_version", R.string.home_manager_version, Icons.Default.SettingsSuggest),
    HOOK_TYPE("hook_type", R.string.home_hook_type, Icons.Default.Link),
    ACTIVE_MANAGERS("active_managers", R.string.multi_manager_list, Icons.Default.Group),
    SELINUX_STATUS("selinux_status", R.string.home_selinux_status, Icons.Default.Security),
    ZYGISK_IMPLEMENT("zygisk_implement", R.string.home_zygisk_implement, Icons.Default.Adb),
    META_MODULE("meta_module", R.string.home_meta_module_implement, Icons.Default.Extension),
    KPM_VERSION("kpm_version", R.string.home_kpm_version, Icons.Default.Archive),
    SUSFS_VERSION("susfs_version", R.string.home_susfs_version, Icons.Default.Storage),
    LINK_CARDS("link_cards", R.string.hide_link_card, Icons.Default.OpenInNew),
    VERSION_IN_STATUS("version_in_status", R.string.hide_kernel_kernelsu_version, Icons.Default.Info),
    TAG_ROW("tag_row", R.string.hide_tag_card, Icons.Default.Label),
    KERNEL_SIMPLE("kernel_simple", R.string.kernel_simple_kernel, Icons.Default.Compress),
}
