package com.resukisu.resukisu.data.webui

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.resukisu.resukisu.domain.model.WebUiCommandResult
import com.resukisu.resukisu.domain.model.WebUiProcess
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Showing a watch module's WebUI on the phone. The watch offers a session for one module to the
 * phone it chose and opens the phone's WebUI activity with RemoteActivityHelper. That link carries
 * no session data: the phone activity claims the session from the watch over the Data Layer, which
 * only connects this app on devices signed with the same key, and then sends each file read and
 * command of the page back to the watch, where the watch serves it with its own root access.
 *
 * Frames on a channel are a type byte, a 4 byte length and the payload.
 */
object WearWebUiProtocol {
    const val CLAIM_PATH = "/resukisu/webui/claim"
    const val RPC_PATH = "/resukisu/webui/rpc"
    const val CLOSED_PATH = "/resukisu/webui/closed"
    /** Advertised by the app on every device, through `android_wear_capabilities`. */
    const val CAPABILITY = "resukisu_webui"
    const val SCHEME = "resukisu-webui"
    const val HOST = "open"

    const val FRAME_OK: Byte = 0
    const val FRAME_ERROR: Byte = 1
    const val FRAME_STDOUT: Byte = 2
    const val FRAME_STDERR: Byte = 3
    const val FRAME_EXIT: Byte = 4

    fun DataOutputStream.writeFrame(type: Byte, payload: ByteArray) {
        writeByte(type.toInt())
        writeInt(payload.size)
        write(payload)
        flush()
    }

    private const val MAX_FRAME_SIZE = 64 * 1024 * 1024

    fun DataInputStream.readFrame(): Pair<Byte, ByteArray> {
        val type = readByte()
        val size = readInt()
        require(size in 0..MAX_FRAME_SIZE) { "Invalid frame size" }
        val payload = ByteArray(size)
        readFully(payload)
        return type to payload
    }

    fun WebUiCommandResult.toJson(): String =
        JSONObject().put("code", code).put("stdout", stdout).put("stderr", stderr).toString()

    fun commandResult(json: String): WebUiCommandResult = JSONObject(json).let {
        WebUiCommandResult(it.getInt("code"), it.optString("stdout"), it.optString("stderr"))
    }
}

/**
 * The watch's only WebUI session: one module, offered to one phone node. That node may claim it once
 * within [CLAIM_WINDOW_MS] of the offer; afterwards only calls from the same node carrying the
 * session's random token are served. A new offer or the phone closing the page ends it.
 */
object WearWebUiSession {
    private const val CLAIM_WINDOW_MS = 30_000L

    private class Session(
        val token: String,
        val moduleId: String,
        val moduleName: String,
        val phoneNodeId: String,
        val offeredAt: Long,
    ) {
        var claimed = false
    }

    private var current: Session? = null

    /** Offers a session for [moduleId] to [phoneNodeId]; the returned token withdraws it. */
    @Synchronized
    fun offer(moduleId: String, moduleName: String, phoneNodeId: String): String =
        UUID.randomUUID().toString().also {
            current = Session(it, moduleId, moduleName, phoneNodeId, SystemClock.elapsedRealtime())
        }

    /** The pending session as JSON for the node it was offered to, at most once; otherwise null. */
    @Synchronized
    fun claim(nodeId: String): JSONObject? {
        val session = current?.takeIf {
            !it.claimed && it.phoneNodeId == nodeId &&
                SystemClock.elapsedRealtime() - it.offeredAt < CLAIM_WINDOW_MS
        } ?: return null
        session.claimed = true
        return JSONObject().put("token", session.token).put("module", session.moduleId).put("name", session.moduleName)
    }

    /** The module of the claimed session when [token] and [nodeId] both match it, otherwise null. */
    @Synchronized
    fun moduleFor(token: String, nodeId: String): String? =
        current?.takeIf { it.claimed && it.token == token && it.phoneNodeId == nodeId }?.moduleId

    /** Ends the session of [token]; a phone may only end the session it claimed. */
    @Synchronized
    fun end(token: String, nodeId: String? = null) {
        val session = current ?: return
        if (session.token == token && (nodeId == null || session.phoneNodeId == nodeId)) current = null
    }
}

/**
 * The phone's [WebUiBackend] for a watch module: each call opens a channel to [nodeId] and is
 * answered by the watch. Calls block, as the WebView calls its bridge and file handler off the main
 * thread.
 */
