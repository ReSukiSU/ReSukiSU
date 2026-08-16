package com.resukisu.resukisu.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class NetworkResponse(
    val body: String?,
    val headers: Map<String, List<String>>,
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()
}

class NetworkRequestRepository(
    private val networkStatusRepository: NetworkStatusRepository,
    private val httpClient: OkHttpClient,
) {
    suspend fun fetch(
        url: String,
        headers: Map<String, String> = emptyMap(),
        forceNetwork: Boolean = false,
        callTimeoutSeconds: Long? = null,
    ): Result<String> = request(
        url = url,
        headers = headers,
        forceNetwork = forceNetwork,
        callTimeoutSeconds = callTimeoutSeconds,
    ).mapCatching { response ->
        response.body ?: error("Empty response")
    }

    suspend fun request(
        url: String,
        headers: Map<String, String> = emptyMap(),
        forceNetwork: Boolean = false,
        callTimeoutSeconds: Long? = null,
    ): Result<NetworkResponse> = withContext(Dispatchers.IO) {
        if (!networkStatusRepository.isAvailable()) {
            return@withContext Result.failure(IllegalStateException("offline"))
        }

        runCatching {
            val request = Request.Builder()
                .url(url)
                .apply {
                    headers.forEach { (name, value) -> header(name, value) }
                    if (forceNetwork) cacheControl(CacheControl.FORCE_NETWORK)
                }
                .build()
            val call = httpClient.newCall(request)
            callTimeoutSeconds?.let {
                call.timeout().timeout(it, TimeUnit.SECONDS)
            }
            call.execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                NetworkResponse(
                    body = response.body?.string(),
                    headers = response.headers.toMultimap(),
                )
            }
        }
    }
}
