package com.resukisu.resukisu.domain.model

enum class DownloadStatus { PENDING, DOWNLOADING, COMPLETED, FAILED }

data class DownloadState(
    val id: Int,
    val fileName: String,
    val url: String,
    val progress: Int = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val resultUri: String? = null,
    val error: String? = null,
)

