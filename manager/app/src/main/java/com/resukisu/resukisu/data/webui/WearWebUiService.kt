package com.resukisu.resukisu.data.webui

import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.resukisu.resukisu.data.packageinfo.AppIconDataSource
import com.resukisu.resukisu.data.packageinfo.InstalledPackageRepository
import com.resukisu.resukisu.data.profile.ProfileTemplateRepository
import com.resukisu.resukisu.domain.usecase.RefreshInstalledModulesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Answers a phone showing a watch module's WebUI (see [WearWebUiProtocol]) and serves
 * phone-assisted online template downloads. Only Google Play services delivers these events, and
 * only from this app on paired devices signed with the same key. Calls run off the binder thread
 * in a process-wide scope, since the service may be unbound as soon as a callback returns.
 */
class WearWebUiService : WearableListenerService(), KoinComponent {
    private val webUiRepository: WebUiRepository by inject()
    private val packageRepository: InstalledPackageRepository by inject()
    private val appIconDataSource: AppIconDataSource by inject()
    private val refreshModules: RefreshInstalledModulesUseCase by inject()
    private val templateRepository: ProfileTemplateRepository by inject()

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val client = Wearable.getChannelClient(applicationContext)
        when (channel.path) {
            ProfileTemplateRepository.ONLINE_TEMPLATES_PATH ->
                bridgeScope.launch { templateRepository.serveOnlineTemplates(client, channel) }
            WearWebUiProtocol.CLAIM_PATH -> bridgeScope.launch { serveWebUiClaim(client, channel) }
            WearWebUiProtocol.RPC_PATH -> bridgeScope.launch {
                serveWebUiCall(client, channel, LocalWebUiBackend(webUiRepository, packageRepository, appIconDataSource)) {
                    // Like the local WebUI, the package list is loaded on first use.
                    if (packageRepository.packages.value.isEmpty()) packageRepository.refresh()
                }
            }
            else -> client.close(channel)
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearWebUiProtocol.CLOSED_PATH) return
        WearWebUiSession.end(event.data.decodeToString(), event.sourceNodeId)
        // The page may have changed the module, so the module list reloads.
        bridgeScope.launch { refreshModules(manual = false, checkUpdates = false) }
    }

    private companion object {
        val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
