package com.resukisu.resukisu.domain.model

data class WebUiCommandResult(
    val code: Int,
    val stdout: String,
    val stderr: String,
)
