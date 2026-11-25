package com.videoplatform.web.error

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Formato de todos los errores de la API")
data class ErrorBody(
    @field:Schema(example = "VALIDATION_ERROR", description = "Código estable para que el cliente decida qué hacer")
    val code: String,
    @field:Schema(example = "Request validation failed")
    val message: String,
    @field:Schema(description = "Detalle por campo cuando aplica")
    val details: List<String> = emptyList(),
)
