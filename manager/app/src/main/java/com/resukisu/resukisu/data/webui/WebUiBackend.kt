package com.resukisu.resukisu.data.webui

import android.graphics.Bitmap
import com.resukisu.resukisu.data.packageinfo.AppIconDataSource
import com.resukisu.resukisu.data.packageinfo.InstalledPackageRepository
import com.resukisu.resukisu.domain.model.WebUiCommandResult
import com.resukisu.resukisu.domain.model.WebUiProcess
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * What a module WebUI needs from the device that owns the module: root commands, module files and
 * the module and package lists. [LocalWebUiBackend] serves the device itself; the phone uses
 * [RemoteWebUiBackend] to reach a watch's module over the Wear Data Layer.
 */
interface WebUiBackend {
    fun execute(command: String): WebUiCommandResult
    fun spawn(command: String): WebUiProcess
    fun listModules(): String
    fun openFile(path: String): InputStream?

    /** Package names as a JSON array, filtered by `system`, `user` or all. */
    fun listPackages(type: String): String

    /** Package details as a JSON array for a JSON array of package names. */
    fun getPackagesInfo(packageNamesJson: String): String

    /** The app icon as PNG bytes, or null when unavailable. */
    fun iconPng(packageName: String, size: Int): ByteArray?
}

class LocalWebUiBackend(
    private val webUiRepository: WebUiRepository,
    private val packageRepository: InstalledPackageRepository,
    private val appIconDataSource: AppIconDataSource,
) : WebUiBackend {
    override fun execute(command: String) = webUiRepository.execute(command)
    override fun spawn(command: String) = webUiRepository.spawn(command, true)
    override fun listModules() = webUiRepository.listModules()
    override fun openFile(path: String) = webUiRepository.openFile(path)

    override fun listPackages(type: String): String {
        val packageNames = packageRepository.packages.value
            .filter { packageInfo ->
                when (type.lowercase()) {
                    "system" -> packageInfo.isSystem
                    "user" -> !packageInfo.isSystem
                    else -> true
                }
            }
            .map { it.packageName }
            .sorted()
        return JSONArray(packageNames).toString()
    }

    override fun getPackagesInfo(packageNamesJson: String): String {
        val packageNames = JSONArray(packageNamesJson)
        val jsonArray = JSONArray()
        val appMap = packageRepository.packages.value.associateBy { it.packageName }
        for (i in 0 until packageNames.length()) {
            val pkgName = packageNames.getString(i)
            val pkg = appMap[pkgName]
            val obj = JSONObject()
            obj.put("packageName", pkg?.packageName ?: pkgName)
            if (pkg != null) {
                obj.put("versionName", pkg.versionName)
                obj.put("versionCode", pkg.versionCode)
                obj.put("appLabel", pkg.appLabel)
                obj.put("isSystem", pkg.isSystem)
                obj.put("uid", pkg.uid)
            } else {
                obj.put("error", "Package not found or inaccessible")
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    override fun iconPng(packageName: String, size: Int): ByteArray? {
        val icon = appIconDataSource.loadSync(packageName, size) ?: return null
        return ByteArrayOutputStream().also { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
