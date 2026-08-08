package com.resukisu.resukisu

import android.system.Os

/**
 * @author weishu
 * @date 2022/12/10.
 */

data class KernelVersion(val major: Int, val patchLevel: Int, val subLevel: Int) {
    override fun toString(): String = "$major.$patchLevel.$subLevel"
    fun isGKI(): Boolean = when {
        major > 5 -> true
        major == 5 && patchLevel >= 10 -> true
        else -> false
    }
}

fun parseKernelVersion(version: String): KernelVersion {
    val find = "(\\d+)\\.(\\d+)\\.(\\d+)".toRegex().find(version)
    if (find != null) {
        val major = find.groupValues[1].toIntOrNull()
        val patchLevel = find.groupValues[2].toIntOrNull()
        val subLevel = find.groupValues[3].toIntOrNull()
        if (major != null && patchLevel != null && subLevel != null) {
            return KernelVersion(major, patchLevel, subLevel)
        }
    }
    return KernelVersion(-1, -1, -1)
}

fun getKernelVersion(): KernelVersion {
    Os.uname().release.let {
        return parseKernelVersion(it)
    }
}