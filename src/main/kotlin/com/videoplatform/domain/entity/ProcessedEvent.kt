package com.videoplatform.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId::class)
class ProcessedEvent(
    @Id
    @Column(name = "consumer")
    val consumer: String,

    @Id
    @Column(name = "event_id")
    val eventId: String,

    @Column(name = "processed_at", nullable = false)
    val processedAt: Instant,
)
