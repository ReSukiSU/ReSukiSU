// From tiann/KernelSU (github.com/tiann/KernelSU) ui/util/KsuCli.kt — the FlashResult
// type and Uri.getFileName helper the ported Miuix flash/module screens use. Kept in a
// separate file to avoid touching ReSukiSU's own KsuCli.kt. GPL-3.0. See docs/ATTRIBUTION.md.
package com.resukisu.resukisu.ui.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.topjohnwu.superuser.Shell

data class FlashResult(val code: Int, val err: String, val showReboot: Boolean) {
    constructor(result: Shell.Result, showReboot: Boolean) : this(result.code, result.err.joinToString("\n"), showReboot)
    constructor(result: Shell.Result) : this(result, result.isSuccess)
}

fun Uri.getFileName(context: Context): String? {
    var fileName: String? = null
    val contentResolver: ContentResolver = context.contentResolver
    val cursor: Cursor? = contentResolver.query(this, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }
    return fileName
}
