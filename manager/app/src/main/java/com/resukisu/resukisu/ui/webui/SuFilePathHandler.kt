package com.resukisu.resukisu.ui.webui

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import androidx.annotation.WorkerThread
import androidx.webkit.WebViewAssetLoader
import com.resukisu.resukisu.ui.webui.MonetColorsProvider.getColorsCss
import com.resukisu.resukisu.ui.webui.SuFilePathHandler.Companion.DEFAULT_MIME_TYPE
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * Handler class to open files from file system by root access
 * For more information about android storage please refer to
 * [Android Developers Docs: Data and file storage overview](https://developer.android.com/guide/topics/data/data-storage).
 *
 * To avoid leaking user or app data to the web, make sure to choose [directory]
 * carefully, and assume any file under this directory could be accessed by any web page subject
 * to same-origin rules.
 *
 * A typical usage would be like:
 * ```
 * val publicDir = File(context.filesDir, "public")
 * // Host "files/public/" in app's data directory under:
 * // http://appassets.androidplatform.net/public/...
 * val assetLoader = WebViewAssetLoader.Builder()
 *     .addPathHandler("/public/", SuFilePathHandler(context, publicDir, shell, insetsSupplier))
 *     .build()
 * ```
 */
class SuFilePathHandler(
    directory: File,
    private val shell: Shell,
    private val insetsSupplier: InsetsSupplier,
    private val onInsetsRequestedListener: ((Boolean) -> Unit)?
) : WebViewAssetLoader.PathHandler {

    private val directory: File

    init {
        try {
            this.directory = File(getCanonicalDirPath(directory))
            if (!isAllowedInternalStorageDir()) {
                throw IllegalArgumentException(
                    "The given directory \"$directory\" doesn't exist under an allowed app internal storage directory"
                )
            }
        } catch (e: IOException) {
            throw IllegalArgumentException(
                "Failed to resolve the canonical path for the given directory: ${directory.path}",
                e
            )
        }
    }

    fun interface InsetsSupplier {
        fun get(): Insets
    }

    private fun isAllowedInternalStorageDir(): Boolean {
        return try {
            val dir = getCanonicalDirPath(directory)
            FORBIDDEN_DATA_DIRS.none { dir.startsWith(it) }
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Opens the requested file from the exposed data directory.
     *
     * The matched prefix path used shouldn't be a prefix of a real web path. Thus, if the
     * requested file cannot be found or is outside the mounted directory a
     * [WebResourceResponse] object with a `null` [InputStream] will be
     * returned instead of `null`. This saves the time of falling back to network and
     * trying to resolve a path that doesn't exist. A [WebResourceResponse] with
     * `null` [InputStream] will be received as an HTTP response with status code
     * `404` and no body.
     *
     * The MIME type for the file will be determined from the file's extension using
     * [java.net.URLConnection.guessContentTypeFromName]. Developers should ensure that
     * files are named using standard file extensions. If the file does not have a
     * recognised extension, `"text/plain"` will be used by default.
     *
     * @param path the suffix path to be handled.
     * @return [WebResourceResponse] for the requested file.
     */
    @WorkerThread
    override fun handle(path: String): WebResourceResponse {
        if (path == "internal/insets.css") {
            onInsetsRequestedListener?.invoke(true)
            val css = insetsSupplier.get().css
            return WebResourceResponse(
                "text/css",
                "utf-8",
                ByteArrayInputStream(css.toByteArray(StandardCharsets.UTF_8))
            )
        }

        if (path == "internal/colors.css") {
            val css = getColorsCss()
            return WebResourceResponse(
                "text/css",
                "utf-8",
                ByteArrayInputStream(css.toByteArray(StandardCharsets.UTF_8))
            )
        }

        try {
            val file = getCanonicalFileIfChild(directory, path)
            if (file != null) {
                val inputStream = openFile(file, shell)
                val mimeType = guessMimeType(path)
                return WebResourceResponse(mimeType, null, inputStream)
            } else {
                Log.e(
                    TAG,
                    "The requested file: $path is outside the mounted directory: $directory"
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error opening the requested path: $path", e)
        }

        return WebResourceResponse(null, null, null)
    }

    companion object {
        private const val TAG = "SuFilePathHandler"

        /**
         * Default value to be used as MIME type if guessing MIME type failed.
         */
        const val DEFAULT_MIME_TYPE = "text/plain"

        /**
         * Forbidden subdirectories of [Context.getDataDir] that cannot be exposed by this
         * handler. They are forbidden as they often contain sensitive information.
         *
         * Note: Any future addition to this list will be considered breaking changes to the API.
         */
        private val FORBIDDEN_DATA_DIRS = arrayOf("/data/data", "/data/system")

        @JvmStatic
        @Throws(IOException::class)
        fun getCanonicalDirPath(file: File): String {
            var canonicalPath = file.canonicalPath
            if (!canonicalPath.endsWith("/")) {
                canonicalPath += "/"
            }
            return canonicalPath
        }

        @JvmStatic
        @Throws(IOException::class)
        fun getCanonicalFileIfChild(parent: File, child: String): File? {
            val parentCanonicalPath = getCanonicalDirPath(parent)
            val childCanonicalPath = File(parent, child).canonicalPath
            return if (childCanonicalPath.startsWith(parentCanonicalPath)) {
                File(childCanonicalPath)
            } else {
                null
            }
        }

        @Throws(IOException::class)
        private fun handleSvgzStream(path: String, stream: InputStream): InputStream {
            return if (path.endsWith(".svgz")) {
                GZIPInputStream(stream)
            } else {
                stream
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun openFile(file: File, shell: Shell): InputStream {
            val suFile = SuFile(file.absolutePath).apply {
                setShell(shell)
            }
            val fis = SuFileInputStream.open(suFile)
            return handleSvgzStream(file.path, fis)
        }

        /**
         * Use [MimeUtil.getMimeFromFileName] to guess MIME type or return the
         * [DEFAULT_MIME_TYPE] if it can't guess.
         *
         * @param filePath path of the file to guess its MIME type.
         * @return MIME type guessed from file extension or [DEFAULT_MIME_TYPE].
         */
        @JvmStatic
        fun guessMimeType(filePath: String): String {
            return MimeUtil.getMimeFromFileName(filePath) ?: DEFAULT_MIME_TYPE
        }
    }
}
