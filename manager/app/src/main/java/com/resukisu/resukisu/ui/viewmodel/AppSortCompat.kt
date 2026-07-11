// App sort model from tiann/KernelSU (github.com/tiann/KernelSU), used by the ported
// Miuix SuperUser screen. Kept additively alongside ReSukiSU/ReSukiSU's own SortType so
// the Miuix UI compiles. GPL-3.0. See docs/ATTRIBUTION.md.
package com.resukisu.resukisu.ui.viewmodel

enum class AppSortType {
    NAME, PACKAGE_NAME, INSTALL_TIME, UPDATE_TIME;

    companion object {
        fun fromOrdinal(ordinal: Int): AppSortType =
            entries.getOrElse(ordinal) { NAME }
    }
}

data class AppSortConfig(
    val sortType: AppSortType = AppSortType.NAME,
    val reversed: Boolean = false,
) {
    fun toInt(): Int = sortType.ordinal * 2 + if (reversed) 1 else 0

    fun withType(type: AppSortType): AppSortConfig = copy(sortType = type)
    fun toggleReversed(): AppSortConfig = copy(reversed = !reversed)

    companion object {
        fun fromInt(value: Int): AppSortConfig = AppSortConfig(
            sortType = AppSortType.fromOrdinal(value / 2),
            reversed = value % 2 != 0,
        )
    }
}
