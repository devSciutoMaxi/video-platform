package com.videoplatform.storage

import com.videoplatform.testProps
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.util.function.Consumer

class S3VideoStorageTest {

    private val s3 = mockk<S3Client>()
    private val presigner = S3Presigner.builder()
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .build()
    private val storage = S3VideoStorage(s3, presigner, testProps())

    private fun headFails(status: Int) {
        val error = S3Exception.builder().statusCode(status).message("error").build()
        every { s3.headObject(any<Consumer<HeadObjectRequest.Builder>>()) } throws error
    }

    @Test
    fun `la URL prefirmada apunta al bucket y a la key y vence segun la configuracion`() {
        val upload = storage.presignUpload("videos/abc/clip.mp4", "video/mp4", 1024)

        assertThat(upload.method).isEqualTo("PUT")
        assertThat(upload.url).contains("bucket").contains("videos/abc/clip.mp4").contains("X-Amz-Expires=300")
    }

    @Test
    fun `la firma incluye el content-type y el tamaño`() {
        val upload = storage.presignUpload("videos/abc/clip.mp4", "video/mp4", 1024)

        assertThat(upload.url).containsIgnoringCase("content-length").containsIgnoringCase("content-type")
    }

    @Test
    fun `devuelve el content-type como header a enviar y omite host y content-length`() {
        val upload = storage.presignUpload("videos/abc/clip.mp4", "video/mp4", 1024)

        assertThat(upload.headers.entries.single { it.key.equals("content-type", ignoreCase = true) }.value)
            .isEqualTo("video/mp4")
        assertThat(upload.headers.keys.map { it.lowercase() }).doesNotContain("host", "content-length")
    }

    @Test
    fun `head devuelve el tamaño y el content-type del objeto`() {
        every { s3.headObject(any<Consumer<HeadObjectRequest.Builder>>()) } returns
            HeadObjectResponse.builder().contentLength(2048).contentType("video/mp4").build()

        val stored = storage.head("videos/abc/clip.mp4")

        assertThat(stored?.sizeBytes).isEqualTo(2048)
        assertThat(stored?.contentType).isEqualTo("video/mp4")
    }

    @Test
    fun `head devuelve null si el objeto no existe`() {
        headFails(404)

        assertThat(storage.head("videos/abc/clip.mp4")).isNull()
    }

    @Test
    fun `head propaga los demas errores para que el mensaje se reintente`() {
        headFails(500)

        assertThatThrownBy { storage.head("videos/abc/clip.mp4") }.isInstanceOf(S3Exception::class.java)
    }
}
