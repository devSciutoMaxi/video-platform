package com.videoplatform.web.response

import com.videoplatform.domain.model.PresignedUpload
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class UploadInstructions(
    @field:Schema(description = "URL prefirmada de S3. Se sube el archivo con un PUT directo a esta URL.")
    val url: String,
    @field:Schema(example = "PUT")
    val method: String,
    @field:Schema(description = "Headers que hay que enviar tal cual en el PUT", example = "{\"Content-Type\": \"video/mp4\"}")
    val headers: Map<String, String>,
    @field:Schema(description = "Hasta cuándo es válida la URL (15 minutos por defecto)")
    val expiresAt: Instant,
) {
    companion object {
        fun from(upload: PresignedUpload) =
            UploadInstructions(upload.url, upload.method, upload.headers, upload.expiresAt)
    }
}
