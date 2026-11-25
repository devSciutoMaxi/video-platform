package com.videoplatform.messaging.consumer

import com.videoplatform.messaging.event.ViewedEvent
import com.videoplatform.persistence.ProcessedEventRepository
import com.videoplatform.persistence.VideoRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ViewedEventsHandler(
    private val videos: VideoRepository,
    private val events: ProcessedEventRepository,
) {

    @Transactional
    fun handle(event: ViewedEvent) {
        if (events.markProcessed(CONSUMER, event.eventId) == 1) {
            videos.incrementViews(event.videoId)
        }
    }

    companion object {
        const val CONSUMER = "video-viewed"
    }
}
