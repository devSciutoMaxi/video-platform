package com.videoplatform.service.command

data class CreateVideoCommand(
    val title: String,
    val description: String?,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
)
