package com.resukisu.resukisu.ui.wear

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.PagerScaffoldDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.network.WearLinkFailure
import com.resukisu.resukisu.domain.model.FlashOperation
import com.resukisu.resukisu.domain.model.InstalledModule
import com.resukisu.resukisu.domain.model.LkmSelection
import com.resukisu.resukisu.ui.wear.component.WearActionButton
import com.resukisu.resukisu.ui.wear.component.WearHorizontalPageIndicator
import com.resukisu.resukisu.ui.wear.component.WearInfoCard
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearLoadingScreen
import com.resukisu.resukisu.ui.wear.component.WearManagerUpdateDialog
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearSwipeToDismissBox
import com.resukisu.resukisu.ui.wear.component.WearTextInputPage
import com.resukisu.resukisu.ui.wear.component.WearTimeText
import com.resukisu.resukisu.ui.wear.component.rememberWearConfirmDialog
import com.resukisu.resukisu.ui.wear.component.rememberWearRemoteInput
import com.resukisu.resukisu.ui.util.ActivityResumeEffect
import com.resukisu.resukisu.domain.model.HomeDashboardState
import com.resukisu.resukisu.ui.viewmodel.HomeUiAction
import com.resukisu.resukisu.ui.viewmodel.HomeUiEvent
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import com.resukisu.resukisu.ui.viewmodel.ModuleUiAction
import com.resukisu.resukisu.ui.viewmodel.ModuleUiEvent
import com.resukisu.resukisu.ui.viewmodel.ModuleUiState
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.viewmodel.SettingsUiAction
import com.resukisu.resukisu.ui.viewmodel.SettingsUiEvent
import com.resukisu.resukisu.ui.viewmodel.SettingsUiState
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import com.resukisu.resukisu.ui.viewmodel.SulogUiAction
import com.resukisu.resukisu.ui.viewmodel.SulogUiEvent
import com.resukisu.resukisu.ui.viewmodel.SulogViewModel
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiAction
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiEvent
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiState
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import com.resukisu.resukisu.ui.viewmodel.WearLinkEvent
import com.resukisu.resukisu.ui.viewmodel.WearLinkViewModel
import com.resukisu.resukisu.ui.viewmodel.WearPreferences
import com.resukisu.resukisu.ui.viewmodel.WearPreferencesViewModel
import org.koin.compose.viewmodel.koinViewModel

private const val HOME = 0
private const val SUPERUSER = 1
private const val MODULES = 2
private const val SETTINGS = 3

/** Destinations that need a full-featured root manager; they close when root goes away. */
private fun WearRoute.needsRoot() = when (this) {
    is WearRoute.Module, is WearRoute.ModuleAction, is WearRoute.ModuleUpdate, is WearRoute.ModuleInstall,
    WearRoute.ModuleSort, WearRoute.ModuleSearch, WearRoute.ModuleSearchResults,
    is WearRoute.App, WearRoute.AppFilter, WearRoute.AppSearch, WearRoute.AppSearchResults,
    WearRoute.Logs, WearRoute.LogOptions, is WearRoute.LogEntry, WearRoute.LogSearch,
    WearRoute.Templates, is WearRoute.TemplateEditor, WearRoute.SuSFS, WearRoute.DynamicManager,
    WearRoute.Umount, WearRoute.Uninstall, WearRoute.Reboot -> true
    is WearRoute.Settings -> category == WearSettingsCategory.Security || category == WearSettingsCategory.Advanced
    is WearRoute.Selection -> selection == WearSelection.SuCompat
    else -> false
}

