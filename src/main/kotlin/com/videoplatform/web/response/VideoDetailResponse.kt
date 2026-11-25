package com.videoplatform.web.response

import com.videoplatform.domain.entity.Video
import com.videoplatform.domain.model.VideoStatus
import java.time.Instant
import java.util.UUID

data class VideoDetailResponse(
    val id: UUID,
    val title: String,
    val description: String?,
    val status: VideoStatus,
    val playbackUrl: String?,
    val views: Long,
    val sizeBytes: Long,
    val contentType: String,
    val ownerId: String,
    val createdAt: Instant,
    val favorite: Boolean,
    val failureReason: String?,
) {
    companion object {
        fun from(video: Video, favorite: Boolean, isOwner: Boolean) = VideoDetailResponse(
            id = video.id,
            title = video.title,
            description = video.description,
            status = video.status,
            playbackUrl = if (video.status == VideoStatus.READY) video.cdnUrl else null,
            views = video.views,
            sizeBytes = video.sizeBytes,
            contentType = video.contentType,
            ownerId = video.ownerId,
            createdAt = video.createdAt,
            favorite = favorite,
            failureReason = if (isOwner) video.failureReason else null,
        )
    }
}
