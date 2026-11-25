package com.videoplatform.service

import com.videoplatform.domain.exception.InvalidRequestException
import com.videoplatform.domain.model.VideoStatus
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

class SearchServiceTest {

    private val videos = mockk<VideoRepository>()
    private val service = SearchService(videos)

    @Test
    fun `rechaza una consulta en blanco sin tocar la base`() {
        assertThatThrownBy { service.search("   ", PageRequest.of(0, 10)) }
            .isInstanceOf(InvalidRequestException::class.java)
        verify(exactly = 0) { videos.findByStatusAndTitleContainingIgnoreCase(any(), any(), any<Pageable>()) }
    }

    @Test
    fun `busca solo videos READY con el texto sin espacios y orden fijo`() {
        val video = testVideo(status = VideoStatus.READY)
        val pageable = slot<Pageable>()
        every {
            videos.findByStatusAndTitleContainingIgnoreCase(VideoStatus.READY, "ana", capture(pageable))
        } returns PageImpl(listOf(video))

        val result = service.search("  ana ", PageRequest.of(2, 10, Sort.by("title")))

        assertThat(pageable.captured.pageNumber).isEqualTo(2)
        assertThat(pageable.captured.pageSize).isEqualTo(10)
        assertThat(pageable.captured.sort).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        assertThat(result.content.single().id).isEqualTo(video.id)
        assertThat(result.content.single().playbackUrl).isEqualTo(video.cdnUrl)
    }
}
