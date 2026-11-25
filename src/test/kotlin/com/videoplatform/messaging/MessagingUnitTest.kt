package com.videoplatform.messaging

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.videoplatform.messaging.event.S3ObjectCreated
import com.videoplatform.messaging.support.S3EventParser
import com.videoplatform.service.VideoService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class S3EventParserTest {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    private fun event(key: String, sequencer: String? = "0055AED6DCD90281E5", eventName: String = "ObjectCreated:Put"): String {
        val seq = sequencer?.let { """"sequencer":"$it",""" } ?: ""
        return """
            {"Records":[{"eventName":"$eventName","s3":{"bucket":{"name":"b"},"object":{$seq"key":"$key","size":1024,"eTag":"abc123"}}}]}
        """.trimIndent()
    }

    @Test
    fun `extrae la key y arma el eventId con el sequencer`() {
        val parsed = S3EventParser.parse(mapper, event("videos/1/clip.mp4"))
        assertThat(parsed).containsExactly(
            S3ObjectCreated("videos/1/clip.mp4", "videos/1/clip.mp4#0055AED6DCD90281E5")
        )
    }

    @Test
    fun `si no hay sequencer usa el eTag`() {
        val parsed = S3EventParser.parse(mapper, event("videos/1/clip.mp4", sequencer = null))
        assertThat(parsed.single().eventId).isEqualTo("videos/1/clip.mp4#abc123")
    }

    @Test
    fun `decodifica la key codificada como URL`() {
        val parsed = S3EventParser.parse(mapper, event("videos/1/mi+video%C3%A9.mp4"))
        assertThat(parsed.single().key).isEqualTo("videos/1/mi videoé.mp4")
    }

    @Test
    fun `ignora el evento de prueba de S3`() {
        val body = """{"Service":"Amazon S3","Event":"s3:TestEvent","Bucket":"b"}"""
        assertThat(S3EventParser.parse(mapper, body)).isEmpty()
    }

    @Test
    fun `ignora eventos que no son de creacion`() {
        val parsed = S3EventParser.parse(mapper, event("videos/1/clip.mp4", eventName = "ObjectRemoved:Delete"))
        assertThat(parsed).isEmpty()
    }

    @Test
    fun `un cuerpo que no es JSON lanza excepcion para que termine en la DLQ`() {
        assertThatThrownBy { S3EventParser.parse(mapper, "not-json{{{") }
            .isInstanceOf(JsonProcessingException::class.java)
    }
}

class SanitizeFileNameTest {

    @Test
    fun `reemplaza caracteres no seguros`() {
        assertThat(VideoService.sanitizeFileName("mi video (1).mp4")).isEqualTo("mi_video__1_.mp4")
    }

    @Test
    fun `evita path traversal`() {
        assertThat(VideoService.sanitizeFileName("../../etc/passwd")).doesNotContain("/")
    }

    @Test
    fun `limita el largo conservando la extension`() {
        val result = VideoService.sanitizeFileName("a".repeat(300) + ".mp4")
        assertThat(result).hasSize(100).endsWith(".mp4")
    }
}
