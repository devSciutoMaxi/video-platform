package com.videoplatform.messaging.publisher

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.videoplatform.config.AwsResources
import com.videoplatform.messaging.event.ViewedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import java.time.Instant
import java.util.UUID
import java.util.function.Consumer

class SqsViewEventPublisherTest {

    private val sqs = mockk<SqsClient>()
    private val aws = mockk<AwsResources>()
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val publisher = SqsViewEventPublisher(sqs, aws, mapper)

    @Test
    fun `envia el evento serializado a la cola de vistas`() {
        val event = ViewedEvent("event-1", UUID.randomUUID(), "user-1", Instant.parse("2026-01-01T00:00:00Z"))
        val customizer = slot<Consumer<SendMessageRequest.Builder>>()
        every { aws.viewedQueueUrl } returns "http://sqs.test/viewed"
        every { sqs.sendMessage(capture(customizer)) } returns SendMessageResponse.builder().build()

        publisher.publish(event)

        val request = SendMessageRequest.builder().also { customizer.captured.accept(it) }.build()
        assertThat(request.queueUrl()).isEqualTo("http://sqs.test/viewed")
        assertThat(mapper.readValue(request.messageBody(), ViewedEvent::class.java)).isEqualTo(event)
    }
}
