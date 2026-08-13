package com.resukisu.resukisu.domain.model

import java.io.InputStream

data class WebResource(
    val mimeType: String,
    val encoding: String,
    val body: InputStream,
)
