package com.resukisu.resukisu.data.profile

import android.app.Application
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.Natives.Profile.Namespace
import com.resukisu.resukisu.Natives.Profile.RootProfileFlag
import com.resukisu.resukisu.data.network.NetworkRequestRepository
import com.resukisu.resukisu.data.network.NetworkStatusRepository
import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.domain.model.ProfileTemplate
import com.resukisu.resukisu.domain.model.ProfileTemplateException
import com.resukisu.resukisu.domain.model.ProfileTemplateFailure
import com.resukisu.resukisu.profile.Capabilities
import com.resukisu.resukisu.profile.Groups
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.text.Collator
import java.util.Locale
import java.util.concurrent.TimeUnit

class ProfileTemplateRepository(
    private val application: Application,
    private val networkStatusRepository: NetworkStatusRepository,
    private val networkRequestRepository: NetworkRequestRepository,
    private val ksuCliRepository: KsuCliRepository,
) {
    companion object {
        private const val TAG = "ProfileTemplate"
        private const val TEMPLATE_INDEX_URL = "https://kernelsu.org/templates/index.json"
        private const val TEMPLATE_URL = "https://kernelsu.org/templates/%s"

        /** Wear Data Layer channel on which a paired phone serves online templates to a watch. */
        const val ONLINE_TEMPLATES_PATH = "/resukisu/templates/online"
        private const val ONLINE_TEMPLATES_CAPABILITY = "resukisu_profile_templates"
    }

    private val refreshMutex = Mutex()
    private val mutableTemplates = MutableStateFlow<List<ProfileTemplate>>(emptyList())
    private val mutableRefreshing = MutableStateFlow(false)
    private val mutableOffline = MutableStateFlow(false)

    val templates: StateFlow<List<ProfileTemplate>> = mutableTemplates.asStateFlow()
    val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()
    val offline: StateFlow<Boolean> = mutableOffline.asStateFlow()

    suspend fun refresh(synchronize: Boolean = false): Result<Unit> = refreshMutex.withLock {
        mutableRefreshing.value = true
        try {
            withContext(Dispatchers.IO) {
                runCatching {
                    val localIds = ksuCliRepository.listAppProfileTemplates()
                    val shouldSynchronize = localIds.isEmpty() || synchronize
                    val synchronized = !shouldSynchronize || fetchRemoteTemplates()
                    mutableOffline.value = shouldSynchronize && !synchronized
                    mutableTemplates.value = ksuCliRepository.listAppProfileTemplates()
                        .mapNotNull(::readTemplate)
                        .sortedWith(
                            compareBy(ProfileTemplate::local).reversed().then(
                                compareBy(
                                    Collator.getInstance(Locale.getDefault()),
                                    ProfileTemplate::id
                                )
                            )
                        )
                }
            }
        } finally {
            mutableRefreshing.value = false
        }
    }

    /** Answers a watch's template synchronization on [channel] with this device's own network. */
    suspend fun serveOnlineTemplates(client: ChannelClient, channel: ChannelClient.Channel) = withContext(Dispatchers.IO) {
        try {
            DataOutputStream(Tasks.await(client.getOutputStream(channel), 5, TimeUnit.SECONDS)).use { output ->
                val bytes = runCatching {
                    check(networkStatusRepository.isAvailable())
                    JSONObject(checkNotNull(fetchRemoteBodies())).toString().toByteArray()
                }.getOrNull()
                output.writeBoolean(bytes != null)
                bytes?.let { output.writeInt(it.size); output.write(it) }
                output.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Online template transfer failed", e)
        } finally {
            client.close(channel)
        }
    }

    /** The official templates by ID, skipping those that fail to download; null without the index. */
    private suspend fun fetchRemoteBodies(): Map<String, String>? {
        val ids = networkRequestRepository.fetch(TEMPLATE_INDEX_URL)
            .mapCatching { JSONArray(it) }
            .getOrElse { return null }
        return (0 until ids.length()).mapNotNull { index ->
            val id = ids.optString(index)
            networkRequestRepository.fetch(TEMPLATE_URL.format(id)).getOrNull()?.let { id to it }
        }.toMap()
    }

    /** The template bodies by ID fetched by a reachable paired phone, or null when none answers. */
    private suspend fun fetchBodiesFromPhone(): Map<String, String>? = runCatching {
        val localId = Tasks.await(Wearable.getNodeClient(application).localNode, 5, TimeUnit.SECONDS).id
        val nodes = Tasks.await(Wearable.getCapabilityClient(application)
            .getCapability(ONLINE_TEMPLATES_CAPABILITY, CapabilityClient.FILTER_REACHABLE), 5, TimeUnit.SECONDS)
            .nodes.filter { it.id != localId }
        val phone = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() ?: return null
        val client = Wearable.getChannelClient(application)
        val channel = Tasks.await(client.openChannel(phone.id, ONLINE_TEMPLATES_PATH), 5, TimeUnit.SECONDS)
        try {
            val input = DataInputStream(Tasks.await(client.getInputStream(channel), 5, TimeUnit.SECONDS))
            // Closing the stream bounds a blocking read even if the phone disconnects mid-transfer.
            val timeout = CoroutineScope(Dispatchers.IO).launch { delay(45_000); input.close() }
            try {
                input.use {
                    check(it.readBoolean()) { "Phone template fetch failed" }
                    val length = it.readInt()
                    require(length in 1..1_048_576) { "Invalid template response size" }
                    val bodies = JSONObject(ByteArray(length).also(it::readFully).decodeToString())
                    bodies.keys().asSequence().associateWith(bodies::getString)
                }
            } finally {
                timeout.cancel()
            }
        } finally {
            client.close(channel)
        }
    }.onFailure { Log.w(TAG, "Phone template fetch failed", it) }.getOrNull()

    suspend fun get(id: String): Result<ProfileTemplate> = withContext(Dispatchers.IO) {
        mutableTemplates.value.firstOrNull { it.id == id }
            ?.let { return@withContext Result.success(it) }
        readTemplate(id)?.let { Result.success(it) }
            ?: Result.failure(ProfileTemplateException(ProfileTemplateFailure.NotFound))
    }

    suspend fun save(template: ProfileTemplate, create: Boolean = false): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (!isValidId(template.id)) {
                return@withContext Result.failure(ProfileTemplateException(ProfileTemplateFailure.Invalid))
            }
            if (create && ksuCliRepository.getAppProfileTemplate(template.id).isNotBlank()) {
                return@withContext Result.failure(ProfileTemplateException(ProfileTemplateFailure.Conflict))
            }
            if (!ksuCliRepository.setAppProfileTemplate(
                    template.id,
                    template.copy(local = true).toJson().toString(),
                )
            ) {
                return@withContext Result.failure(
                    ProfileTemplateException(ProfileTemplateFailure.Command("save failed"))
                )
            }
            refreshLocalCache()
            Result.success(Unit)
        }

    suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!ksuCliRepository.deleteAppProfileTemplate(id)) {
            Result.failure(ProfileTemplateException(ProfileTemplateFailure.Command("delete failed")))
        } else {
            refreshLocalCache()
            Result.success(Unit)
        }
    }

    suspend fun import(json: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val array = runCatching { JSONArray(json) }.getOrElse {
                JSONArray().apply { put(JSONObject(json)) }
            }
            var saved = 0
            (0 until array.length()).forEach { index ->
                val value = array.optJSONObject(index) ?: return@forEach
                val parsed = value.toTemplate() ?: return@forEach
                if (ksuCliRepository.setAppProfileTemplate(
                        parsed.id,
                        parsed.copy(local = true).toJson().toString(),
                    )
                ) saved++
            }
            check(saved > 0) { "No valid templates" }
            refreshLocalCache()
        }
    }

    suspend fun export(): Result<String> = withContext(Dispatchers.IO) {
        val local = ksuCliRepository.listAppProfileTemplates()
            .mapNotNull(::readTemplate)
            .filter(ProfileTemplate::local)
        if (local.isEmpty()) {
            Result.failure(ProfileTemplateException(ProfileTemplateFailure.Empty))
        } else {
            Result.success(JSONArray(local.map { it.toJson() }).toString())
        }
    }

    private fun refreshLocalCache() {
        mutableTemplates.value =
            ksuCliRepository.listAppProfileTemplates().mapNotNull(::readTemplate)
                .sortedWith(
                    compareBy(ProfileTemplate::local).reversed().thenBy(ProfileTemplate::id)
                )
    }

    /**
     * Without a usable network, as on a watch behind its phone's Bluetooth proxy, the templates are
     * fetched by the paired phone's Manager instead.
     */
    private suspend fun fetchRemoteTemplates(): Boolean = runCatching {
        val bodies = (if (networkStatusRepository.isAvailable()) fetchRemoteBodies() else fetchBodiesFromPhone())
            ?: return false
        var fetchedAny = bodies.isEmpty()
        bodies.forEach { (id, body) ->
            val template =
                runCatching { JSONObject(body).toTemplate() }.getOrNull() ?: return@forEach
            if (ksuCliRepository.setAppProfileTemplate(
                    id,
                    template.copy(local = false).toJson().toString(),
                )
            ) fetchedAny = true
        }
        fetchedAny
    }.getOrDefault(false)

    private fun readTemplate(id: String): ProfileTemplate? = runCatching {
        JSONObject(ksuCliRepository.getAppProfileTemplate(id)).toTemplate()
    }.getOrNull()

    private fun JSONObject.toTemplate(): ProfileTemplate? = runCatching {
        val namespaceName = optString("namespace").ifBlank { Namespace.INHERITED.name }
        ProfileTemplate(
            id = getString("id"),
            name = localeString("name"),
            description = localeString("description"),
            author = optString("author"),
            local = optBoolean("local"),
            namespace = Namespace.valueOf(namespaceName.uppercase()).ordinal,
            uid = optInt("uid", Natives.ROOT_UID),
            gid = optInt("gid", Natives.ROOT_GID),
            groups = enumOrdinals<Groups>(optJSONArray("groups")).map(Groups::gid),
            capabilities = enumOrdinals<Capabilities>(optJSONArray("capabilities")).map(Capabilities::cap),
            context = optString("context").ifBlank { Natives.KERNEL_SU_DOMAIN },
            rules = optJSONArray("rules").strings(),
            flags = optJSONArray("flags")?.let {
                enumOrdinals<RootProfileFlag>(it).map(RootProfileFlag::ordinal)
            } ?: listOf(RootProfileFlag.NO_NEW_PRIVS.ordinal),
        )
    }.getOrNull()

    private fun ProfileTemplate.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name.ifBlank { id })
        put("description", description.ifBlank { id })
        if (author.isNotEmpty()) put("author", author)
        put("local", local)
        put("namespace", Namespace.entries[namespace].name)
        put("uid", uid)
        put("gid", gid)
        if (groups.isNotEmpty()) put(
            "groups",
            JSONArray(Groups.entries.filter { it.gid in groups }.map { it.name })
        )
        if (capabilities.isNotEmpty()) put(
            "capabilities",
            JSONArray(Capabilities.entries.filter { it.cap in capabilities }.map { it.name }),
        )
        if (context.isNotEmpty()) put("context", context)
        if (rules.isNotEmpty()) put("rules", JSONArray(rules))
        put(
            "flags",
            JSONArray(RootProfileFlag.entries.filter { it.ordinal in flags }.map { it.name })
        )
    }

    private fun JSONObject.localeString(key: String): String {
        val fallback = getString(key)
        val locale = Locale.getDefault()
        val localized = optJSONObject("locales") ?: return fallback
        return localized.optJSONObject("${locale.language}_${locale.country}")
            ?.optString(key, fallback)
            ?: localized.optJSONObject(locale.language)?.optString(key, fallback)
            ?: fallback
    }

    private inline fun <reified T : Enum<T>> enumOrdinals(array: JSONArray?): List<T> =
        array.strings().mapNotNull { runCatching { enumValueOf<T>(it.uppercase()) }.getOrNull() }

    private fun JSONArray?.strings(): List<String> = this?.let { array ->
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) }
    }.orEmpty()

    private fun isValidId(id: String) =
        Regex("""^([A-Za-z][A-Za-z\d_]*\.)*[A-Za-z][A-Za-z\d_]*$""").matches(id)
}
