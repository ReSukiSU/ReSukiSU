package com.resukisu.resukisu.data.shell

import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream

/** Keeps libsu access out of the Compose shortcut helper. */
class ShortcutRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    fun openRootFile(path: String) = runCatching {
        val file = SuFile(path).apply { shell = ksuCliRepository.getRootShell(true) }
        SuFileInputStream.open(file)
    }.getOrNull()

    fun grantShortcutPermission(packageName: String): Boolean = runCatching {
        val result = ksuCliRepository.getRootShell().newJob()
            .add("appops set $packageName 10017 allow")
            .exec()
        result.isSuccess
    }.getOrDefault(false)
}
