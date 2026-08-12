package com.resukisu.resukisu.data.file

import android.app.Application
import androidx.core.net.toUri

class ModuleFileRepository(
    private val application: Application,
) {
    private val moduleUtils = ModuleUtils()

    fun isUriAccessible(uri: String): Boolean =
        moduleUtils.isUriAccessible(application, uri.toUri())

    fun takePersistableUriPermission(uri: String) {
        moduleUtils.takePersistableUriPermission(application, uri.toUri())
    }

    fun extractModuleName(uri: String): String =
        moduleUtils.extractModuleName(application, uri.toUri())

    fun extractModuleId(uri: String): String? =
        moduleUtils.extractModuleId(application, uri.toUri())
}
