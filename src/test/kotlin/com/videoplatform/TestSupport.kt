package com.videoplatform

import com.videoplatform.config.AppProperties
import com.videoplatform.domain.entity.Video
import com.videoplatform.domain.model.VideoStatus
import io.mockk.mockk
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

fun testProps(maxVideoSizeBytes: Long = 1_000_000) = AppProperties(
    cdnBaseUrl = "http://cdn.test/bucket",
    maxVideoSizeBytes = maxVideoSizeBytes,
    allowedContentTypes = setOf("video/mp4", "video/webm"),
    presignExpiry = Duration.ofMinutes(5),
    pendingTtl = Duration.ofHours(24),
    aws = AppProperties.Aws(
        region = "us-east-1", endpoint = null, accessKey = null, secretKey = null,
        bucket = "bucket", provision = false,
    ),
    queues = AppProperties.Queues(
        uploaded = "up", uploadedDlq = "up-dlq", viewed = "vw", viewedDlq = "vw-dlq",
        maxReceiveCount = 3, visibilityTimeoutSeconds = 30,
    ),
    messaging = AppProperties.Messaging(
        pollersEnabled = false, pollIntervalMs = 500, waitTimeSeconds = 1,
    ),
)

fun testVideo(
    id: UUID = UUID.randomUUID(),
    sizeBytes: Long = 1024,
    contentType: String = "video/mp4",
    status: VideoStatus = VideoStatus.PENDING,
    failureReason: String? = null,
    s3Key: String = "videos/test/clip.mp4",
    cdnUrl: String = "http://cdn.test/bucket/$s3Key",
) = Video(
    id = id,
    ownerId = "user-1",
    title = "Title",
    description = null,
    fileName = "clip.mp4",
    contentType = contentType,
    sizeBytes = sizeBytes,
    s3Key = s3Key,
    cdnUrl = cdnUrl,
    status = status,
    failureReason = failureReason,
    views = 0,
    createdAt = Instant.now(),
    updatedAt = Instant.now(),
)

fun noOpTransactions() = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true))
