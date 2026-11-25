package com.videoplatform.service.result

import com.videoplatform.domain.entity.Video
import com.videoplatform.domain.model.PresignedUpload

data class CreateVideoResult(
    val video: Video,
    val upload: PresignedUpload?,
    val replayed: Boolean,
)
