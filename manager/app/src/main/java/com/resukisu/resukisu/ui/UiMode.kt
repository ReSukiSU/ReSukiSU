package com.resukisu.resukisu.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material");

    companion object {
        fun fromValue(value: String): UiMode = when (value) {
            Miuix.value -> Miuix
            else -> Material
        }

        val DEFAULT_VALUE = Material.value
    }
}

// Default to ReSukiSU (Material) design; MainActivity provides the value from settings.
val LocalUiMode = staticCompositionLocalOf { UiMode.Material }
