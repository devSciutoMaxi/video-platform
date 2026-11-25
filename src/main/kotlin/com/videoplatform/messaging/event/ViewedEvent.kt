package com.videoplatform.messaging.event

import java.time.Instant
import java.util.UUID

data class ViewedEvent(
    val eventId: String,
    val videoId: UUID,
    val userId: String,
    val occurredAt: Instant,
)
