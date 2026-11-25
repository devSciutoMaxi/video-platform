package com.videoplatform.service

import com.videoplatform.persistence.ProcessedEventRepository
import com.videoplatform.persistence.VideoRepository
import com.videoplatform.testProps
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class MaintenanceJobsTest {

    private val videos = mockk<VideoRepository>()
    private val processedEvents = mockk<ProcessedEventRepository>()
    private val jobs = MaintenanceJobs(videos, processedEvents, testProps())

    @Test
    fun `expira los PENDING mas viejos que el ttl configurado`() {
        val now = Instant.parse("2026-03-10T12:00:00Z")
        every { videos.expirePending(any()) } returns 2

        val expired = jobs.expirePending(now)

        assertThat(expired).isEqualTo(2)
        verify { videos.expirePending(now.minus(Duration.ofHours(24))) }
    }

    @Test
    fun `la purga de eventos procesados conserva mas que la retencion de SQS`() {
        assertThat(MaintenanceJobs.PROCESSED_EVENTS_RETENTION).isGreaterThan(Duration.ofDays(4))
    }
}