@Composable
fun WearManagerScreen() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // The shared models belong to the activity, outside the back stack, as on the phone.
    val homeViewModel = koinViewModel<HomeViewModel>()
    val superUserViewModel = koinViewModel<SuperUserViewModel>()
    val moduleViewModel = koinViewModel<ModuleViewModel>()
    val sulogViewModel = koinViewModel<SulogViewModel>()
    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val preferenceViewModel = koinViewModel<WearPreferencesViewModel>()
    val linkViewModel = koinViewModel<WearLinkViewModel>()
    val preferences by preferenceViewModel.state.collectAsStateWithLifecycle()
    val linkLoading by linkViewModel.loading.collectAsStateWithLifecycle()
    val home by homeViewModel.uiState.collectAsStateWithLifecycle()
    val superuser by superUserViewModel.uiState.collectAsStateWithLifecycle()
    val modules by moduleViewModel.uiState.collectAsStateWithLifecycle()
    val logs by sulogViewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val backStack = rememberNavBackStack(WearRoute.Main)

    var settingsError by remember { mutableStateOf<String?>(null) }
    var settingsMessageResource by remember { mutableStateOf<Pair<Int, Int?>?>(null) }
    var moduleError by remember { mutableStateOf<String?>(null) }
    var superuserError by remember { mutableStateOf<String?>(null) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var logError by remember { mutableStateOf<String?>(null) }
    // Results of opening a module WebUI on the phone, shown as confirmation dialogs.
    var webUiOnPhone by remember { mutableStateOf(false) }
    var webUiPhoneFailure by remember { mutableStateOf<WearLinkFailure?>(null) }
    var webUiPhoneFailed by remember { mutableStateOf(false) }
    val operationFailedText = stringResource(R.string.operation_failed)

    // Settings feedback belongs to the page that caused it; navigating clears it.
    fun clearSettingsFeedback() {
        settingsError = null
        settingsMessageResource = null
    }
    fun open(route: WearRoute) {
        clearSettingsFeedback()
        if (backStack.lastOrNull() != route) backStack.add(route)
    }
    fun back() {
        clearSettingsFeedback()
        // Search results live on their own page; leaving it clears the query of the main list.
        when (backStack.lastOrNull()) {
            WearRoute.AppSearchResults -> superUserViewModel.dispatch(SuperUserUiAction.Search(""))
            WearRoute.ModuleSearchResults -> moduleViewModel.dispatch(ModuleUiAction.Search(""))
            else -> Unit
        }
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else activity?.finish()
    }
    // A query submitted on an in-app input page leaves it for the results page, or updates the
    // results page it was opened from; a blank query only closes the input.
    fun showSearchResults(results: WearRoute, query: String, search: (String) -> Unit) {
        back()
        if (query.isBlank()) return
        search(query.trim())
        open(results)
    }
    // Searches use the system Wear input; the in-app input pages are only the fallback.
    val appSearchInput = rememberWearRemoteInput(stringResource(R.string.search_apps),
        onUnavailable = { open(WearRoute.AppSearch) }) { query ->
        if (query.isNotBlank()) {
            superUserViewModel.dispatch(SuperUserUiAction.Search(query.trim()))
            open(WearRoute.AppSearchResults)
        }
    }
    val moduleSearchInput = rememberWearRemoteInput(stringResource(R.string.search_modules),
        onUnavailable = { open(WearRoute.ModuleSearch) }) { query ->
        if (query.isNotBlank()) {
            moduleViewModel.dispatch(ModuleUiAction.Search(query.trim()))
            open(WearRoute.ModuleSearchResults)
        }
    }
    val logSearchInput = rememberWearRemoteInput(stringResource(R.string.sulog_search_placeholder),
        onUnavailable = { open(WearRoute.LogSearch) }) { sulogViewModel.dispatch(SulogUiAction.Search(it.trim())) }

    // As on the phone, a selected module ZIP is installed only after the user confirms it.
    var pendingInstallUri by rememberSaveable { mutableStateOf("") }
    val pendingInstallName = pendingInstallUri.toUri().lastPathSegment?.substringAfterLast('/') ?: pendingInstallUri
    val installDialog = rememberWearConfirmDialog(stringResource(R.string.confirm_installation),
        stringResource(R.string.confirm_install_module_title, pendingInstallName)) {
        open(WearRoute.ModuleInstall(pendingInstallUri))
    }
    // Plain SAF requests; on watches without a usable DocumentsUI the activity routes them to the
    // built-in picker (see withDocumentPickerFallback).
    val selectModuleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingInstallUri = uri.toString()
            installDialog.show()
        }
    }
    val selectImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) settingsViewModel.dispatch(SettingsUiAction.SetCustomBackground(uri.toString()))
    }
    val dispatchSettings: (SettingsUiAction) -> Unit = {
        clearSettingsFeedback()
        settingsViewModel.dispatch(it)
    }
    val settingsMessage = settingsError ?: settingsMessageResource?.let { (id, arg) ->
        if (arg == null) stringResource(id) else stringResource(id, arg)
    }

    LaunchedEffect(home.systemStatus.isFullFeatured, home.systemInfo.susfsEnabled) {
        val lost = backStack.any {
            (it as WearRoute).needsRoot() && !home.systemStatus.isFullFeatured ||
                it == WearRoute.SuSFS && !home.systemInfo.susfsEnabled
        }
        if (lost) {
            clearSettingsFeedback()
            backStack.retainAll { it == WearRoute.Main }
        }
    }
    ActivityResumeEffect(homeViewModel) {
        homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = true))
    }
    LaunchedEffect(sulogViewModel) {
        sulogViewModel.events.collect { event -> if (event is SulogUiEvent.Error) logError = event.message }
    }
    LaunchedEffect(homeViewModel) {
        homeViewModel.events.collect { event -> if (event is HomeUiEvent.Error) homeError = event.message }
    }
    LaunchedEffect(superUserViewModel) {
        superUserViewModel.events.collect { event -> if (event is SuperUserUiEvent.Error) superuserError = event.message }
    }
    LaunchedEffect(moduleViewModel, operationFailedText) {
        moduleViewModel.events.collect { event ->
            when (event) {
                is ModuleUiEvent.Error -> moduleError = event.message
                ModuleUiEvent.RefreshCompleted -> moduleError = null
                is ModuleUiEvent.EnabledChanged -> {
                    if (event.successful) moduleViewModel.dispatch(ModuleUiAction.Refresh())
                    else moduleError = operationFailedText
                }
                is ModuleUiEvent.RemovedChanged -> {
                    if (event.successful) moduleViewModel.dispatch(ModuleUiAction.Refresh())
                    else moduleError = operationFailedText
                }
            }
        }
    }
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.events.collect { event ->
            when (event) {
                is SettingsUiEvent.Error -> {
                    settingsError = event.message
                    settingsMessageResource = null
                }
                is SettingsUiEvent.Message -> {
                    settingsError = null
                    settingsMessageResource = event.stringResource to event.formatArg
                }
                SettingsUiEvent.RestartActivity -> activity?.recreate()
            }
        }
    }
    LaunchedEffect(linkViewModel) {
        linkViewModel.events.collect { event ->
            when (event) {
                is WearLinkEvent.WebView -> open(WearRoute.Browser(event.url))
                is WearLinkEvent.WebUi -> runCatching {
                    context.startActivity(Intent(context, WearWebUIActivity::class.java)
                        .setData("kernelsu://webui/${event.moduleId}".toUri())
                        .putExtra("id", event.moduleId).putExtra("name", event.moduleName))
                }.onFailure { moduleError = operationFailedText }
                WearLinkEvent.SentToPhone -> open(WearRoute.LinkResult(R.string.wear_phone_sent))
                is WearLinkEvent.Failed -> open(WearRoute.LinkResult(linkFailureMessage(event.reason)))
                WearLinkEvent.WebUiOnPhone -> webUiOnPhone = true
                is WearLinkEvent.WebUiPhoneFailed -> {
                    webUiPhoneFailure = event.reason
                    webUiPhoneFailed = true
                }
            }
        }
    }
    BackHandler(linkLoading) { linkViewModel.cancel() }

    // A found manager update is offered in a dialog, the stable channel first as on the phone's Home.
    WearManagerUpdateDialog(home.stableManagerUpdate ?: home.betaManagerUpdate)
    WearPhoneWebUiDialogs(webUiOnPhone, { webUiOnPhone = false }, webUiPhoneFailure, webUiPhoneFailed) {
        webUiPhoneFailed = false
    }
    AppScaffold(timeText = { WearTimeText() }, containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { back() },
                sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<WearRoute.Main> {
                        WearMainPages(
                            home = home,
                            superuser = superuser,
                            modules = modules,
                            settings = settings,
                            preferences = preferences,
                            homeError = homeError,
                            superuserError = superuserError,
                            moduleError = moduleError,
                            settingsMessage = settingsMessage,
                            onHome = { action ->
                                if (action is HomeUiAction.Refresh) homeError = null
                                homeViewModel.dispatch(action)
                            },
                            onSuperUser = { action ->
                                if (action == SuperUserUiAction.Refresh) superuserError = null
                                superUserViewModel.dispatch(action)
                            },
                            onModule = { action ->
                                if (action is ModuleUiAction.Refresh) moduleError = null
                                moduleViewModel.dispatch(action)
                            },
                            onSettings = dispatchSettings,
                            onOpen = ::open,
                            onSearchApps = appSearchInput,
                            onSearchModules = moduleSearchInput,
                            onInstallModule = {
                                runCatching { selectModuleLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
                                    .onFailure { moduleError = operationFailedText }
                            },
                            onWebUi = { linkViewModel.openWebUi(it.id, it.name, preferences.link) },
                        )
                    }
                    entry<WearRoute.Module> { route ->
                        WearModuleDetail(
                            module = modules.moduleList.firstOrNull { it.id == route.id },
                            error = moduleError,
                            onBack = ::back,
                            onEnabledChange = { id, enabled -> moduleViewModel.dispatch(ModuleUiAction.SetEnabled(id, enabled)) },
                            onRemove = { id, removed -> moduleViewModel.dispatch(ModuleUiAction.SetRemoved(id, removed)) },
                            onWebUi = { linkViewModel.openWebUi(it.id, it.name, preferences.link) },
                            onExecute = { open(WearRoute.ModuleAction(it.id)) },
                            onUpdate = { open(WearRoute.ModuleUpdate(it.id)) },
                        )
                    }
                    entry<WearRoute.ModuleAction> { route ->
                        WearExecuteModulePage(route.id, ::back) { moduleViewModel.dispatch(ModuleUiAction.Refresh()) }
                    }
                    entry<WearRoute.ModuleUpdate> { route ->
                        WearModuleUpdatePage(modules.moduleList.firstOrNull { it.id == route.id }, ::back) { uri ->
                            back()
                            open(WearRoute.ModuleInstall(uri))
                        }
                    }
                    entry<WearRoute.ModuleInstall> { route ->
                        WearFlashPage(FlashOperation.Module(route.uri), ::back) {
                            moduleViewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
                            moduleViewModel.dispatch(ModuleUiAction.Refresh())
                            homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
                        }
                    }
                    entry<WearRoute.ModuleSort> {
                        WearModulePanel(modules, ::back, moduleViewModel::dispatch)
                    }
                    entry<WearRoute.ModuleSearch> {
                        WearTextInputPage(stringResource(R.string.search_modules), modules.search) {
                            showSearchResults(WearRoute.ModuleSearchResults, it) { query ->
                                moduleViewModel.dispatch(ModuleUiAction.Search(query))
                            }
                        }
                    }
                    entry<WearRoute.ModuleSearchResults> {
                        WearModuleSearchResults(modules, moduleError, onEdit = moduleSearchInput,
                            onModuleClick = { open(WearRoute.Module(it)) }, onBack = ::back)
                    }
                    entry<WearRoute.App> { route ->
                        val group = superuser.appGroupList.firstOrNull {
                            it.uid == route.uid && it.primaryPackageName == route.packageName
                        }
                        WearAppDetail(
                            group = group,
                            isManager = group?.uid?.let { it in superuser.managerUids } == true,
                            onBack = ::back,
                            onSaved = { superUserViewModel.dispatch(SuperUserUiAction.StatusChanged) },
                            onOpen = ::open,
                        )
                    }
                    entry<WearRoute.AppFilter> {
                        WearSuperUserPanel(superuser, ::back, superUserViewModel::dispatch)
                    }
                    entry<WearRoute.AppSearch> {
                        WearTextInputPage(stringResource(R.string.search_apps), superuser.search) {
                            showSearchResults(WearRoute.AppSearchResults, it) { query ->
                                superUserViewModel.dispatch(SuperUserUiAction.Search(query))
                            }
                        }
                    }
                    entry<WearRoute.AppSearchResults> {
                        WearAppSearchResults(superuser, superuserError, onEdit = appSearchInput,
                            onAppClick = { uid, packageName -> open(WearRoute.App(uid, packageName)) }, onBack = ::back)
                    }
                    entry<WearRoute.Logs> {
                        // Opening the SU log shows the latest file; returning from its own pages keeps
                        // the file chosen there.
                        var loaded by rememberSaveable { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            if (!loaded) {
                                loaded = true
                                sulogViewModel.dispatch(SulogUiAction.RefreshLatest)
                            }
                        }
                        WearLogsPage(
                            state = logs,
                            onBack = ::back,
                            onEnable = { sulogViewModel.dispatch(SulogUiAction.Enable) },
                            onSearch = logSearchInput,
                            onClearSearch = { sulogViewModel.dispatch(SulogUiAction.Search("")) },
                            onRefresh = {
                                logError = null
                                sulogViewModel.dispatch(SulogUiAction.Refresh)
                            },
                            onOptions = { open(WearRoute.LogOptions) },
                            onEntry = { open(WearRoute.LogEntry(it.key)) },
                            error = logError,
                        )
                    }
                    entry<WearRoute.LogOptions> {
                        WearLogOptionsPage(
                            state = logs,
                            onBack = ::back,
                            onSelectFile = { sulogViewModel.dispatch(SulogUiAction.SelectFile(it)) },
                            onToggleFilter = { sulogViewModel.dispatch(SulogUiAction.ToggleFilter(it)) },
                            onClean = { sulogViewModel.dispatch(SulogUiAction.CleanFile) },
                        )
                    }
                    entry<WearRoute.LogEntry> { route ->
                        WearLogEntryPage(logs.entries.firstOrNull { it.key == route.key }, ::back)
                    }
                    entry<WearRoute.LogSearch> {
                        WearTextInputPage(stringResource(R.string.sulog_search_placeholder), logs.searchText) {
                            sulogViewModel.dispatch(SulogUiAction.Search(it))
                            back()
                        }
                    }
                    entry<WearRoute.Settings> { route ->
                        WearSettingsPage(settings, home.systemStatus, settingsMessage, dispatchSettings, ::open, ::back,
                            route.category, preferences)
                    }
                    entry<WearRoute.Selection> { route ->
                        WearSelectionPage(route.selection, settings, preferences, preferenceViewModel, settingsMessage,
                            ::back, dispatchSettings)
                    }
                    entry<WearRoute.Color> {
                        WearColorPage(settings, settingsMessage, ::back, dispatchSettings) {
                            runCatching { selectImageLauncher.launch(arrayOf("image/*")) }
                                .onFailure { settingsError = operationFailedText }
                        }
                    }
                    entry<WearRoute.Dpi> {
                        WearDpiPage(settings, settingsMessage, ::back, dispatchSettings)
                    }
                    entry<WearRoute.Templates> {
                        WearTemplatePage(::back) { id, readOnly, creation ->
                            open(WearRoute.TemplateEditor(id, readOnly, creation))
                        }
                    }
                    entry<WearRoute.TemplateEditor> { route ->
                        WearTemplateEditor(route.id, route.readOnly, route.creation, ::back)
                    }
                    entry<WearRoute.SuSFS> {
                        WearSuSFSPage(::back)
                    }
                    entry<WearRoute.DynamicManager> {
                        WearDynamicManagerPage(::back)
                    }
                    entry<WearRoute.Umount> {
                        WearUmountPage(::back)
                    }
                    entry<WearRoute.Uninstall> {
                        WearUninstallPage(::back) { restore -> open(WearRoute.UninstallFlash(restore)) }
                    }
                    entry<WearRoute.UninstallFlash> { route ->
                        WearFlashPage(if (route.restore) FlashOperation.Restore else FlashOperation.Uninstall, ::back) {
                            homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
                        }
                    }
                    entry<WearRoute.KernelInstall> {
                        WearInstallPage(::back, ::open)
                    }
                    entry<WearRoute.LkmInstall> {
                        WearLkmInstallPage(::back, ::open)
                    }
                    entry<WearRoute.Ak3Install> {
                        WearAk3InstallPage(::back, ::open)
                    }
                    entry<WearRoute.BootFlash> { route ->
                        WearFlashPage(route.toOperation(), ::back) {
                            homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
                        }
                    }
                    entry<WearRoute.KernelFlash> { route ->
                        WearKernelFlashPage(route.uri, route.slot, route.skipKsud, ::back)
                    }
                    entry<WearRoute.Reboot> {
                        WearRebootPanel(home.systemStatus.isRootAvailable, ::back) {
                            homeViewModel.dispatch(HomeUiAction.Reboot(it))
                        }
                    }
                    entry<WearRoute.Bugreport> {
                        WearBugreportPage(::back)
                    }
                    entry<WearRoute.About> {
                        WearAboutDetail(onBack = ::back, onOpenLink = { linkViewModel.open(it, preferences.link) },
                            onLicenses = { open(WearRoute.Licenses) })
                    }
                    entry<WearRoute.Licenses> {
                        WearLicensePage(::back) { linkViewModel.open(it, preferences.link) }
                    }
                    entry<WearRoute.Browser> { route ->
                        WearBrowserPage(route.url, ::back) {
                            back()
                            // In automatic mode a WebView that cannot be created falls back to the phone.
                            if (preferences.link == "auto") linkViewModel.open(route.url, "phone")
                            else open(WearRoute.LinkResult(R.string.wear_link_webview_unavailable))
                        }
                    }
                    entry<WearRoute.LinkResult> { route ->
                        WearList(onBack = ::back) { spec ->
                            item { WearPageHeader(spec, stringResource(R.string.operation_failed)) }
                            item { WearInfoCard(spec) { Text(stringResource(route.message)) } }
                            item {
                                WearActionButton(spec, Icons.TwoTone.Settings, stringResource(R.string.wear_link_mode),
                                    { open(WearRoute.Selection(WearSelection.LinkMode)) })
                            }
                        }
                    }
                },
            )
            // Waiting for a link to open; Back cancels it.
            if (linkLoading) {
                // A waiting spinner; the branded loading screen is only shown at app startup.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/**
 * The four top-level pages in a horizontal pager; without a full-featured root manager only Home
 * and Settings are shown. Swiping right on the first page leaves the app.
 */
@Composable
private fun WearMainPages(
    home: HomeDashboardState,
    superuser: SuperUserUiState,
    modules: ModuleUiState,
    settings: SettingsUiState,
    preferences: WearPreferences,
    homeError: String?,
    superuserError: String?,
    moduleError: String?,
    settingsMessage: String?,
    onHome: (HomeUiAction) -> Unit,
    onSuperUser: (SuperUserUiAction) -> Unit,
    onModule: (ModuleUiAction) -> Unit,
    onSettings: (SettingsUiAction) -> Unit,
    onOpen: (WearRoute) -> Unit,
    onSearchApps: () -> Unit,
    onSearchModules: () -> Unit,
    onInstallModule: () -> Unit,
    onWebUi: (InstalledModule) -> Unit,
) {
    val activity = LocalActivity.current
    if (!home.isInitialDataLoaded && homeError == null) {
        // The first load keeps the startup spinner until Home has its data.
        LaunchedEffect(Unit) { onHome(HomeUiAction.Refresh(showIndicator = false)) }
        WearLoadingScreen()
        return
    }
    val pages = if (home.systemStatus.isFullFeatured) listOf(HOME, SUPERUSER, MODULES, SETTINGS) else listOf(HOME, SETTINGS)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    // Keep the shown page when root availability adds or removes pages.
    var previousPages by remember { mutableStateOf(pages) }
    LaunchedEffect(pages) {
        if (pages != previousPages) {
            val selectedPage = previousPages.getOrNull(pagerState.currentPage) ?: HOME
            pagerState.scrollToPage(pages.indexOf(selectedPage).coerceAtLeast(0))
            previousPages = pages
        }
    }
    // Lists load once, as on the phone; later changes refresh through their own events and the pull
    // gesture, not on every page visit.
    LaunchedEffect(pagerState.currentPage, home.systemStatus.isFullFeatured) {
        when (pages.getOrNull(pagerState.currentPage)) {
            HOME -> onHome(HomeUiAction.Refresh(showIndicator = false))
            SUPERUSER -> if (superuser.appGroupList.isEmpty() && !superuser.isRefreshing) onSuperUser(SuperUserUiAction.Refresh)
            MODULES -> if (modules.moduleList.isEmpty() || modules.isNeedRefresh) onModule(ModuleUiAction.Refresh())
        }
    }
    val homeListState = rememberTransformingLazyColumnState()
    val appListState = rememberTransformingLazyColumnState()
    val moduleListState = rememberTransformingLazyColumnState()
    val settingsListState = rememberTransformingLazyColumnState()
    WearSwipeToDismissBox(onDismissed = { activity?.finish() }) { isBackground ->
        if (!isBackground) HorizontalPagerScaffold(pagerState = pagerState,
            pageIndicator = { WearHorizontalPageIndicator(pagerState) }) {
            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = pagerState,
                flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(pagerState),
                key = { pages[it] },
            ) { page ->
                AnimatedPage(pageIndex = page, pagerState = pagerState) {
                    when (pages.getOrNull(page)) {
                        HOME -> WearHomePage(home, homeError, { activity?.finish() }, { onOpen(WearRoute.Reboot) },
                            homeListState, onInstall = { onOpen(WearRoute.KernelInstall) }, backToTop = true)
                        SUPERUSER -> WearSuperUserPage(
                            state = superuser,
                            error = superuserError,
                            onRefresh = { onSuperUser(SuperUserUiAction.Refresh) },
                            onBack = { activity?.finish() },
                            onSearch = onSearchApps,
                            onLogs = { onOpen(WearRoute.Logs) },
                            onFilter = { onOpen(WearRoute.AppFilter) },
                            listState = appListState,
                            onAppClick = { uid, packageName -> onOpen(WearRoute.App(uid, packageName)) },
                            backToTop = true,
                        )
                        MODULES -> WearModulesPage(
                            state = modules,
                            error = moduleError,
                            onRefresh = { onModule(ModuleUiAction.Refresh(manual = true)) },
                            onSearch = onSearchModules,
                            onSort = { onOpen(WearRoute.ModuleSort) },
                            listState = moduleListState,
                            onModuleClick = { onOpen(WearRoute.Module(it)) },
                            onInstallClick = onInstallModule,
                            onWebUi = onWebUi,
                            onExecute = { onOpen(WearRoute.ModuleAction(it.id)) },
                        )
                        SETTINGS -> WearSettingsPage(settings, home.systemStatus, settingsMessage, onSettings, onOpen,
                            { activity?.finish() }, preferences = preferences,
                            susfsAvailable = home.systemInfo.susfsEnabled, listState = settingsListState, backToTop = true)
                    }
                }
            }
        }
    }
}

