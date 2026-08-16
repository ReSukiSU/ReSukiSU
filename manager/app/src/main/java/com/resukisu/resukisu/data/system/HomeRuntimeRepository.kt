package com.resukisu.resukisu.data.system

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.system.Os
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.domain.model.HomeBasicInfo
import com.resukisu.resukisu.domain.model.HomeModuleOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeRuntimeRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    suspend fun getBasicInfo(managerUapiVersion: Int): HomeBasicInfo =
        withContext(Dispatchers.IO) {
            val uname = runCatching { Os.uname() }.getOrNull()
            HomeBasicInfo(
                kernelRelease = uname?.release ?: "Unknown",
                androidVersion = Build.VERSION.RELEASE ?: "Unknown",
                deviceModel = getDeviceModel(),
                managerVersion = Triple(
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                    managerUapiVersion,
                ),
                selinuxStatus = runCatching { getSELinuxStatus(application) }.getOrDefault("Unknown"),
                seccompStatus = runCatching { Os.prctl(21, 0, 0, 0, 0) }.getOrDefault(-1),
            )
        }

    suspend fun getModuleOverview(): HomeModuleOverview = withContext(Dispatchers.IO) {
        HomeModuleOverview(
            count = runCatching { ksuCliRepository.getModuleCount() }.getOrDefault(0),
            zygiskImplementation = runCatching {
                ksuCliRepository.getZygiskImplement()
            }.getOrDefault("None"),
            metaModuleImplementation = runCatching {
                ksuCliRepository.getMetaModuleImplement()
            }.getOrDefault("None"),
        )
    }

    suspend fun getSuperuserCount(): Int = withContext(Dispatchers.IO) {
        runCatching { ksuCliRepository.getSuperuserCount() }.getOrDefault(0)
    }

    @SuppressLint("PrivateApi")
    private fun getDeviceModel(): String = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
        val marketNameKeys = listOf(
            "ro.product.marketname",
            "ro.vendor.oplus.market.name",
            "ro.vivo.market.name",
            "ro.config.marketing_name",
        )
        marketNameKeys.firstNotNullOfOrNull { key ->
            (getMethod.invoke(null, key, "") as? String)?.takeIf(String::isNotEmpty)
        } ?: getDeviceInfo()
    }.getOrDefault(getDeviceInfo())

    private fun getDeviceInfo(): String = runCatching {
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val brand = Build.BRAND.orEmpty().takeUnless {
            it.equals(Build.MANUFACTURER, ignoreCase = true)
        }.orEmpty()
        listOf(manufacturer, brand, Build.MODEL.orEmpty())
            .filter(String::isNotBlank)
            .joinToString(" ")
    }.getOrDefault("Unknown Device")
}
