package com.videoplatform.service

import com.videoplatform.domain.exception.NotFoundException
import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.messaging.event.ViewedEvent
import com.videoplatform.messaging.publisher.ViewEventPublisher
import com.videoplatform.persistence.VideoRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

// Write-behind: publica el evento y responde; el contador lo actualiza el consumer.
@Service
class ViewService(
    private val videos: VideoRepository,
    private val publisher: ViewEventPublisher,
) {

    fun recordView(videoId: UUID, userId: String) {
        if (!videos.existsByIdAndStatus(videoId, VideoStatus.READY)) throw NotFoundException("Video not found")
        publisher.publish(
            ViewedEvent(
                eventId = UUID.randomUUID().toString(),
                videoId = videoId,
                userId = userId,
                occurredAt = Instant.now(),
            )
        )
    }
}
