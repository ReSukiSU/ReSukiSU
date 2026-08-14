package com.resukisu.resukisu.data.packageinfo

import android.content.pm.PackageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InstalledPackageCache {
    private val mutablePackages = MutableStateFlow<List<PackageInfo>>(emptyList())
    val packages: StateFlow<List<PackageInfo>> = mutablePackages.asStateFlow()

    fun replace(packages: List<PackageInfo>) {
        mutablePackages.value = packages.toList()
    }

    fun find(packageName: String): PackageInfo? =
        mutablePackages.value.firstOrNull { it.packageName == packageName }
}

