package com.resukisu.resukisu.domain.text

fun interface TextTransliterator {
    fun transliterate(value: String): String
}