class RemoteWebUiBackend private constructor(
    context: Context,
    private val nodeId: String,
    private val token: String,
    val moduleId: String,
    val moduleName: String,
) : WebUiBackend {
    private val client = Wearable.getChannelClient(context)

    private fun <T> call(op: String, arg: String, extra: Int = 0, read: (DataInputStream) -> T): T {
        val channel = Tasks.await(client.openChannel(nodeId, WearWebUiProtocol.RPC_PATH), 15, TimeUnit.SECONDS)
        try {
            val output = DataOutputStream(Tasks.await(client.getOutputStream(channel)).buffered())
            val request = JSONObject().put("token", token).put("op", op).put("arg", arg).put("extra", extra)
            with(WearWebUiProtocol) { output.writeFrame(FRAME_OK, request.toString().toByteArray()) }
            val input = DataInputStream(Tasks.await(client.getInputStream(channel)).buffered())
            return read(input)
        } finally {
            client.close(channel)
        }
    }

    /** A single answer frame: its payload, or null when the watch answered with an error frame. */
    private fun single(op: String, arg: String, extra: Int = 0): ByteArray? = call(op, arg, extra) { input ->
        val (type, payload) = with(WearWebUiProtocol) { input.readFrame() }
        payload.takeIf { type == WearWebUiProtocol.FRAME_OK }
    }

    override fun execute(command: String): WebUiCommandResult = runCatching {
        single("exec", command)?.let { WearWebUiProtocol.commandResult(it.decodeToString()) }
    }.getOrNull() ?: WebUiCommandResult(-1, "", "")

    override fun spawn(command: String): WebUiProcess = object : WebUiProcess {
        override fun start(
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onComplete: (WebUiCommandResult) -> Unit,
        ) {
            thread(name = "remote-webui-spawn") {
                val result = runCatching {
                    call("spawn", command) { input ->
                        var exit: WebUiCommandResult? = null
                        while (exit == null) {
                            val (type, payload) = with(WearWebUiProtocol) { input.readFrame() }
                            when (type) {
                                WearWebUiProtocol.FRAME_STDOUT -> onStdout(payload.decodeToString())
                                WearWebUiProtocol.FRAME_STDERR -> onStderr(payload.decodeToString())
                                WearWebUiProtocol.FRAME_EXIT -> exit = WearWebUiProtocol.commandResult(payload.decodeToString())
                                else -> exit = WebUiCommandResult(-1, "", "")
                            }
                        }
                        exit
                    }
                }.getOrElse { WebUiCommandResult(-1, "", it.message.orEmpty()) }
                onComplete(result)
            }
        }

        override fun close() = Unit
    }

    override fun listModules(): String = runCatching { single("modules", "")?.decodeToString() }.getOrNull() ?: "[]"

    override fun openFile(path: String): InputStream? =
        runCatching { single("file", path)?.inputStream() }.getOrNull()

    override fun listPackages(type: String): String =
        runCatching { single("packages", type)?.decodeToString() }.getOrNull() ?: "[]"

    override fun getPackagesInfo(packageNamesJson: String): String =
        runCatching { single("packagesInfo", packageNamesJson)?.decodeToString() }.getOrNull() ?: "[]"

    override fun iconPng(packageName: String, size: Int): ByteArray? =
        runCatching { single("icon", packageName, size) }.getOrNull()

    /** Tells the watch the page was closed, so it ends the session and reloads its modules. */
    fun close(context: Context) {
        Wearable.getMessageClient(context).sendMessage(nodeId, WearWebUiProtocol.CLOSED_PATH, token.toByteArray())
    }

    companion object {
        /**
         * Claims the session a reachable watch offered to this phone, or returns null when none did.
         * Blocks, so it must run off the main thread.
         */
        fun claim(context: Context): RemoteWebUiBackend? = runCatching {
            val localId = Tasks.await(Wearable.getNodeClient(context).localNode, 5, TimeUnit.SECONDS).id
            val client = Wearable.getChannelClient(context)
            Tasks.await(Wearable.getCapabilityClient(context)
                .getCapability(WearWebUiProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE), 5, TimeUnit.SECONDS)
                .nodes.filter { it.id != localId }
                .firstNotNullOfOrNull { node -> runCatching { claimFrom(context, client, node.id) }.getOrNull() }
        }.onFailure { Log.w("RemoteWebUi", "Session claim failed", it) }.getOrNull()

        private fun claimFrom(context: Context, client: ChannelClient, nodeId: String): RemoteWebUiBackend? {
            val channel = Tasks.await(client.openChannel(nodeId, WearWebUiProtocol.CLAIM_PATH), 5, TimeUnit.SECONDS)
            try {
                val input = DataInputStream(Tasks.await(client.getInputStream(channel), 5, TimeUnit.SECONDS).buffered())
                val (type, payload) = with(WearWebUiProtocol) { input.readFrame() }
                if (type != WearWebUiProtocol.FRAME_OK) return null
                val session = JSONObject(payload.decodeToString())
                val moduleId = session.getString("module")
                require(moduleId.isNotEmpty() && '/' !in moduleId && moduleId != "..") { "Invalid module" }
                return RemoteWebUiBackend(context, nodeId, session.getString("token"), moduleId,
                    session.optString("name").ifEmpty { moduleId })
            } finally {
                client.close(channel)
            }
        }
    }
}

