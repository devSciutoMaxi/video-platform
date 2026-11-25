package com.videoplatform.persistence

import com.videoplatform.domain.entity.IdempotencyKey
import com.videoplatform.domain.entity.IdempotencyKeyId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface IdempotencyRepository : JpaRepository<IdempotencyKey, IdempotencyKeyId> {

    // INSERT plano: un duplicado lanza DataIntegrityViolationException.
    @Transactional
    @Modifying
    @Query(
        value = "INSERT INTO idempotency_keys (user_id, idem_key, request_hash, video_id, created_at) " +
            "VALUES (:userId, :idemKey, :requestHash, :videoId, now())",
        nativeQuery = true,
    )
    fun insert(
        @Param("userId") userId: String,
        @Param("idemKey") idemKey: String,
        @Param("requestHash") requestHash: String,
        @Param("videoId") videoId: UUID,
    ): Int
}
