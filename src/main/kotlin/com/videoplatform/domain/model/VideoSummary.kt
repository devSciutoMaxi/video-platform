package com.videoplatform.domain.model

import java.time.Instant
import java.util.UUID

data class VideoSummary(
    val id: UUID,
    val title: String,
    val description: String?,
    val views: Long,
    val playbackUrl: String,
    val createdAt: Instant,
)
