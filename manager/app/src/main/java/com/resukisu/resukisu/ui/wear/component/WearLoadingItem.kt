package com.resukisu.resukisu.ui.wear.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.lazy.TransformationSpec
import com.resukisu.resukisu.R

/**
 * The loading state of a list, as the phone shows a centered LoadingIndicator in place of its
 * content: an indeterminate progress indicator in its own list slot below the title and actions,
 * so it never covers other items.
 */
fun TransformingLazyColumnScope.wearLoadingItem(transformationSpec: TransformationSpec) {
    item(key = "wear-loading", contentType = "wear-loading") {
        val loading = stringResource(R.string.wear_loading)
        WearScaledItem(transformationSpec, Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            // The official indeterminate size: a 24dp diameter with a 3dp stroke.
            CircularProgressIndicator(Modifier.align(Alignment.Center).semantics { contentDescription = loading })
        }
    }
}
