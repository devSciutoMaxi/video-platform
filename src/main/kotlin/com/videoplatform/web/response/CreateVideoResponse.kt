package com.videoplatform.web.response

import com.videoplatform.domain.model.VideoStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class CreateVideoResponse(
    @field:Schema(example = "3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11")
    val videoId: UUID,
    val status: VideoStatus,
    @field:Schema(description = "Cómo subir el archivo. Es null si el video ya no está PENDING (ya se procesó o expiró).")
    val upload: UploadInstructions?,
)
