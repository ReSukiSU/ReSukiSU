package com.resukisu.resukisu.domain.model

sealed interface LkmSelection {
    data class LkmUri(val uri: String) : LkmSelection
    data class KmiString(val value: String) : LkmSelection
    data object KmiNone : LkmSelection
}
