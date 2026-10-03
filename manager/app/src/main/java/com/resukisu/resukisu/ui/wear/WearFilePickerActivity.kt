package com.resukisu.resukisu.ui.wear

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import com.resukisu.resukisu.ui.wear.component.WearTimeText

/**
 * The app's own document picker for watches without a usable DocumentsUI. It answers the standard
 * SAF intents the app already sends (open, get content and create document) and returns a
 * `content://` URI of [com.resukisu.resukisu.data.file.WearFileProvider]. It is not exported, so
 * only this app can start it, and [withDocumentPickerFallback] routes an intent here only when no
 * system picker can take it or the Wear picker setting asks for the built-in one.
 */
class WearFilePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saving = intent.action == Intent.ACTION_CREATE_DOCUMENT
        val mimeTypes = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.takeIf { it.isNotEmpty() }?.toList()
            ?: listOf(intent.type ?: "*/*")
        val name = intent.getStringExtra(Intent.EXTRA_TITLE).orEmpty()
        setContent {
            WearManagerTheme {
                AppScaffold(timeText = { WearTimeText() }, containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface) {
                    WearFilePage(mimeTypes, saving, name, onBack = { finish() }) { uri ->
                        setResult(RESULT_OK, Intent().setData(uri))
                        finish()
                    }
                }
            }
        }
    }
}

private val DocumentActions = setOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_GET_CONTENT)

/**
 * The SAF [intent] unchanged while a system picker can take it; otherwise the same intent limited
 * to this app, where it resolves to [WearFilePickerActivity] through that activity's intent filter.
 * Some watches resolve these actions only to a framework stub that cannot pick files. [pickerMode]
 * is the Wear picker setting: `builtin` always uses the app's picker and `system` never does.
 */
fun Context.withDocumentPickerFallback(intent: Intent, pickerMode: String?): Intent {
    if (intent.action !in DocumentActions || intent.component != null || intent.`package` != null) return intent
    val useBuiltin = when (pickerMode) {
        "builtin" -> true
        "system" -> false
        else -> packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.name.let { it == null || it.endsWith("DocumentsStub") }
    }
    return if (useBuiltin) Intent(intent).setPackage(packageName) else intent
}
