package com.videoplatform.web.error

import com.videoplatform.domain.exception.ConflictException
import com.videoplatform.domain.exception.InvalidRequestException
import com.videoplatform.domain.exception.NotFoundException
import com.videoplatform.domain.exception.UnprocessableException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class ErrorHandler {

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = respond(HttpStatus.NOT_FOUND, "NOT_FOUND", e.message)

    @ExceptionHandler(ConflictException::class)
    fun conflict(e: ConflictException) = respond(HttpStatus.CONFLICT, "CONFLICT", e.message)

    @ExceptionHandler(InvalidRequestException::class)
    fun invalid(e: InvalidRequestException) = respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.message)

    @ExceptionHandler(UnprocessableException::class)
    fun unprocessable(e: UnprocessableException) =
        respond(HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE", e.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(e: MethodArgumentNotValidException): ResponseEntity<ErrorBody> {
        val details = e.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details)
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun missingHeader(e: MissingRequestHeaderException) =
        respond(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "Missing required header: ${e.headerName}")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParam(e: MissingServletRequestParameterException) =
        respond(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Missing required parameter: ${e.parameterName}")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun typeMismatch(e: MethodArgumentTypeMismatchException) =
        respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid value for '${e.name}'")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(e: HttpMessageNotReadableException) =
        respond(HttpStatus.BAD_REQUEST, "MALFORMED_BODY", "Request body is missing or malformed")

    private fun respond(
        status: HttpStatus,
        code: String,
        message: String?,
        details: List<String> = emptyList(),
    ): ResponseEntity<ErrorBody> =
        ResponseEntity.status(status).body(ErrorBody(code, message ?: status.reasonPhrase, details))
}
