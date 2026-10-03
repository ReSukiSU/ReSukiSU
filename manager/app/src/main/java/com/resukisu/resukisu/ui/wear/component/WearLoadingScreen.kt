package com.resukisu.resukisu.ui.wear.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ArcProgressIndicator
import androidx.wear.compose.material3.ArcProgressIndicatorDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shown inside [androidx.wear.compose.material3.AppScaffold], which already draws the system time.
 * The icon and name sit centered, and the official indeterminate [ArcProgressIndicator] runs along
 * the bottom of the screen at its recommended diameter, as in the Wear progress indicator sample.
 */
@Composable
fun WearLoadingScreen() {
    val context = LocalContext.current
    val settings by koinViewModel<SettingsViewModel>().uiState.collectAsStateWithLifecycle()
    val icon = remember(context, settings.useAltIcon) {
        ContextCompat.getDrawable(context, if (settings.useAltIcon) R.mipmap.ic_launcher_alt else R.mipmap.ic_launcher)
    }
    val loading = stringResource(R.string.wear_loading)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val iconSize = (minOf(maxWidth, maxHeight) * 0.27f).coerceIn(48.dp, 72.dp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Image(rememberDrawablePainter(icon), null, Modifier.size(iconSize))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center)
        }
        ArcProgressIndicator(
            Modifier.align(Alignment.Center).size(ArcProgressIndicatorDefaults.recommendedIndeterminateDiameter)
                .semantics { contentDescription = loading },
        )
    }
}
