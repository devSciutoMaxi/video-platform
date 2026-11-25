package com.videoplatform.web.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

// Nullable para reportar los campos faltantes como 400 con su nombre.
data class CreateVideoRequest(
    @field:NotBlank @field:Size(max = 200)
    @field:Schema(example = "Mi primer video")
    val title: String?,

    @field:Size(max = 5000)
    @field:Schema(example = "Demo del challenge")
    val description: String? = null,

    @field:NotBlank @field:Size(max = 255)
    @field:Schema(example = "demo.mp4")
    val fileName: String?,

    @field:NotBlank @field:Size(max = 100)
    @field:Schema(example = "video/mp4", description = "video/mp4, video/webm o video/quicktime")
    val contentType: String?,

    @field:NotNull @field:Positive
    @field:Schema(example = "1048576", description = "Tamaño exacto del archivo en bytes")
    val sizeBytes: Long?,
)
