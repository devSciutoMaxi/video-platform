package com.videoplatform.service

import com.videoplatform.domain.entity.UserFavorite
import com.videoplatform.domain.exception.NotFoundException
import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.persistence.FavoriteRepository
import com.videoplatform.persistence.VideoRepository
import com.videoplatform.testVideo
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.time.Instant
import java.util.UUID

class FavoriteServiceTest {

    private val favorites = mockk<FavoriteRepository>()
    private val videos = mockk<VideoRepository>()
    private val service = FavoriteService(favorites, videos)

    @Test
    fun `no permite marcar como favorito un video que no esta READY`() {
        val id = UUID.randomUUID()
        every { videos.existsByIdAndStatus(id, VideoStatus.READY) } returns false

        assertThatThrownBy { service.add("user-1", id) }.isInstanceOf(NotFoundException::class.java)
        verify(exactly = 0) { favorites.addIfAbsent(any(), any()) }
    }

    @Test
    fun `agrega el favorito de un video READY`() {
        val id = UUID.randomUUID()
        every { videos.existsByIdAndStatus(id, VideoStatus.READY) } returns true
        every { favorites.addIfAbsent("user-1", id) } returns 1

        service.add("user-1", id)

        verify { favorites.addIfAbsent("user-1", id) }
    }

    @Test
    fun `quitar algo que no es favorito no es un error`() {
        val id = UUID.randomUUID()
        every { favorites.removeIfPresent("user-1", id) } returns 0

        service.remove("user-1", id)

        verify { favorites.removeIfPresent("user-1", id) }
    }

    @Test
    fun `lista los favoritos mas recientes primero ignorando el sort del cliente`() {
        val readyVideo = testVideo(status = VideoStatus.READY)
        val favoritedAt = Instant.parse("2026-01-01T00:00:00Z")
        val favorite = UserFavorite("user-1", readyVideo.id, favoritedAt).apply { video = readyVideo }
        val pageable = slot<Pageable>()
        every {
            favorites.findByUserIdAndVideoStatus("user-1", VideoStatus.READY, capture(pageable))
        } returns PageImpl(listOf(favorite))

        val result = service.list("user-1", PageRequest.of(1, 5, Sort.by("title")))

        assertThat(pageable.captured.pageNumber).isEqualTo(1)
        assertThat(pageable.captured.pageSize).isEqualTo(5)
        assertThat(pageable.captured.sort)
            .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("videoId")))
        assertThat(result.content.single().video.id).isEqualTo(readyVideo.id)
        assertThat(result.content.single().favoritedAt).isEqualTo(favoritedAt)
    }
}
