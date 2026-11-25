package com.videoplatform.messaging.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.videoplatform.domain.model.ValidationResult
import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.domain.validation.UploadValidator
import com.videoplatform.messaging.event.S3ObjectCreated
import com.videoplatform.messaging.support.S3EventParser
import com.videoplatform.persistence.ProcessedEventRepository
import com.videoplatform.persistence.VideoRepository
import com.videoplatform.storage.VideoStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

// SQS entrega al menos una vez: se deduplica con processed_events y las transiciones son condicionales.
@Component
class UploadedEventHandler(
    private val videos: VideoRepository,
    private val events: ProcessedEventRepository,
    private val storage: VideoStorage,
    private val validator: UploadValidator,
    private val mapper: ObjectMapper,
    private val tx: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(body: String) {
        S3EventParser.parse(mapper, body).forEach { process(it) }
    }

    private fun process(event: S3ObjectCreated) {
        if (events.existsByConsumerAndEventId(CONSUMER, event.eventId)) {
            log.info("Evento duplicado ignorado: {}", event.eventId)
            return
        }

        val video = videos.findByS3Key(event.key)
        if (video == null) {
            log.warn("Evento para una key desconocida, se ignora: {}", event.key)
            return
        }
        if (video.status != VideoStatus.PENDING) {
            log.info("El video {} ya está en estado {}, se ignora el evento", video.id, video.status)
            return
        }

        val result = validator.validate(video, storage.head(event.key))

        tx.executeWithoutResult {
            if (events.markProcessed(CONSUMER, event.eventId) == 0) return@executeWithoutResult
            when (result) {
                ValidationResult.Valid -> {
                    videos.markReady(video.id)
                    log.info("Video {} listo", video.id)
                }
                is ValidationResult.Invalid -> {
                    videos.markFailed(video.id, result.reason)
                    log.warn("Video {} rechazado: {}", video.id, result.reason)
                }
            }
        }
    }

    companion object {
        const val CONSUMER = "video-uploaded"
    }
}
