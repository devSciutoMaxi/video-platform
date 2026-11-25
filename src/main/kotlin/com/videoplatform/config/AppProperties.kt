package com.videoplatform.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val cdnBaseUrl: String,
    val maxVideoSizeBytes: Long,
    val allowedContentTypes: Set<String>,
    val presignExpiry: Duration,
    val pendingTtl: Duration,
    val aws: Aws,
    val queues: Queues,
    val messaging: Messaging,
) {
    data class Aws(
        val region: String,
        val endpoint: String?,
        val accessKey: String?,
        val secretKey: String?,
        val bucket: String,
        val provision: Boolean,
    )

    data class Queues(
        val uploaded: String,
        val uploadedDlq: String,
        val viewed: String,
        val viewedDlq: String,
        val maxReceiveCount: Int,
        val visibilityTimeoutSeconds: Int,
    )

    data class Messaging(
        val pollersEnabled: Boolean,
        val pollIntervalMs: Long,
        val waitTimeSeconds: Int,
    )
}
