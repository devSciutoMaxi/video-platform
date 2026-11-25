package com.videoplatform.web.controller

import com.ninjasquad.springmockk.MockkBean
import com.videoplatform.domain.exception.NotFoundException
import com.videoplatform.domain.model.FavoriteItem
import com.videoplatform.domain.model.VideoSummary
import com.videoplatform.service.FavoriteService
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.util.UUID

@WebMvcTest(FavoriteController::class)
class FavoriteControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockkBean
    lateinit var favorites: FavoriteService

    private val videoId = UUID.randomUUID()
    private val base = "/users/me/favorites"

    @Test
    fun `agregar a favoritos responde 204`() {
        every { favorites.add("user-1", videoId) } just runs

        mvc.put("$base/$videoId") { header("X-User-Id", "user-1") }.andExpect { status { isNoContent() } }

        verify { favorites.add("user-1", videoId) }
    }

    @Test
    fun `agregar un video que no esta READY responde 404`() {
        every { favorites.add(any(), any()) } throws NotFoundException("Video not found")

        mvc.put("$base/$videoId") { header("X-User-Id", "user-1") }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun `quitar de favoritos responde 204`() {
        every { favorites.remove("user-1", videoId) } just runs

        mvc.delete("$base/$videoId") { header("X-User-Id", "user-1") }.andExpect { status { isNoContent() } }

        verify { favorites.remove("user-1", videoId) }
    }

    @Test
    fun `listar favoritos devuelve la pagina`() {
        val summary = VideoSummary(videoId, "Gatitos", null, 3, "http://cdn.test/x", Instant.now())
        val item = FavoriteItem(summary, Instant.parse("2026-01-01T00:00:00Z"))
        every { favorites.list("user-1", any()) } returns PageImpl(listOf(item), PageRequest.of(0, 20), 1)

        mvc.get(base) { header("X-User-Id", "user-1") }.andExpect {
            status { isOk() }
            jsonPath("$.content[0].video.id") { value(videoId.toString()) }
            jsonPath("$.page.totalElements") { value(1) }
        }
    }

    @Test
    fun `el listado respeta page y size y limita size a 50`() {
        val pageable = slot<Pageable>()
        every { favorites.list(any(), capture(pageable)) } returns PageImpl(emptyList<FavoriteItem>())

        mvc.get("$base?page=3&size=500") { header("X-User-Id", "user-1") }.andExpect { status { isOk() } }

        assertThat(pageable.captured.pageNumber).isEqualTo(3)
        assertThat(pageable.captured.pageSize).isEqualTo(50)
    }

    @Test
    fun `sin el header X-User-Id responde 400`() {
        mvc.get(base).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MISSING_HEADER") }
        }
    }

    @Test
    fun `un videoId que no es UUID responde 400`() {
        mvc.put("$base/abc") { header("X-User-Id", "user-1") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_PARAMETER") }
        }
    }
}