private fun WearRoute.BootFlash.toOperation() = FlashOperation.Boot(
    bootUri = bootUri,
    lkm = when {
        lkmUri != null -> LkmSelection.LkmUri(lkmUri)
        kmi != null -> LkmSelection.KmiString(kmi)
        else -> LkmSelection.KmiNone
    },
    ota = ota,
    partition = partition,
    allowShell = allowShell,
    enableAdb = enableAdb,
    forceBackup = forceBackup,
)

private fun linkFailureMessage(reason: WearLinkFailure?): Int = when (reason) {
    null -> R.string.operation_failed
    WearLinkFailure.UNSUPPORTED_URL -> R.string.wear_link_unsupported
    WearLinkFailure.WEBVIEW_UNAVAILABLE -> R.string.wear_link_webview_unavailable
    WearLinkFailure.PHONE_UNAVAILABLE -> R.string.wear_link_phone_unavailable
    WearLinkFailure.PHONE_APP_MISSING -> R.string.wear_phone_app_missing
    WearLinkFailure.PHONE_FAILED -> R.string.wear_link_phone_failed
}

/**
 * The official open-on-phone confirmation after a module WebUI was opened on the phone, and the
 * failure confirmation when it could not be: a disconnected phone shows the connection failure icon.
 */
@Composable
private fun WearPhoneWebUiDialogs(
    opened: Boolean,
    onOpenedDismissed: () -> Unit,
    failure: WearLinkFailure?,
    failed: Boolean,
    onFailureDismissed: () -> Unit,
) {
    val openText = OpenOnPhoneDialogDefaults.text
    val openStyle = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(visible = opened, onDismissRequest = onOpenedDismissed,
        curvedText = { openOnPhoneDialogCurvedText(text = openText, style = openStyle) })
    val failureText = stringResource(when (failure) {
        WearLinkFailure.PHONE_UNAVAILABLE -> R.string.wear_phone_disconnected
        WearLinkFailure.PHONE_APP_MISSING -> R.string.wear_phone_app_missing
        else -> R.string.wear_phone_open_failed
    })
    val failureStyle = ConfirmationDialogDefaults.curvedTextStyle
    FailureConfirmationDialog(visible = failed, onDismissRequest = onFailureDismissed,
        curvedText = { confirmationDialogCurvedText(text = failureText, style = failureStyle) }) {
        if (failure == WearLinkFailure.PHONE_UNAVAILABLE) ConfirmationDialogDefaults.ConnectionFailureIcon()
        else ConfirmationDialogDefaults.FailureIcon()
    }
}
