package com.resukisu.resukisu.ui.wear.component

import android.app.RemoteInput
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

private const val RemoteInputKey = "wear_text_input"

/**
 * Opens the system Wear text input (keyboard, voice and handwriting) through [RemoteInputIntentHelper],
 * as the Wear input guidance recommends. [onResult] receives the submitted text; canceling returns
 * nothing. [onUnavailable] runs if the system input cannot be started.
 */
@Composable
fun rememberWearRemoteInput(
    label: String,
    onUnavailable: () -> Unit,
    onResult: (String) -> Unit,
): () -> Unit {
    val result by rememberUpdatedState(onResult)
    val unavailable by rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val data = it.data ?: return@rememberLauncherForActivityResult
        RemoteInput.getResultsFromIntent(data)?.getCharSequence(RemoteInputKey)?.let { text -> result(text.toString()) }
    }
    return {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(
            RemoteInput.Builder(RemoteInputKey).setLabel(label).wearableExtender {
                setEmojisAllowed(false)
                setInputActionType(EditorInfo.IME_ACTION_SEARCH)
            }.build(),
        ))
        runCatching { launcher.launch(intent) }.onFailure { unavailable() }
    }
}
