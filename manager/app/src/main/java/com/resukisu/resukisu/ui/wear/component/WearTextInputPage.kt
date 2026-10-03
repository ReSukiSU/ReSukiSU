package com.resukisu.resukisu.ui.wear.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** Uses the watch IME, including its voice input, without importing a phone text-field control. */
@Composable
fun WearTextInputPage(title: String, initialValue: String, multiline: Boolean = false, onSubmit: (String) -> Unit) {
    var value by rememberSaveable(title, initialValue) { mutableStateOf(initialValue) }
    // Confirming is the page's primary action, so it takes the edge button; Back and swipe dismiss it.
    WearList(onConfirm = { onSubmit(value) }) { spec ->
        item { WearPageHeader(spec, title) }
        item {
            WearScaledItem(spec) {
                BasicTextField(
                    value = value, onValueChange = { value = it }, singleLine = !multiline,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit(value) }),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                        .padding(12.dp).semantics { contentDescription = title },
                    decorationBox = { field ->
                        if (value.isEmpty()) Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        field()
                    },
                )
            }
        }
    }
}
