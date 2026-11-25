package com.videoplatform.persistence

import com.videoplatform.domain.entity.Video
import com.videoplatform.domain.model.VideoStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface VideoRepository : JpaRepository<Video, UUID> {

    fun findByS3Key(s3Key: String): Video?

    fun existsByIdAndStatus(id: UUID, status: VideoStatus): Boolean

    fun findByStatusAndTitleContainingIgnoreCase(status: VideoStatus, title: String, pageable: Pageable): Page<Video>

    // Condicional: reintentos y eventos tardíos no cambian nada.
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "UPDATE videos SET status = 'READY', failure_reason = NULL, updated_at = now() " +
            "WHERE id = :id AND status = 'PENDING'",
        nativeQuery = true,
    )
    fun markReady(@Param("id") id: UUID): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "UPDATE videos SET status = 'FAILED', failure_reason = :reason, updated_at = now() " +
            "WHERE id = :id AND status = 'PENDING'",
        nativeQuery = true,
    )
    fun markFailed(@Param("id") id: UUID, @Param("reason") reason: String): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "UPDATE videos SET status = 'FAILED', failure_reason = 'UPLOAD_EXPIRED', updated_at = now() " +
            "WHERE status = 'PENDING' AND created_at < :cutoff",
        nativeQuery = true,
    )
    fun expirePending(@Param("cutoff") cutoff: Instant): Int

    // Incremento atómico en la base.
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE videos SET views = views + 1 WHERE id = :id", nativeQuery = true)
    fun incrementViews(@Param("id") id: UUID): Int
}
