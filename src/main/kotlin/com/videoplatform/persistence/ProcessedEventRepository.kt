package com.videoplatform.persistence

import com.videoplatform.domain.entity.ProcessedEvent
import com.videoplatform.domain.entity.ProcessedEventId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface ProcessedEventRepository : JpaRepository<ProcessedEvent, ProcessedEventId> {

    fun existsByConsumerAndEventId(consumer: String, eventId: String): Boolean

    // Devuelve 1 si es la primera vez que se ve el evento y 0 si ya estaba.
    @Transactional
    @Modifying
    @Query(
        value = "INSERT INTO processed_events (consumer, event_id, processed_at) " +
            "VALUES (:consumer, :eventId, now()) ON CONFLICT DO NOTHING",
        nativeQuery = true,
    )
    fun markProcessed(@Param("consumer") consumer: String, @Param("eventId") eventId: String): Int

    @Transactional
    @Modifying
    @Query("DELETE FROM ProcessedEvent e WHERE e.processedAt < :cutoff")
    fun purgeOlderThan(@Param("cutoff") cutoff: Instant): Int
}
