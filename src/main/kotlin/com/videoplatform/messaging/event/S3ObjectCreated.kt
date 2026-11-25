package com.videoplatform.messaging.event

data class S3ObjectCreated(
    val key: String,
    val eventId: String,
)
