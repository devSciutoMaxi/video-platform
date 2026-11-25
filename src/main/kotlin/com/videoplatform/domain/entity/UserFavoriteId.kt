package com.videoplatform.domain.entity

import java.io.Serializable
import java.util.UUID

data class UserFavoriteId(
    val userId: String = "",
    val videoId: UUID = UUID(0L, 0L),
) : Serializable
