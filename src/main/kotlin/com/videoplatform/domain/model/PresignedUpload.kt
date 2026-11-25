package com.videoplatform.domain.model

import java.time.Instant

data class PresignedUpload(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val expiresAt: Instant,
)
