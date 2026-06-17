package com.resukisu.resukisu.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object ThemeSeedColors {
    val Default = Color(0xFF415F91)

    fun fromLegacyName(name: String): Color = when (name.lowercase()) {
        "green" -> Color(0xFF4C662B)
        "purple" -> Color(0xFF7C4E7E)
        "orange" -> Color(0xFF8B4F24)
        "pink" -> Color(0xFF8C4A60)
        "gray" -> Color(0xFF5B5C5C)
        "yellow" -> Color(0xFF6D5E0F)
        else -> Default
    }

    fun fromLegacyNameArgb(name: String): Int = fromLegacyName(name).toArgb()
}
