package com.videoplatform.domain.validation

import com.videoplatform.domain.model.StoredObject
import com.videoplatform.domain.model.ValidationResult
import com.videoplatform.testProps
import com.videoplatform.testVideo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UploadValidatorTest {

    private val validator = UploadValidator(testProps(maxVideoSizeBytes = 10_000))

    @Test
    fun `un archivo que coincide con lo declarado es valido`() {
        val result = validator.validate(testVideo(sizeBytes = 2048), StoredObject(2048, "video/mp4"))
        assertThat(result).isEqualTo(ValidationResult.Valid)
    }

    @Test
    fun `el content-type con parametros se normaliza`() {
        val result = validator.validate(testVideo(), StoredObject(1024, "Video/MP4; charset=binary"))
        assertThat(result).isEqualTo(ValidationResult.Valid)
    }

    @Test
    fun `si el objeto no existe en S3 se rechaza`() {
        val result = validator.validate(testVideo(), null)
        assertThat(result).isEqualTo(ValidationResult.Invalid("OBJECT_NOT_FOUND"))
    }

    @Test
    fun `un archivo vacio se rechaza`() {
        val result = validator.validate(testVideo(), StoredObject(0, "video/mp4"))
        assertThat(result).isEqualTo(ValidationResult.Invalid("EMPTY_FILE"))
    }

    @Test
    fun `un archivo mas grande que el maximo se rechaza`() {
        val result = validator.validate(testVideo(sizeBytes = 20_000), StoredObject(20_000, "video/mp4"))
        assertThat(result).isEqualTo(ValidationResult.Invalid("FILE_TOO_LARGE"))
    }

    @Test
    fun `si el tamano real no coincide con el declarado se rechaza`() {
        val result = validator.validate(testVideo(sizeBytes = 1024), StoredObject(2048, "video/mp4"))
        assertThat(result).isEqualTo(ValidationResult.Invalid("SIZE_MISMATCH"))
    }

    @Test
    fun `un tipo no permitido se rechaza`() {
        val result = validator.validate(testVideo(), StoredObject(1024, "application/pdf"))
        assertThat(result).isEqualTo(ValidationResult.Invalid("UNSUPPORTED_CONTENT_TYPE"))
    }

    @Test
    fun `sin content-type se rechaza`() {
        val result = validator.validate(testVideo(), StoredObject(1024, null))
        assertThat(result).isEqualTo(ValidationResult.Invalid("UNSUPPORTED_CONTENT_TYPE"))
    }

    @Test
    fun `un tipo permitido pero distinto al declarado se rechaza`() {
        val result = validator.validate(testVideo(contentType = "video/mp4"), StoredObject(1024, "video/webm"))
        assertThat(result).isEqualTo(ValidationResult.Invalid("CONTENT_TYPE_MISMATCH"))
    }
}
