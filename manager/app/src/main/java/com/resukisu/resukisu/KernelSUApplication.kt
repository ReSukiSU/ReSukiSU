package com.resukisu.resukisu

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.system.Os
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import coil.Coil
import coil.ImageLoader
import com.resukisu.resukisu.data.AppPreferencesRepository
import com.resukisu.resukisu.ui.util.generateMainShellBuilder
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import com.topjohnwu.superuser.internal.MainShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

lateinit var ksuApp: KernelSUApplication

class KernelSUApplication : Application(), ViewModelStoreOwner {

    lateinit var okhttpClient: OkHttpClient
    lateinit var preferencesRepository: AppPreferencesRepository
    val UserAgent = "ReSukiSU/${BuildConfig.VERSION_CODE}"
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val appViewModelStore by lazy { ViewModelStore() }
    private var preferencesRepositoryStarted = false

    fun ensurePreferencesRepository(): AppPreferencesRepository {
        if (!::preferencesRepository.isInitialized) {
            preferencesRepository = AppPreferencesRepository(this)
            runBlocking(Dispatchers.IO) {
                preferencesRepository.preload()
            }
        }

        if (!preferencesRepositoryStarted) {
            preferencesRepository.start(applicationScope)
            preferencesRepositoryStarted = true
        }

        return preferencesRepository
    }

    @SuppressLint("RestrictedApi")
    override fun onCreate() {
        super.onCreate()
        ksuApp = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = getProcessName()
            if (processName.endsWith("MagicaService")) {
                // avoid loading unnecessary thing when starting MagicaService
                return
            }
        }

        MainShell.setBuilder(generateMainShellBuilder())

        ensurePreferencesRepository()

        // For faster response when first entering superuser or webui activity
        val superUserViewModel = ViewModelProvider(this)[SuperUserViewModel::class.java]
        val homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        val moduleViewModel = ViewModelProvider(this)[ModuleViewModel::class.java]
        val settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        applicationScope.launch {
            settingsViewModel.initialize(this@KernelSUApplication)
            homeViewModel.refreshData(this@KernelSUApplication)
            superUserViewModel.fetchAppList()
            moduleViewModel.fetchModuleList()
        }

        val context = this
        val iconSize = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(iconSize, false, context))
                }
                .build()
        )

        val webroot = File(dataDir, "webroot")
        if (!webroot.exists()) {
            webroot.mkdir()
        }

        // Provide working env for rust's temp_dir()
        Os.setenv("TMPDIR", cacheDir.absolutePath, true)

        okhttpClient =
            OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", UserAgent)
                            .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                    )
                }
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build()
    }
    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}
