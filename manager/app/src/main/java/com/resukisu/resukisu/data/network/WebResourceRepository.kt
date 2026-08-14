package com.resukisu.resukisu.data.network

import com.resukisu.resukisu.domain.model.WebResource
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request

class WebResourceRepository(
    private val httpClient: OkHttpClient,
) {
    fun load(
        url: String,
        method: String,
        requestHeaders: Map<String, String>,
    ): WebResource? = runCatching {
        val response = httpClient.newCall(
            Request.Builder()
                .url(url)
                .method(method, null)
                .headers(requestHeaders.toHeaders())
                .build()
        ).execute()
        val contentTypes = response.header("content-type", "text/plain; charset=utf-8")
            ?.split(";\\s*".toRegex())
            .orEmpty()
        val mimeType = contentTypes.firstOrNull() ?: "image/*"
        val charset = contentTypes.getOrNull(1)
            ?.split("=\\s*".toRegex())
            ?.getOrNull(1)
            ?: "utf-8"
        val body = response.body ?: return null
        WebResource(mimeType, charset, body.byteStream())
    }.getOrNull()
}
