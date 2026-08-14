package com.resukisu.resukisu.data.file

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.resukisu.resukisu.R
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ModuleUtils {
    private companion object {
        const val TAG = "ModuleUtils"
    }

    /**
     * Extracts the module name from a module ZIP file.
     *
     * Resolution order:
     * 1. Attempts to read the `name` field from `module.prop` file
     * 2. Falls back to the ZIP file name
     *
     * @param context Context used to access the [ContentResolver] and fallback string
     * @param uri Uri pointing to the module ZIP file
     * @return The extracted module name, or a fallback name if extraction fails
     */
    fun extractModuleName(context: Context, uri: Uri): String {
        if (uri == Uri.EMPTY) {
            Log.e(TAG, "The supplied URI is empty")
            return context.getString(R.string.unknown_module)
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry: ZipEntry?

                    while (zip.nextEntry.also { entry = it } != null) {
                        if (entry?.name == "module.prop") {
                            val prop = Properties()
                            prop.load(zip)

                            val name = prop.getProperty("name")
                            if (!name.isNullOrBlank()) {
                                return name.replace(
                                    Regex("[^a-zA-Z0-9\\s\\-_.@()\\u4e00-\\u9fa5]"),
                                    ""
                                ).trim()
                            }
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting module name: ${e.message}")
        }

        val fallback = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.removeSuffix(".zip")
            ?.replace(
                Regex("[^a-zA-Z0-9\\s\\-_.@()\\u4e00-\\u9fa5]"),
                ""
            )
            ?.trim()
            ?: context.getString(R.string.unknown_module)

        return fallback
    }

    /**
     * Checks whether the given Uri is accessible and readable.
     *
     * This method attempts to open an InputStream from the Uri.
     *
     * @param context Context used to access the [ContentResolver]
     * @param uri The Uri to be checked
     * @return `true` if the Uri can be opened, `false` otherwise
     */
    fun isUriAccessible(context: Context, uri: Uri): Boolean {
        if (uri == Uri.EMPTY) return false

        return try {
            context.contentResolver.openInputStream(uri)?.use {} != null
        } catch (e: Exception) {
            Log.e(TAG, "URI is inaccessible: $uri", e)
            false
        }
    }

    /**
     * Requests persistable read permission for the given Uri.
     *
     * This allows the app to retain access to the Uri across device reboots.
     *
     * @param context Context used to access the [ContentResolver]
     * @param uri The Uri for which persistable permission is requested
     */
    fun takePersistableUriPermission(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            Log.d(TAG, "Persistent permissions for URIs have been obtained: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get persistent permissions on URIs: $uri, Error: ${e.message}")
        }
    }

    /**
     * Extracts the module ID from a module ZIP file.
     *
     * This method read the `id` field from `module.prop` file
     *
     * @param context Context used to access the [ContentResolver]
     * @param uri Uri pointing to the module ZIP file
     * @return The module ID if found, or `null` if extraction fails
     */
    fun extractModuleId(context: Context, uri: Uri): String? {
        if (uri == Uri.EMPTY) return null

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry: ZipEntry?

                while (zip.nextEntry.also { entry = it } != null) {
                    if (entry?.name == "module.prop") {
                        val prop = Properties()
                        prop.load(zip)
                        return prop.getProperty("id")
                    }
                }
            }
        }

        return null
    }
}
