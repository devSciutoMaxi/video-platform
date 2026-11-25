package com.videoplatform.persistence

import com.videoplatform.domain.entity.UserFavorite
import com.videoplatform.domain.entity.UserFavoriteId
import com.videoplatform.domain.model.VideoStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface FavoriteRepository : JpaRepository<UserFavorite, UserFavoriteId> {

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "INSERT INTO user_favorites (user_id, video_id, created_at) " +
            "VALUES (:userId, :videoId, now()) ON CONFLICT DO NOTHING",
        nativeQuery = true,
    )
    fun addIfAbsent(@Param("userId") userId: String, @Param("videoId") videoId: UUID): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "DELETE FROM user_favorites WHERE user_id = :userId AND video_id = :videoId",
        nativeQuery = true,
    )
    fun removeIfPresent(@Param("userId") userId: String, @Param("videoId") videoId: UUID): Int

    fun existsByUserIdAndVideoId(userId: String, videoId: UUID): Boolean

    @EntityGraph(attributePaths = ["video"])
    fun findByUserIdAndVideoStatus(userId: String, status: VideoStatus, pageable: Pageable): Page<UserFavorite>
}
