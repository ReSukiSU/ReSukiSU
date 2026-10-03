package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.resukisu.resukisu.domain.usecase.GetStringPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.SetStringPreferenceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */
data class WearPreferences(val picker: String = "auto", val link: String = "auto", val shape: String = "auto")
class WearPreferencesViewModel(getPreference: GetStringPreferenceUseCase, private val setPreference: SetStringPreferenceUseCase) : ViewModel() {
    private val mutableState = MutableStateFlow(WearPreferences(
        getPreference("wear_file_picker", "auto").orEmpty(), getPreference("wear_link_mode", "auto").orEmpty().let { if (it in LinkModes) it else "auto" },
        getPreference("wear_screen_shape", "auto").orEmpty(),
    ))
    val state = mutableState.asStateFlow()
    fun setPicker(mode: String) {
        setPreference("wear_file_picker", mode)
        mutableState.update { it.copy(picker = mode) }
    }
    fun setLink(mode: String) {
        setPreference("wear_link_mode", mode)
        mutableState.update { it.copy(link = mode) }
    }
    fun setShape(shape: String) {
        setPreference("wear_screen_shape", shape)
        mutableState.update { it.copy(shape = shape) }
    }
}

/** The saved link modes; the former "browser" mode was removed and falls back to automatic. */
private val LinkModes = setOf("auto", "webview", "phone")
