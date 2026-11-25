package com.videoplatform.service

import com.videoplatform.domain.entity.IdempotencyKey
import com.videoplatform.domain.exception.ConflictException
import com.videoplatform.domain.exception.InvalidRequestException
import com.videoplatform.domain.exception.NotFoundException
import com.videoplatform.domain.exception.UnprocessableException
import com.videoplatform.domain.entity.Video
import com.videoplatform.domain.model.PresignedUpload
import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.domain.util.RequestHasher
import com.videoplatform.noOpTransactions
import com.videoplatform.persistence.FavoriteRepository
import com.videoplatform.persistence.IdempotencyRepository
import com.videoplatform.persistence.VideoRepository
import com.videoplatform.service.command.CreateVideoCommand
import com.videoplatform.storage.VideoStorage
import com.videoplatform.testProps
import com.videoplatform.testVideo
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class VideoServiceTest {

    private val videos = mockk<VideoRepository>()
    private val idempotency = mockk<IdempotencyRepository>()
    private val favorites = mockk<FavoriteRepository>()
    private val storage = mockk<VideoStorage>()
    private val service = VideoService(videos, idempotency, favorites, storage, testProps(), noOpTransactions())

    private val upload = PresignedUpload(
        url = "http://s3.test/upload",
        method = "PUT",
        headers = mapOf("content-type" to "video/mp4"),
        expiresAt = Instant.now().plusSeconds(300),
    )

    private val command = CreateVideoCommand(
        title = "Mi video",
        description = "Demo",
        fileName = "clip.mp4",
        contentType = "video/mp4",
        sizeBytes = 1024,
    )

    @BeforeEach
    fun setUp() {
        every { idempotency.findById(any()) } returns Optional.empty()
        every { idempotency.insert(any(), any(), any(), any()) } returns 1
        every { storage.presignUpload(any(), any(), any()) } returns upload
    }

    private fun hashOf(c: CreateVideoCommand) =
        RequestHasher.hash(c.title, c.description, c.fileName, c.contentType, c.sizeBytes.toString())

    private fun recordFor(video: Video, hash: String) =
        IdempotencyKey("user-1", "key-1", hash, video.id, Instant.now())

    @Test
    fun `crea el video en PENDING y devuelve la URL de subida`() {
        val saved = slot<Video>()
        every { videos.saveAndFlush(capture(saved)) } answers { saved.captured }

        val result = service.createUpload("user-1", "key-1", command)

        assertThat(result.replayed).isFalse()
        assertThat(result.upload).isEqualTo(upload)
        assertThat(saved.captured.status).isEqualTo(VideoStatus.PENDING)
        assertThat(saved.captured.ownerId).isEqualTo("user-1")
        assertThat(saved.captured.s3Key).startsWith("videos/${saved.captured.id}/")
        assertThat(saved.captured.cdnUrl).isEqualTo("http://cdn.test/bucket/${saved.captured.s3Key}")
        verify { idempotency.insert("user-1", "key-1", hashOf(command), saved.captured.id) }
        verify { storage.presignUpload(saved.captured.s3Key, "video/mp4", 1024) }
    }

    @Test
    fun `normaliza los datos antes de guardar`() {
        val saved = slot<Video>()
        every { videos.saveAndFlush(capture(saved)) } answers { saved.captured }

        service.createUpload(
            "user-1", "key-1",
            command.copy(title = "  Mi video  ", description = "   ", contentType = " VIDEO/MP4 "),
        )

        assertThat(saved.captured.title).isEqualTo("Mi video")
        assertThat(saved.captured.description).isNull()
        assertThat(saved.captured.contentType).isEqualTo("video/mp4")
    }

    @Test
    fun `rechaza un content type no permitido`() {
        assertThatThrownBy { service.createUpload("user-1", "key-1", command.copy(contentType = "image/png")) }
            .isInstanceOf(UnprocessableException::class.java)
        verify(exactly = 0) { videos.saveAndFlush(any<Video>()) }
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1, 1_000_001])
    fun `rechaza un tamaño fuera de rango`(sizeBytes: Long) {
        assertThatThrownBy { service.createUpload("user-1", "key-1", command.copy(sizeBytes = sizeBytes)) }
            .isInstanceOf(UnprocessableException::class.java)
        verify(exactly = 0) { videos.saveAndFlush(any<Video>()) }
    }

    @Test
    fun `rechaza una Idempotency-Key vacia o demasiado larga`() {
        assertThatThrownBy { service.createUpload("user-1", " ", command) }
            .isInstanceOf(InvalidRequestException::class.java)
        assertThatThrownBy { service.createUpload("user-1", "k".repeat(201), command) }
            .isInstanceOf(InvalidRequestException::class.java)
    }

    @Test
    fun `un reintento con el mismo payload devuelve el mismo video y una URL nueva`() {
        val existing = testVideo(status = VideoStatus.PENDING)
        every { idempotency.findById(any()) } returns Optional.of(recordFor(existing, hashOf(command)))
        every { videos.findById(existing.id) } returns Optional.of(existing)

        val result = service.createUpload("user-1", "key-1", command)

        assertThat(result.replayed).isTrue()
        assertThat(result.video.id).isEqualTo(existing.id)
        assertThat(result.upload).isEqualTo(upload)
        verify(exactly = 0) { videos.saveAndFlush(any<Video>()) }
    }

    @Test
    fun `un reintento sobre un video que ya no esta PENDING no devuelve URL de subida`() {
        val existing = testVideo(status = VideoStatus.READY)
        every { idempotency.findById(any()) } returns Optional.of(recordFor(existing, hashOf(command)))
        every { videos.findById(existing.id) } returns Optional.of(existing)

        val result = service.createUpload("user-1", "key-1", command)

        assertThat(result.replayed).isTrue()
        assertThat(result.upload).isNull()
    }

    @Test
    fun `reusar la clave con otro payload es un conflicto`() {
        val existing = testVideo()
        every { idempotency.findById(any()) } returns Optional.of(recordFor(existing, hashOf(command)))

        assertThatThrownBy { service.createUpload("user-1", "key-1", command.copy(title = "Otro titulo")) }
            .isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `si otra request gano la carrera devuelve el resultado de esa request`() {
        val existing = testVideo(status = VideoStatus.PENDING)
        every { idempotency.findById(any()) } returnsMany
            listOf(Optional.empty<IdempotencyKey>(), Optional.of(recordFor(existing, hashOf(command))))
        every { videos.saveAndFlush(any<Video>()) } answers { firstArg<Video>() }
        every { idempotency.insert(any(), any(), any(), any()) } throws DataIntegrityViolationException("duplicate")
        every { videos.findById(existing.id) } returns Optional.of(existing)

        val result = service.createUpload("user-1", "key-1", command)

        assertThat(result.replayed).isTrue()
        assertThat(result.video.id).isEqualTo(existing.id)
    }

    @Test
    fun `el detalle de un video inexistente es 404`() {
        every { videos.findById(any()) } returns Optional.empty()

        assertThatThrownBy { service.getDetail(UUID.randomUUID(), "user-1") }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `un video que no esta READY solo lo ve su dueño`() {
        val video = testVideo(status = VideoStatus.PENDING)
        every { videos.findById(video.id) } returns Optional.of(video)
        every { favorites.existsByUserIdAndVideoId(any(), any()) } returns false

        assertThatThrownBy { service.getDetail(video.id, "otro-usuario") }
            .isInstanceOf(NotFoundException::class.java)
        assertThat(service.getDetail(video.id, "user-1").isOwner).isTrue()
    }

    @Test
    fun `un video READY lo ve cualquiera e indica si es favorito`() {
        val video = testVideo(status = VideoStatus.READY)
        every { videos.findById(video.id) } returns Optional.of(video)
        every { favorites.existsByUserIdAndVideoId("otro-usuario", video.id) } returns true

        val detail = service.getDetail(video.id, "otro-usuario")

        assertThat(detail.isOwner).isFalse()
        assertThat(detail.favorite).isTrue()
    }
}
