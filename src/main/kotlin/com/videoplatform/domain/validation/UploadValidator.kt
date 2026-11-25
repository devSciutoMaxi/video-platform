package com.videoplatform.domain.validation

import com.videoplatform.config.AppProperties
import com.videoplatform.domain.entity.Video
import com.videoplatform.domain.model.StoredObject
import com.videoplatform.domain.model.ValidationResult
import org.springframework.stereotype.Component

@Component
class UploadValidator(private val props: AppProperties) {

    fun validate(video: Video, stored: StoredObject?): ValidationResult {
        if (stored == null) return ValidationResult.Invalid("OBJECT_NOT_FOUND")
        if (stored.sizeBytes <= 0) return ValidationResult.Invalid("EMPTY_FILE")
        if (stored.sizeBytes > props.maxVideoSizeBytes) return ValidationResult.Invalid("FILE_TOO_LARGE")
        if (stored.sizeBytes != video.sizeBytes) return ValidationResult.Invalid("SIZE_MISMATCH")

        val contentType = stored.contentType?.substringBefore(';')?.trim()?.lowercase()
        if (contentType == null || contentType !in props.allowedContentTypes) {
            return ValidationResult.Invalid("UNSUPPORTED_CONTENT_TYPE")
        }
        if (contentType != video.contentType.lowercase()) {
            return ValidationResult.Invalid("CONTENT_TYPE_MISMATCH")
        }
        return ValidationResult.Valid
    }
}
