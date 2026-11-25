package com.videoplatform.web.controller

import com.ninjasquad.springmockk.MockkBean
import com.videoplatform.domain.exception.ConflictException
import com.videoplatform.domain.exception.InvalidRequestException
import com.videoplatform.domain.exception.NotFoundException
import com.videoplatform.domain.exception.UnprocessableException
import com.videoplatform.domain.model.PresignedUpload
import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.domain.model.VideoSummary
import com.videoplatform.service.SearchService
import com.videoplatform.service.VideoService
import com.videoplatform.service.ViewService
import com.videoplatform.service.command.CreateVideoCommand
import com.videoplatform.service.result.CreateVideoResult
import com.videoplatform.service.result.VideoDetail
import com.videoplatform.testVideo
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@WebMvcTest(VideoController::class)
class VideoControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockkBean
    lateinit var videos: VideoService

    @MockkBean
    lateinit var searchService: SearchService

    @MockkBean
    lateinit var views: ViewService

    private val validBody =
        """{"title":"Mi video","description":"Demo","fileName":"clip.mp4","contentType":"video/mp4","sizeBytes":1024}"""

    private val upload = PresignedUpload(
        url = "http://s3.test/upload",
        method = "PUT",
        headers = mapOf("content-type" to "video/mp4"),
        expiresAt = Instant.parse("2026-01-01T00:15:00Z"),
    )

    private fun postVideo(
        body: String = validBody,
        userId: String? = "user-1",
        key: String? = "key-1",
    ) = mvc.post("/videos") {
        userId?.let { header("X-User-Id", it) }
        key?.let { header("Idempotency-Key", it) }
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `crear un video responde 201 con las instrucciones de subida`() {
        val video = testVideo()
        every { videos.createUpload("user-1", "key-1", any()) } returns CreateVideoResult(video, upload, replayed = false)

        postVideo().andExpect {
            status { isCreated() }
            jsonPath("$.videoId") { value(video.id.toString()) }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.upload.url") { value("http://s3.test/upload") }
            jsonPath("$.upload.method") { value("PUT") }
        }
    }

    @Test
    fun `un reintento con la misma clave responde 200`() {
        val video = testVideo()
        every { videos.createUpload(any(), any(), any()) } returns CreateVideoResult(video, upload, replayed = true)

        postVideo().andExpect {
            status { isOk() }
            jsonPath("$.videoId") { value(video.id.toString()) }
        }
    }

    @Test
    fun `un reintento sobre un video ya procesado responde sin upload`() {
        val video = testVideo(status = VideoStatus.READY)
        every { videos.createUpload(any(), any(), any()) } returns CreateVideoResult(video, null, replayed = true)

        postVideo().andExpect {
            status { isOk() }
            jsonPath("$.status") { value("READY") }
            jsonPath("$.upload") { isEmpty() }
        }
    }

    @Test
    fun `pasa el usuario, la clave sin espacios y los datos del body al servicio`() {
        val command = slot<CreateVideoCommand>()
        every { videos.createUpload("user-1", "key-1", capture(command)) } returns
            CreateVideoResult(testVideo(), upload, replayed = false)

        postVideo(key = "  key-1  ").andExpect { status { isCreated() } }

        assertThat(command.captured).isEqualTo(
            CreateVideoCommand("Mi video", "Demo", "clip.mp4", "video/mp4", 1024),
        )
    }

    @Test
    fun `falta el header X-User-Id`() {
        postVideo(userId = null).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MISSING_HEADER") }
        }
    }

    @Test
    fun `falta el header Idempotency-Key`() {
        postVideo(key = null).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MISSING_HEADER") }
        }
    }

    @Test
    fun `un X-User-Id en blanco es una request invalida`() {
        postVideo(userId = "   ").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
        verify(exactly = 0) { videos.createUpload(any(), any(), any()) }
    }

    @Test
    fun `un body con campos invalidos responde 400 con el detalle por campo`() {
        val body = """{"title":"","fileName":"clip.mp4","contentType":"video/mp4","sizeBytes":0}"""

        val response = postVideo(body = body).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
        }.andReturn().response.contentAsString

        assertThat(response).contains("title:").contains("sizeBytes:")
        verify(exactly = 0) { videos.createUpload(any(), any(), any()) }
    }

    @Test
    fun `un campo obligatorio ausente se reporta por su nombre`() {
        val response = postVideo(body = """{"title":"x","contentType":"video/mp4","sizeBytes":10}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
        }.andReturn().response.contentAsString

        assertThat(response).contains("fileName:")
    }

    @Test
    fun `un body que no es JSON responde 400 MALFORMED_BODY`() {
        postVideo(body = "{no es json").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MALFORMED_BODY") }
        }
    }

    @Test
    fun `reusar la clave con otro payload responde 409`() {
        every { videos.createUpload(any(), any(), any()) } throws ConflictException("otra request")

        postVideo().andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONFLICT") }
        }
    }

    @Test
    fun `una regla de negocio violada responde 422`() {
        every { videos.createUpload(any(), any(), any()) } throws UnprocessableException("tipo no permitido")

        postVideo().andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("UNPROCESSABLE") }
        }
    }

    @Test
    fun `el detalle de un video READY incluye la playbackUrl y si es favorito`() {
        val video = testVideo(status = VideoStatus.READY)
        every { videos.getDetail(video.id, "user-1") } returns VideoDetail(video, favorite = true, isOwner = true)

        mvc.get("/videos/${video.id}") { header("X-User-Id", "user-1") }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("READY") }
            jsonPath("$.playbackUrl") { value(video.cdnUrl) }
            jsonPath("$.favorite") { value(true) }
        }
    }

    @Test
    fun `el detalle de un video PENDING no tiene playbackUrl`() {
        val video = testVideo(status = VideoStatus.PENDING)
        every { videos.getDetail(video.id, "user-1") } returns VideoDetail(video, favorite = false, isOwner = true)

        mvc.get("/videos/${video.id}") { header("X-User-Id", "user-1") }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.playbackUrl") { isEmpty() }
        }
    }

    @Test
    fun `el detalle de un video inexistente responde 404`() {
        val id = UUID.randomUUID()
        every { videos.getDetail(id, "user-1") } throws NotFoundException("Video not found")

        mvc.get("/videos/$id") { header("X-User-Id", "user-1") }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun `un id que no es UUID responde 400`() {
        mvc.get("/videos/no-es-un-uuid") { header("X-User-Id", "user-1") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_PARAMETER") }
        }
    }

    @Test
    fun `la busqueda devuelve la pagina de resultados`() {
        val summary = VideoSummary(UUID.randomUUID(), "Gatitos", null, 3, "http://cdn.test/x", Instant.now())
        every { searchService.search("gat", any()) } returns PageImpl(listOf(summary), PageRequest.of(0, 20), 1)

        mvc.get("/videos/search?q=gat") { header("X-User-Id", "user-1") }.andExpect {
            status { isOk() }
            jsonPath("$.content[0].title") { value("Gatitos") }
            jsonPath("$.page.totalElements") { value(1) }
        }
    }

    @Test
    fun `la busqueda usa pagina 0 y tamaño 20 por defecto`() {
        val pageable = slot<Pageable>()
        every { searchService.search(any(), capture(pageable)) } returns PageImpl(emptyList<VideoSummary>())

        mvc.get("/videos/search?q=gat") { header("X-User-Id", "user-1") }.andExpect { status { isOk() } }

        assertThat(pageable.captured.pageNumber).isEqualTo(0)
        assertThat(pageable.captured.pageSize).isEqualTo(20)
    }

    @Test
    fun `la busqueda limita el tamaño de pagina a 50`() {
        val pageable = slot<Pageable>()
        every { searchService.search(any(), capture(pageable)) } returns PageImpl(emptyList<VideoSummary>())

        mvc.get("/videos/search?q=gat&page=2&size=1000") { header("X-User-Id", "user-1") }
            .andExpect { status { isOk() } }

        assertThat(pageable.captured.pageNumber).isEqualTo(2)
        assertThat(pageable.captured.pageSize).isEqualTo(50)
    }

    @Test
    fun `la busqueda sin q responde 400`() {
        mvc.get("/videos/search") { header("X-User-Id", "user-1") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MISSING_PARAMETER") }
        }
    }

    @Test
    fun `una consulta en blanco responde 400`() {
        every { searchService.search(any(), any()) } throws InvalidRequestException("q must not be blank")

        mvc.get("/videos/search?q=%20") { header("X-User-Id", "user-1") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `registrar una vista responde 202`() {
        val id = UUID.randomUUID()
        every { views.recordView(id, "user-1") } just runs

        mvc.post("/videos/$id/views") { header("X-User-Id", "user-1") }.andExpect { status { isAccepted() } }

        verify { views.recordView(id, "user-1") }
    }

    @Test
    fun `registrar una vista de un video que no esta READY responde 404`() {
        val id = UUID.randomUUID()
        every { views.recordView(id, "user-1") } throws NotFoundException("Video not found")

        mvc.post("/videos/$id/views") { header("X-User-Id", "user-1") }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }
}