/** The watch's answer to a phone claiming its pending session on [channel]. */
internal fun serveWebUiClaim(client: ChannelClient, channel: ChannelClient.Channel) {
    try {
        val output = DataOutputStream(Tasks.await(client.getOutputStream(channel)).buffered())
        val session = WearWebUiSession.claim(channel.nodeId)
        with(WearWebUiProtocol) {
            if (session == null) output.writeFrame(FRAME_ERROR, ByteArray(0))
            else output.writeFrame(FRAME_OK, session.toString().toByteArray())
        }
    } catch (_: Exception) {
        // The phone closed the channel or the connection dropped; the claim simply fails.
    } finally {
        client.close(channel)
    }
}

/**
 * The watch's answer to one phone call on [channel], served by [backend]. Only the phone node that
 * claimed the session, presenting its token, is answered; file reads are limited to the session
 * module's webroot.
 */
internal suspend fun serveWebUiCall(
    client: ChannelClient,
    channel: ChannelClient.Channel,
    backend: WebUiBackend,
    ensurePackages: suspend () -> Unit,
) {
    try {
        val input = DataInputStream(Tasks.await(client.getInputStream(channel)).buffered())
        val output = DataOutputStream(Tasks.await(client.getOutputStream(channel)).buffered())
        with(WearWebUiProtocol) {
            val request = JSONObject(input.readFrame().second.decodeToString())
            val moduleId = WearWebUiSession.moduleFor(request.optString("token"), channel.nodeId)
            if (moduleId == null) {
                output.writeFrame(FRAME_ERROR, ByteArray(0))
                return
            }
            val arg = request.optString("arg")
            fun answer(payload: ByteArray?) =
                if (payload == null) output.writeFrame(FRAME_ERROR, ByteArray(0)) else output.writeFrame(FRAME_OK, payload)
            when (request.optString("op")) {
                "exec" -> answer(backend.execute(arg).toJson().toByteArray())
                "modules" -> answer(backend.listModules().toByteArray())
                "packages" -> { ensurePackages(); answer(backend.listPackages(arg).toByteArray()) }
                "packagesInfo" -> { ensurePackages(); answer(backend.getPackagesInfo(arg).toByteArray()) }
                "icon" -> answer(backend.iconPng(arg, request.optInt("extra", 128)))
                "file" -> {
                    val webRoot = File("/data/adb/modules/$moduleId/webroot")
                    val file = File(arg).normalize()
                    answer(if (file.path.startsWith(webRoot.path + "/")) backend.openFile(file.path)?.use { it.readBytes() } else null)
                }
                "spawn" -> {
                    val done = java.util.concurrent.CountDownLatch(1)
                    backend.spawn(arg).start(
                        onStdout = { synchronized(output) { output.writeFrame(FRAME_STDOUT, it.toByteArray()) } },
                        onStderr = { synchronized(output) { output.writeFrame(FRAME_STDERR, it.toByteArray()) } },
                        onComplete = { result ->
                            synchronized(output) { output.writeFrame(FRAME_EXIT, result.toJson().toByteArray()) }
                            done.countDown()
                        },
                    )
                    done.await()
                }
                else -> answer(null)
            }
        }
    } catch (_: Exception) {
        // The phone closed the channel or the connection dropped; the call simply ends.
    } finally {
        client.close(channel)
    }
}
