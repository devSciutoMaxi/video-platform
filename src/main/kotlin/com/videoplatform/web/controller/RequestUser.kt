package com.videoplatform.web.controller

import com.videoplatform.domain.exception.InvalidRequestException

// Autenticación simulada con X-User-Id; en producción vendría de un JWT.
internal fun requireUserId(raw: String): String =
    raw.trim().ifEmpty { throw InvalidRequestException("X-User-Id must not be blank") }
