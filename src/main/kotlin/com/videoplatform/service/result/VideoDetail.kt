package com.videoplatform.service.result

import com.videoplatform.domain.entity.Video

data class VideoDetail(
    val video: Video,
    val favorite: Boolean,
    val isOwner: Boolean,
)
