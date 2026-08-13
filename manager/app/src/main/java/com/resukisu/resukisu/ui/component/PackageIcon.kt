package com.resukisu.resukisu.ui.component

import android.content.pm.PackageInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.resukisu.resukisu.data.packageinfo.AppIconDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** Resolves a package name to the [PackageInfo] model expected by AppIconFetcher. */
@Composable
fun PackageIcon(
    packageName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconDataSource = koinInject<AppIconDataSource>()
    val packageInfo by produceState<PackageInfo?>(
        initialValue = iconDataSource.findCachedPackageInfo(packageName),
        packageName,
        iconDataSource,
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                iconDataSource.loadPackageInfo(packageName)
            }
        }
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(packageInfo)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
