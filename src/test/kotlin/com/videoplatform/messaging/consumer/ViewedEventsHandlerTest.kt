package com.videoplatform.messaging.consumer

import com.videoplatform.messaging.event.ViewedEvent
import com.videoplatform.persistence.ProcessedEventRepository
import com.videoplatform.persistence.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ViewedEventsHandlerTest {

    private val videos = mockk<VideoRepository>()
    private val events = mockk<ProcessedEventRepository>()
    private val handler = ViewedEventsHandler(videos, events)

    private val event = ViewedEvent("event-1", UUID.randomUUID(), "user-1", Instant.now())

    @Test
    fun `la primera vez que llega el evento suma una vista`() {
        every { events.markProcessed("video-viewed", "event-1") } returns 1
        every { videos.incrementViews(event.videoId) } returns 1

        handler.handle(event)

        verify(exactly = 1) { videos.incrementViews(event.videoId) }
    }

    @Test
    fun `un evento reentregado no suma otra vista`() {
        every { events.markProcessed("video-viewed", "event-1") } returns 0

        handler.handle(event)

        verify(exactly = 0) { videos.incrementViews(any()) }
    }
}
