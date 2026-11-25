package com.videoplatform.domain.model

import java.time.Instant

data class FavoriteItem(
    val video: VideoSummary,
    val favoritedAt: Instant,
)
