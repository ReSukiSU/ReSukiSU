package com.resukisu.resukisu.di

import coil.ImageLoader
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.data.AppSettingsRepository
import com.resukisu.resukisu.data.application.ApplicationControlRepository
import com.resukisu.resukisu.data.application.DynamicManagerRepository
import com.resukisu.resukisu.data.download.DownloadRepository
import com.resukisu.resukisu.data.file.ModuleFileRepository
import com.resukisu.resukisu.data.flash.FlashRepository
import com.resukisu.resukisu.data.kernel.KernelRepository
import com.resukisu.resukisu.data.kernel.UmountRepository
import com.resukisu.resukisu.data.logging.BugreportRepository
import com.resukisu.resukisu.data.logging.SulogRepository
import com.resukisu.resukisu.data.module.ModuleActionRepository
import com.resukisu.resukisu.data.module.ModuleCatalogRepository
import com.resukisu.resukisu.data.module.ModulePreferencesRepository
import com.resukisu.resukisu.data.module.ModuleRepository
import com.resukisu.resukisu.data.network.NetworkRequestRepository
import com.resukisu.resukisu.data.network.NetworkStatusRepository
import com.resukisu.resukisu.data.network.WebResourceRepository
import com.resukisu.resukisu.data.packageinfo.AppIconDataSource
import com.resukisu.resukisu.data.packageinfo.InstalledPackageCache
import com.resukisu.resukisu.data.packageinfo.InstalledPackageRepository
import com.resukisu.resukisu.data.packageinfo.RootServiceRepository
import com.resukisu.resukisu.data.packageinfo.SuperUserRepository
import com.resukisu.resukisu.data.profile.ProfileRepository
import com.resukisu.resukisu.data.profile.ProfileTemplateRepository
import com.resukisu.resukisu.data.settings.LocaleHelper
import com.resukisu.resukisu.data.settings.LocaleRepository
import com.resukisu.resukisu.data.settings.SettingsPlatformRepository
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.data.shell.ShortcutRepository
import com.resukisu.resukisu.data.startup.ApplicationInitializationRepository
import com.resukisu.resukisu.data.startup.StartupRepository
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.data.susfs.SuSFSRepository
import com.resukisu.resukisu.data.system.HomeRuntimeRepository
import com.resukisu.resukisu.data.system.HomeStateRepository
import com.resukisu.resukisu.data.text.HanziToPinyin
import com.resukisu.resukisu.data.theme.MonetCompatColorSource
import com.resukisu.resukisu.data.theme.ThemeRepository
import com.resukisu.resukisu.data.update.ManagerUpdateRepository
import com.resukisu.resukisu.data.webui.WebUiRepository
import com.resukisu.resukisu.domain.text.TextTransliterator
import com.resukisu.resukisu.domain.usecase.AddUmountPathUseCase
import com.resukisu.resukisu.domain.usecase.ApplyLanguageUseCase
import com.resukisu.resukisu.domain.usecase.BackupAllowlistUseCase
import com.resukisu.resukisu.domain.usecase.CalculateInstalledModuleSizeUseCase
import com.resukisu.resukisu.domain.usecase.CheckFlashModuleMountUseCase
import com.resukisu.resukisu.domain.usecase.CheckManagerUpdateUseCase
import com.resukisu.resukisu.domain.usecase.CleanSulogUseCase
import com.resukisu.resukisu.domain.usecase.ClearDynamicManagerUseCase
import com.resukisu.resukisu.domain.usecase.ConfigureSuLogUseCase
import com.resukisu.resukisu.domain.usecase.ControlAppUseCase
import com.resukisu.resukisu.domain.usecase.DeleteProfileTemplateUseCase
import com.resukisu.resukisu.domain.usecase.EnableSulogUseCase
import com.resukisu.resukisu.domain.usecase.EnqueueDownloadUseCase
import com.resukisu.resukisu.domain.usecase.EnqueueManagerUpdateUseCase
import com.resukisu.resukisu.domain.usecase.EnsureManagerInstalledUseCase
import com.resukisu.resukisu.domain.usecase.ExecuteFlashOperationUseCase
import com.resukisu.resukisu.domain.usecase.ExecuteModuleActionUseCase
import com.resukisu.resukisu.domain.usecase.ExportProfileTemplatesUseCase
import com.resukisu.resukisu.domain.usecase.ExtractModuleIdUseCase
import com.resukisu.resukisu.domain.usecase.ExtractModuleNameUseCase
import com.resukisu.resukisu.domain.usecase.FetchRemoteTextUseCase
import com.resukisu.resukisu.domain.usecase.GenerateBugreportUseCase
import com.resukisu.resukisu.domain.usecase.GetAppProfileUseCase
import com.resukisu.resukisu.domain.usecase.GetAppSepolicyUseCase
import com.resukisu.resukisu.domain.usecase.GetBooleanPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.GetCatalogModuleUseCase
import com.resukisu.resukisu.domain.usecase.GetDefaultUmountModulesUseCase
import com.resukisu.resukisu.domain.usecase.GetHomeBasicInfoUseCase
import com.resukisu.resukisu.domain.usecase.GetHomeModuleOverviewUseCase
import com.resukisu.resukisu.domain.usecase.GetHomeSuperuserCountUseCase
import com.resukisu.resukisu.domain.usecase.GetInstallEnvironmentUseCase
import com.resukisu.resukisu.domain.usecase.GetKernelFeatureSettingsUseCase
import com.resukisu.resukisu.domain.usecase.GetKernelStatusUseCase
import com.resukisu.resukisu.domain.usecase.GetManagerRuntimeInfoUseCase
import com.resukisu.resukisu.domain.usecase.GetPlatformFeatureStatusUseCase
import com.resukisu.resukisu.domain.usecase.GetProfileTemplateUseCase
import com.resukisu.resukisu.domain.usecase.GetStringPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.GetStringSetPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.GetSuSFSStatusUseCase
import com.resukisu.resukisu.domain.usecase.GetSuperUserAppGroupUseCase
import com.resukisu.resukisu.domain.usecase.ImportAllowlistUseCase
import com.resukisu.resukisu.domain.usecase.ImportProfileTemplatesUseCase
import com.resukisu.resukisu.domain.usecase.InitializeApplicationUseCase
import com.resukisu.resukisu.domain.usecase.IsLateLoadModeUseCase
import com.resukisu.resukisu.domain.usecase.IsModuleUriAccessibleUseCase
import com.resukisu.resukisu.domain.usecase.IsNetworkAvailableUseCase
import com.resukisu.resukisu.domain.usecase.IsSystemLanguageSettingsUseCase
import com.resukisu.resukisu.domain.usecase.LaunchSystemLanguageSettingsUseCase
import com.resukisu.resukisu.domain.usecase.LoadSettingsPlatformUseCase
import com.resukisu.resukisu.domain.usecase.ObserveCatalogModulesUseCase
import com.resukisu.resukisu.domain.usecase.ObserveDownloadUseCase
import com.resukisu.resukisu.domain.usecase.ObserveDynamicManagerStateUseCase
import com.resukisu.resukisu.domain.usecase.ObserveInstalledModulesUseCase
import com.resukisu.resukisu.domain.usecase.ObserveKernelFlashUseCase
import com.resukisu.resukisu.domain.usecase.ObserveModuleCatalogOfflineUseCase
import com.resukisu.resukisu.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import com.resukisu.resukisu.domain.usecase.ObserveProfileTemplateOfflineUseCase
import com.resukisu.resukisu.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import com.resukisu.resukisu.domain.usecase.ObserveProfileTemplatesUseCase
import com.resukisu.resukisu.domain.usecase.ObserveStartupStateUseCase
import com.resukisu.resukisu.domain.usecase.ObserveSulogStateUseCase
import com.resukisu.resukisu.domain.usecase.ObserveSuperUserStateUseCase
import com.resukisu.resukisu.domain.usecase.ObserveUmountStateUseCase
import com.resukisu.resukisu.domain.usecase.RebootUseCase
import com.resukisu.resukisu.domain.usecase.RefreshDynamicManagerUseCase
import com.resukisu.resukisu.domain.usecase.RefreshInstalledModulesUseCase
import com.resukisu.resukisu.domain.usecase.RefreshModuleCatalogUseCase
import com.resukisu.resukisu.domain.usecase.RefreshProfileTemplatesUseCase
import com.resukisu.resukisu.domain.usecase.RefreshSulogUseCase
import com.resukisu.resukisu.domain.usecase.RefreshSuperUsersUseCase
import com.resukisu.resukisu.domain.usecase.RefreshUmountPathsUseCase
import com.resukisu.resukisu.domain.usecase.RemovePreferenceUseCase
import com.resukisu.resukisu.domain.usecase.RemoveUmountPathUseCase
import com.resukisu.resukisu.domain.usecase.SaveModuleActionLogUseCase
import com.resukisu.resukisu.domain.usecase.SaveProfileTemplateUseCase
import com.resukisu.resukisu.domain.usecase.SelectDynamicManagerUseCase
import com.resukisu.resukisu.domain.usecase.SetAppProfileUseCase
import com.resukisu.resukisu.domain.usecase.SetAppSepolicyUseCase
import com.resukisu.resukisu.domain.usecase.SetBooleanPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.SetDefaultUmountModulesUseCase
import com.resukisu.resukisu.domain.usecase.SetKernelUmountEnabledUseCase
import com.resukisu.resukisu.domain.usecase.SetManualDynamicManagerUseCase
import com.resukisu.resukisu.domain.usecase.SetModuleEnabledUseCase
import com.resukisu.resukisu.domain.usecase.SetModuleRemovedUseCase
import com.resukisu.resukisu.domain.usecase.SetSelinuxHideEnabledUseCase
import com.resukisu.resukisu.domain.usecase.SetStringPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.SetStringSetPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.SetSuEnabledUseCase
import com.resukisu.resukisu.domain.usecase.StartKernelFlashUseCase
import com.resukisu.resukisu.domain.usecase.SuSFSConfigUseCase
import com.resukisu.resukisu.domain.usecase.TakeModuleUriPermissionUseCase
import com.resukisu.resukisu.domain.usecase.TransliterateTextUseCase
import com.resukisu.resukisu.domain.usecase.UpdateAppearanceUseCase
import com.resukisu.resukisu.domain.usecase.UpdateCachedModuleEnabledUseCase
import com.resukisu.resukisu.domain.usecase.UpdatePlatformSettingUseCase
import com.resukisu.resukisu.domain.usecase.ValidateSepolicyUseCase
import com.resukisu.resukisu.ui.activity.util.ThemeUtils
import com.resukisu.resukisu.ui.component.ZipFileDetector
import com.resukisu.resukisu.ui.theme.BackgroundManager
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.util.module.Shortcut
import com.resukisu.resukisu.ui.viewmodel.AppProfileViewModel
import com.resukisu.resukisu.ui.viewmodel.DynamicManagerViewModel
import com.resukisu.resukisu.ui.viewmodel.ExecuteModuleActionViewModel
import com.resukisu.resukisu.ui.viewmodel.FlashViewModel
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import com.resukisu.resukisu.ui.viewmodel.InstallViewModel
import com.resukisu.resukisu.ui.viewmodel.KernelFlashViewModel
import com.resukisu.resukisu.ui.viewmodel.MainIntentViewModel
import com.resukisu.resukisu.ui.viewmodel.ModuleDetailViewModel
import com.resukisu.resukisu.ui.viewmodel.ModuleRepoViewModel
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import com.resukisu.resukisu.ui.viewmodel.SuSFSViewModel
import com.resukisu.resukisu.ui.viewmodel.SulogViewModel
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import com.resukisu.resukisu.ui.viewmodel.TemplateEditorViewModel
import com.resukisu.resukisu.ui.viewmodel.TemplateViewModel
import com.resukisu.resukisu.ui.viewmodel.UmountManagerScreenViewModel
import com.resukisu.resukisu.ui.webui.MonetColorsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

