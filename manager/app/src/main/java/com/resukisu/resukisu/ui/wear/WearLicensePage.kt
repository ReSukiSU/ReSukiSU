package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Copyright
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Text
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withJson
import com.mikepenz.aboutlibraries.ui.compose.util.author
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.wear.component.WearActionButton
import com.resukisu.resukisu.ui.wear.component.WearInfoCard
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearLicensePage(onBack: () -> Unit, onOpenLink: (String) -> Unit) {
    val context = LocalContext.current
    val libraries by produceState<Libs?>(null, context) {
        value = withContext(Dispatchers.IO) { Libs.Builder().withJson(context, R.raw.aboutlibraries).build() }
    }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    WearList(isLoading = libraries == null, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.open_source_license)) }
        libraries?.libraries?.forEachIndexed { index, library ->
            item(key = "library-$index") {
                WearActionButton(spec, Icons.TwoTone.Copyright, library.name, { selected = if (selected == index) -1 else index })
            }
            if (selected == index) {
                item { WearInfoCard(spec) {
                    Text(library.author)
                    library.artifactVersion?.let { Text(it) }
                } }
                library.licenses.forEach { license ->
                    item { WearActionButton(spec, Icons.TwoTone.Copyright, license.name,
                        { license.url?.let(onOpenLink) }, license.url != null) }
                    val text = license.licenseContent
                    if (text == null) item { WearInfoCard(spec) { Text(stringResource(R.string.no_license_text)) } }
                    else text.chunked(350).forEach { chunk -> item { WearInfoCard(spec) { Text(chunk) } } }
                }
            }
        }
    }
}
