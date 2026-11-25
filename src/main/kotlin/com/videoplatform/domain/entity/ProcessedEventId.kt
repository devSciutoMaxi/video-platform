package com.videoplatform.domain.entity

import java.io.Serializable

data class ProcessedEventId(
    val consumer: String = "",
    val eventId: String = "",
) : Serializable
