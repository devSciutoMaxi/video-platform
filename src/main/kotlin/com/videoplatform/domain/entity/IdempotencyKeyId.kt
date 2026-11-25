package com.videoplatform.domain.entity

import java.io.Serializable

data class IdempotencyKeyId(
    val userId: String = "",
    val idemKey: String = "",
) : Serializable
