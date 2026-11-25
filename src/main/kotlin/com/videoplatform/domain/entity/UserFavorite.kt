package com.videoplatform.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_favorites")
@IdClass(UserFavoriteId::class)
class UserFavorite(
    @Id
    @Column(name = "user_id")
    val userId: String,

    @Id
    @Column(name = "video_id")
    val videoId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    // La misma columna video_id ya está mapeada arriba: acá solo se usa para traer el video (solo lectura).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", insertable = false, updatable = false)
    lateinit var video: Video
}
