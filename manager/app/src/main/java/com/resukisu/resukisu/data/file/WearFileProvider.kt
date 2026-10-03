package com.resukisu.resukisu.data.file

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import com.resukisu.resukisu.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.FileNotFoundException

/**
 * Serves the files picked in the app's own document picker as `content://` URIs, so the existing
 * ContentResolver-based callers read and write them as they do with DocumentsUI results. The files
 * are reached with root through [WearFileRepository]: reads are staged first, and writes go to a
 * staging file that is copied to the target when the caller closes it. The provider is not
 * exported and grants no URI permissions, so only this app can use it.
 */
class WearFileProvider : ContentProvider(), KoinComponent {
    private val files: WearFileRepository by inject()

    override fun onCreate() = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        val columns = projection?.filter { it == OpenableColumns.DISPLAY_NAME }?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME)
        return MatrixCursor(columns).apply { addRow(columns.map { uri.lastPathSegment }) }
    }

    override fun getType(uri: Uri) = uri.lastPathSegment?.let(::wearFileMimeType)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val path = uri.path ?: throw FileNotFoundException(uri.toString())
        val name = uri.lastPathSegment ?: throw FileNotFoundException(uri.toString())
        if ('w' !in mode && 'a' !in mode) {
            val staged = runCatching { runBlocking { files.stage(path) } }
                .getOrElse { throw FileNotFoundException(it.message) }
            return ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        val staged = files.stagingFile(name)
        return ParcelFileDescriptor.open(staged, ParcelFileDescriptor.parseMode(mode), Handler(Looper.getMainLooper())) { error ->
            if (error != null) staged.delete()
            else commitScope.launch {
                runCatching { files.commit(staged, path) }.onFailure { Log.e("WearFileProvider", "Failed to write $path", it) }
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.wearfiles"
        private val commitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun uriFor(path: String): Uri = Uri.Builder().scheme("content").authority(AUTHORITY).path(path).build()
    }
}
