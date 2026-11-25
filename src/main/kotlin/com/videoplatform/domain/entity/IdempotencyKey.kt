package com.videoplatform.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeyId::class)
class IdempotencyKey(
    @Id
    @Column(name = "user_id")
    val userId: String,

    @Id
    @Column(name = "idem_key")
    val idemKey: String,

    @Column(name = "request_hash", nullable = false)
    val requestHash: String,

    @Column(name = "video_id", nullable = false)
    val videoId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
