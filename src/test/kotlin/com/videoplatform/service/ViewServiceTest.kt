package com.videoplatform.service

import com.videoplatform.domain.exception.NotFoundException
import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.messaging.event.ViewedEvent
import com.videoplatform.messaging.publisher.ViewEventPublisher
import com.videoplatform.persistence.VideoRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ViewServiceTest {

    private val videos = mockk<VideoRepository>()
    private val publisher = mockk<ViewEventPublisher>()
    private val service = ViewService(videos, publisher)

    @Test
    fun `no registra vistas de un video que no esta READY`() {
        val id = UUID.randomUUID()
        every { videos.existsByIdAndStatus(id, VideoStatus.READY) } returns false

        assertThatThrownBy { service.recordView(id, "user-1") }.isInstanceOf(NotFoundException::class.java)
        verify(exactly = 0) { publisher.publish(any()) }
    }

    @Test
    fun `publica un evento con id propio y no toca el contador`() {
        val id = UUID.randomUUID()
        val event = slot<ViewedEvent>()
        every { videos.existsByIdAndStatus(id, VideoStatus.READY) } returns true
        every { publisher.publish(capture(event)) } just runs

        service.recordView(id, "user-1")

        assertThat(event.captured.videoId).isEqualTo(id)
        assertThat(event.captured.userId).isEqualTo("user-1")
        assertThat(event.captured.eventId).isNotBlank()
        verify(exactly = 0) { videos.incrementViews(any()) }
    }

    @Test
    fun `cada reproduccion genera un eventId distinto`() {
        val id = UUID.randomUUID()
        val events = mutableListOf<ViewedEvent>()
        every { videos.existsByIdAndStatus(id, VideoStatus.READY) } returns true
        every { publisher.publish(capture(events)) } just runs

        service.recordView(id, "user-1")
        service.recordView(id, "user-1")

        assertThat(events.map { it.eventId }).doesNotHaveDuplicates().hasSize(2)
    }
}