val applicationScopeQualifier = named("applicationScope")

val coreModule = module {
    single<CoroutineScope>(applicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    single {
        OkHttpClient.Builder()
            .cache(Cache(File(androidApplication().cacheDir, "okhttp"), 10L * 1024L * 1024L))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "ReSukiSU/${BuildConfig.VERSION_CODE}")
                        .header("Accept-Language", Locale.getDefault().toLanguageTag())
                        .build()
                )
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    single {
        val application = androidApplication()
        val iconSize = application.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        ImageLoader.Builder(application)
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(iconSize, false, application))
            }
            .build()
    }
}

val repositoryModule = module {
    single { KsuCliRepository(androidApplication()) }
    singleOf(::InstalledPackageCache)
    singleOf(::AppIconDataSource)
    singleOf(::RootServiceRepository)
    singleOf(::InstalledPackageRepository)
    single {
        SuperUserRepository(
            application = get(),
            cache = get(),
            installedPackageRepository = get(),
            profileRepository = get(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    single {
        AppSettingsRepository(
            context = androidApplication(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    singleOf(::StartupRepository)
    single {
        ApplicationInitializationRepository(
            application = get(),
            imageLoader = get(),
            applicationScope = get(applicationScopeQualifier),
            flashRepository = get(),
            ksuCliRepository = get(),
            monetCompatColorSource = get(),
        )
    }
    singleOf(::ManagerUpdateRepository)
    singleOf(::ApplicationControlRepository)
    singleOf(::DownloadRepository)
    single { FlashRepository(get(), get(applicationScopeQualifier), get(), get()) }
    singleOf(::KernelRepository)
    singleOf(::HomeRuntimeRepository)
    singleOf(::HomeStateRepository)
    singleOf(::NetworkStatusRepository)
    singleOf(::NetworkRequestRepository)
    singleOf(::DynamicManagerRepository)
    singleOf(::SulogRepository)
    singleOf(::BugreportRepository)
    singleOf(::UmountRepository)
    singleOf(::ModuleCatalogRepository)
    singleOf(::ModuleRepository)
    singleOf(::ModulePreferencesRepository)
    singleOf(::ModuleActionRepository)
    singleOf(::WebResourceRepository)
    singleOf(::WebUiRepository)
    singleOf(::ModuleFileRepository)
    singleOf(::ProfileRepository)
    singleOf(::ProfileTemplateRepository)
    singleOf(::SuSFSConfigHelper)
    singleOf(::SuSFSRepository)
    singleOf(::MonetCompatColorSource)
    singleOf(::ThemeRepository)
    single {
        val themeRepository = get<ThemeRepository>()
        ThemeConfig(themeRepository::defaultSeedColor)
    }
    singleOf(::CardConfig)
    singleOf(::BackgroundManager)
    singleOf(::ThemeUtils)
    singleOf(::LocaleHelper)
    singleOf(::LocaleRepository)
    singleOf(::SettingsPlatformRepository)
    singleOf(::ShortcutRepository)
    singleOf(::Shortcut)
    singleOf(::MonetColorsProvider)
    singleOf(::ZipFileDetector)
    single { HanziToPinyin.create() } bind TextTransliterator::class
}

val useCaseModule = module {
    factoryOf(::InitializeApplicationUseCase)
    factoryOf(::GetHomeBasicInfoUseCase)
    factoryOf(::GetHomeModuleOverviewUseCase)
    factoryOf(::GetHomeSuperuserCountUseCase)
    factoryOf(::IsNetworkAvailableUseCase)
    factoryOf(::LoadSettingsPlatformUseCase)
    factoryOf(::UpdateAppearanceUseCase)
    factoryOf(::UpdatePlatformSettingUseCase)
    factoryOf(::GetPlatformFeatureStatusUseCase)
    factoryOf(::CheckManagerUpdateUseCase)
    factoryOf(::EnsureManagerInstalledUseCase)
    factoryOf(::RebootUseCase)
    factoryOf(::EnqueueDownloadUseCase)
    factoryOf(::EnqueueManagerUpdateUseCase)
    factoryOf(::ObserveDownloadUseCase)
    factoryOf(::GetKernelStatusUseCase)
    factoryOf(::GetInstallEnvironmentUseCase)
    factoryOf(::ExecuteFlashOperationUseCase)
    factoryOf(::CheckFlashModuleMountUseCase)
    factoryOf(::GetManagerRuntimeInfoUseCase)
    factoryOf(::GetKernelFeatureSettingsUseCase)
    factoryOf(::SetSuEnabledUseCase)
    factoryOf(::SetKernelUmountEnabledUseCase)
    factoryOf(::ConfigureSuLogUseCase)
    factoryOf(::SetSelinuxHideEnabledUseCase)
    factoryOf(::SetDefaultUmountModulesUseCase)
    factoryOf(::IsLateLoadModeUseCase)
    factoryOf(::GetAppProfileUseCase)
    factoryOf(::SetAppProfileUseCase)
    factoryOf(::GetAppSepolicyUseCase)
    factoryOf(::SetAppSepolicyUseCase)
    factoryOf(::ControlAppUseCase)
    factoryOf(::ValidateSepolicyUseCase)
    factoryOf(::GetDefaultUmountModulesUseCase)
    factoryOf(::GetSuSFSStatusUseCase)
    factoryOf(::SuSFSConfigUseCase)
    factoryOf(::ApplyLanguageUseCase)
    factoryOf(::IsSystemLanguageSettingsUseCase)
    factoryOf(::LaunchSystemLanguageSettingsUseCase)
    factoryOf(::GenerateBugreportUseCase)
    factoryOf(::ObserveStartupStateUseCase)
    factoryOf(::GetSuperUserAppGroupUseCase)
    factoryOf(::ObserveCatalogModulesUseCase)
    factoryOf(::ObserveModuleCatalogRefreshingUseCase)
    factoryOf(::ObserveModuleCatalogOfflineUseCase)
    factoryOf(::RefreshModuleCatalogUseCase)
    factoryOf(::GetCatalogModuleUseCase)
    factoryOf(::ObserveProfileTemplatesUseCase)
    factoryOf(::ObserveProfileTemplateRefreshingUseCase)
    factoryOf(::ObserveProfileTemplateOfflineUseCase)
    factoryOf(::RefreshProfileTemplatesUseCase)
    factoryOf(::GetProfileTemplateUseCase)
    factoryOf(::SaveProfileTemplateUseCase)
    factoryOf(::DeleteProfileTemplateUseCase)
    factoryOf(::ImportProfileTemplatesUseCase)
    factoryOf(::ExportProfileTemplatesUseCase)
    factoryOf(::GetBooleanPreferenceUseCase)
    factoryOf(::SetBooleanPreferenceUseCase)
    factoryOf(::GetStringPreferenceUseCase)
    factoryOf(::SetStringPreferenceUseCase)
    factoryOf(::GetStringSetPreferenceUseCase)
    factoryOf(::SetStringSetPreferenceUseCase)
    factoryOf(::ObserveDynamicManagerStateUseCase)
    factoryOf(::RefreshDynamicManagerUseCase)
    factoryOf(::SelectDynamicManagerUseCase)
    factoryOf(::SetManualDynamicManagerUseCase)
    factoryOf(::ClearDynamicManagerUseCase)
    factoryOf(::ObserveSulogStateUseCase)
    factoryOf(::RefreshSulogUseCase)
    factoryOf(::EnableSulogUseCase)
    factoryOf(::CleanSulogUseCase)
    factoryOf(::ObserveUmountStateUseCase)
    factoryOf(::RefreshUmountPathsUseCase)
    factoryOf(::AddUmountPathUseCase)
    factoryOf(::RemoveUmountPathUseCase)
    factoryOf(::ObserveKernelFlashUseCase)
    factoryOf(::StartKernelFlashUseCase)
    factoryOf(::RemovePreferenceUseCase)
    factoryOf(::ObserveSuperUserStateUseCase)
    factoryOf(::RefreshSuperUsersUseCase)
    factoryOf(::BackupAllowlistUseCase)
    factoryOf(::ImportAllowlistUseCase)
    factoryOf(::FetchRemoteTextUseCase)
    factoryOf(::IsModuleUriAccessibleUseCase)
    factoryOf(::TakeModuleUriPermissionUseCase)
    factoryOf(::ExtractModuleNameUseCase)
    factoryOf(::ExtractModuleIdUseCase)
    factoryOf(::ObserveInstalledModulesUseCase)
    factoryOf(::RefreshInstalledModulesUseCase)
    factoryOf(::CalculateInstalledModuleSizeUseCase)
    factoryOf(::UpdateCachedModuleEnabledUseCase)
    factoryOf(::ExecuteModuleActionUseCase)
    factoryOf(::SaveModuleActionLogUseCase)
    factoryOf(::SetModuleEnabledUseCase)
    factoryOf(::SetModuleRemovedUseCase)
    factoryOf(::TransliterateTextUseCase)
}

val viewModelModule = module {
    viewModel { parameters ->
        AppProfileViewModel(
            uid = parameters[0],
            packageName = parameters[1],
            getAppGroup = get(),
            getProfile = get(),
            getDefaultUmountModules = get(),
            setProfile = get(),
            getSepolicy = get(),
            setSepolicy = get(),
            controlApp = get(),
            validateSepolicy = get(),
        )
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::InstallViewModel)
    viewModelOf(::MainIntentViewModel)
    viewModelOf(::KernelFlashViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ModuleViewModel)
    viewModelOf(::SuperUserViewModel)
    viewModelOf(::SuSFSViewModel)
    viewModelOf(::ModuleRepoViewModel)
    viewModel { parameters -> ModuleDetailViewModel(parameters[0], get()) }
    viewModelOf(::TemplateViewModel)
    viewModel { parameters ->
        TemplateEditorViewModel(
            templateId = parameters[0],
            readOnly = parameters[1],
            isCreation = parameters[2],
            getTemplate = get(),
            saveTemplate = get(),
            deleteTemplate = get(),
        )
    }
    viewModelOf(::SulogViewModel)
    viewModelOf(::DynamicManagerViewModel)
    viewModelOf(::FlashViewModel)
    viewModelOf(::UmountManagerScreenViewModel)
    viewModel { parameters ->
        ExecuteModuleActionViewModel(
            moduleId = parameters[0],
            executeModuleAction = get(),
            saveModuleActionLog = get(),
        )
    }
}

val appModules = listOf(coreModule, repositoryModule, useCaseModule, viewModelModule)
