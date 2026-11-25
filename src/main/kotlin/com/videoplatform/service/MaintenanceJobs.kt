package com.videoplatform.service

import com.videoplatform.config.AppProperties
import com.videoplatform.persistence.ProcessedEventRepository
import com.videoplatform.persistence.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class MaintenanceJobs(
    private val videos: VideoRepository,
    private val processedEvents: ProcessedEventRepository,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    fun expirePending() {
        expirePending(Instant.now())
    }

    fun expirePending(now: Instant): Int {
        val expired = videos.expirePending(now.minus(props.pendingTtl))
        if (expired > 0) log.info("{} videos PENDING expirados", expired)
        return expired
    }

    // La retención debe superar la de mensajes de SQS (4 días) para no recontar duplicados tardíos.
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 600_000)
    fun purgeProcessedEvents() {
        val purged = processedEvents.purgeOlderThan(Instant.now().minus(PROCESSED_EVENTS_RETENTION))
        if (purged > 0) log.info("{} eventos procesados purgados", purged)
    }

    companion object {
        val PROCESSED_EVENTS_RETENTION: Duration = Duration.ofDays(7)
    }
}
