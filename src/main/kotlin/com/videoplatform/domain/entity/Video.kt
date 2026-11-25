package com.videoplatform.domain.entity

import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.domain.model.VideoSummary
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// Inmutable para JPA: estado y vistas cambian solo con UPDATE condicionales del repositorio.
@Entity
@Table(name = "videos")
class Video(
    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "owner_id", nullable = false)
    val ownerId: String,

    @Column(name = "title", nullable = false)
    val title: String,

    @Column(name = "description")
    val description: String?,

    @Column(name = "file_name", nullable = false)
    val fileName: String,

    @Column(name = "content_type", nullable = false)
    val contentType: String,

    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,

    @Column(name = "s3_key", nullable = false)
    val s3Key: String,

    @Column(name = "cdn_url", nullable = false)
    val cdnUrl: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: VideoStatus,

    @Column(name = "failure_reason")
    val failureReason: String?,

    @Column(name = "views", nullable = false, updatable = false)
    val views: Long,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)

fun Video.toSummary() = VideoSummary(
    id = id,
    title = title,
    description = description,
    views = views,
    playbackUrl = cdnUrl,
    createdAt = createdAt,
)
