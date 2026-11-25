package com.videoplatform.messaging.consumer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.videoplatform.domain.entity.Video
import com.videoplatform.domain.model.StoredObject
import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.domain.validation.UploadValidator
import com.videoplatform.noOpTransactions
import com.videoplatform.persistence.ProcessedEventRepository
import com.videoplatform.persistence.VideoRepository
import com.videoplatform.storage.VideoStorage
import com.videoplatform.testProps
import com.videoplatform.testVideo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UploadedEventHandlerTest {

    private val videos = mockk<VideoRepository>()
    private val events = mockk<ProcessedEventRepository>()
    private val storage = mockk<VideoStorage>()
    private val handler = UploadedEventHandler(
        videos, events, storage, UploadValidator(testProps()), jacksonObjectMapper(), noOpTransactions(),
    )

    private val video = testVideo(sizeBytes = 1024, s3Key = "videos/abc/clip.mp4")
    private val eventId = "videos/abc/clip.mp4#01"
    private val body = """
        {"Records":[{"eventName":"ObjectCreated:Put","s3":{"bucket":{"name":"b"},
        "object":{"sequencer":"01","key":"videos/abc/clip.mp4","size":1024,"eTag":"e"}}}]}
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        every { events.existsByConsumerAndEventId(any(), any()) } returns false
        every { events.markProcessed(any(), any()) } returns 1
        every { videos.findByS3Key(video.s3Key) } returns video
        every { videos.markReady(any()) } returns 1
        every { videos.markFailed(any(), any()) } returns 1
    }

    @Test
    fun `un archivo valido pasa el video a READY`() {
        every { storage.head(video.s3Key) } returns StoredObject(1024, "video/mp4")

        handler.handle(body)

        verify { events.markProcessed("video-uploaded", eventId) }
        verify { videos.markReady(video.id) }
        verify(exactly = 0) { videos.markFailed(any(), any()) }
    }

    @Test
    fun `un archivo con otro tamaño pasa el video a FAILED con el motivo`() {
        every { storage.head(video.s3Key) } returns StoredObject(2048, "video/mp4")

        handler.handle(body)

        verify { videos.markFailed(video.id, "SIZE_MISMATCH") }
        verify(exactly = 0) { videos.markReady(any()) }
    }

    @Test
    fun `si el objeto no existe en S3 el video queda FAILED`() {
        every { storage.head(video.s3Key) } returns null

        handler.handle(body)

        verify { videos.markFailed(video.id, "OBJECT_NOT_FOUND") }
    }

    @Test
    fun `un evento ya procesado se ignora sin consultar S3`() {
        every { events.existsByConsumerAndEventId("video-uploaded", eventId) } returns true

        handler.handle(body)

        verify(exactly = 0) { storage.head(any()) }
        verify(exactly = 0) { videos.markReady(any()) }
        verify(exactly = 0) { videos.markFailed(any(), any()) }
    }

    @Test
    fun `un evento de una key desconocida se ignora`() {
        every { videos.findByS3Key(any()) } returns null

        handler.handle(body)

        verify(exactly = 0) { storage.head(any()) }
        verify(exactly = 0) { events.markProcessed(any(), any()) }
    }

    @Test
    fun `un evento tardio sobre un video que ya no esta PENDING se ignora`() {
        val ready: Video = testVideo(status = VideoStatus.READY, s3Key = video.s3Key)
        every { videos.findByS3Key(video.s3Key) } returns ready

        handler.handle(body)

        verify(exactly = 0) { storage.head(any()) }
        verify(exactly = 0) { videos.markReady(any()) }
    }

    @Test
    fun `si otro consumer ya marco el evento no cambia el estado`() {
        every { storage.head(video.s3Key) } returns StoredObject(1024, "video/mp4")
        every { events.markProcessed(any(), any()) } returns 0

        handler.handle(body)

        verify(exactly = 0) { videos.markReady(any()) }
        verify(exactly = 0) { videos.markFailed(any(), any()) }
    }

    @Test
    fun `si S3 falla propaga el error para que el mensaje se reintente`() {
        every { storage.head(video.s3Key) } throws IllegalStateException("S3 caido")

        assertThatThrownBy { handler.handle(body) }.isInstanceOf(IllegalStateException::class.java)
        verify(exactly = 0) { events.markProcessed(any(), any()) }
    }

    @Test
    fun `un cuerpo que no es JSON propaga el error`() {
        assertThatThrownBy { handler.handle("not-json{{{") }.isInstanceOf(Exception::class.java)
    }
}
