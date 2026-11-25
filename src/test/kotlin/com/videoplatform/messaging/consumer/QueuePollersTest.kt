package com.videoplatform.messaging.consumer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.videoplatform.config.AwsResources
import com.videoplatform.messaging.event.ViewedEvent
import com.videoplatform.testProps
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.time.Instant
import java.util.UUID
import java.util.function.Consumer

class QueuePollersTest {

    private val sqs = mockk<SqsClient>()
    private val aws = mockk<AwsResources>()
    private val uploadedHandler = mockk<UploadedEventHandler>()
    private val viewedHandler = mockk<ViewedEventsHandler>()
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val pollers = QueuePollers(sqs, aws, uploadedHandler, viewedHandler, mapper, testProps())

    private val deleted = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        every { aws.uploadedQueueUrl } returns "http://sqs.test/uploaded"
        every { aws.viewedQueueUrl } returns "http://sqs.test/viewed"
        every { sqs.deleteMessage(any<Consumer<DeleteMessageRequest.Builder>>()) } answers {
            val builder = DeleteMessageRequest.builder()
            firstArg<Consumer<DeleteMessageRequest.Builder>>().accept(builder)
            deleted += builder.build().receiptHandle()
            DeleteMessageResponse.builder().build()
        }
    }

    private fun message(id: String, body: String) =
        Message.builder().messageId(id).receiptHandle("receipt-$id").body(body).build()

    private fun receives(vararg messages: Message) {
        every { sqs.receiveMessage(any<Consumer<ReceiveMessageRequest.Builder>>()) } returns
            ReceiveMessageResponse.builder().messages(*messages).build()
    }

    @Test
    fun `un mensaje procesado con exito se borra de la cola`() {
        receives(message("1", "body-1"))
        every { uploadedHandler.handle("body-1") } just runs

        pollers.pollUploaded()

        verify { uploadedHandler.handle("body-1") }
        assertThat(deleted).containsExactly("receipt-1")
    }

    @Test
    fun `un mensaje que falla no se borra para que se reintente`() {
        receives(message("1", "body-1"))
        every { uploadedHandler.handle(any()) } throws IllegalStateException("BD caida")

        pollers.pollUploaded()

        assertThat(deleted).isEmpty()
    }

    @Test
    fun `un mensaje que falla no impide procesar el siguiente`() {
        receives(message("1", "malo"), message("2", "bueno"))
        every { uploadedHandler.handle("malo") } throws IllegalStateException("fallo")
        every { uploadedHandler.handle("bueno") } just runs

        pollers.pollUploaded()

        assertThat(deleted).containsExactly("receipt-2")
    }

    @Test
    fun `si no se puede leer la cola no falla ni borra nada`() {
        every { sqs.receiveMessage(any<Consumer<ReceiveMessageRequest.Builder>>()) } throws
            IllegalStateException("SQS caido")

        pollers.pollUploaded()

        assertThat(deleted).isEmpty()
        verify(exactly = 0) { uploadedHandler.handle(any()) }
    }

    @Test
    fun `lee los eventos de vistas, los convierte y los borra al procesarlos`() {
        val event = ViewedEvent("event-1", UUID.randomUUID(), "user-1", Instant.parse("2026-01-01T00:00:00Z"))
        val received = slot<ViewedEvent>()
        receives(message("1", mapper.writeValueAsString(event)))
        every { viewedHandler.handle(capture(received)) } just runs

        pollers.pollViewed()

        assertThat(received.captured).isEqualTo(event)
        assertThat(deleted).containsExactly("receipt-1")
    }

    @Test
    fun `un evento de vista ilegible no se borra`() {
        receives(message("1", "esto-no-es-json"))

        pollers.pollViewed()

        verify(exactly = 0) { viewedHandler.handle(any()) }
        assertThat(deleted).isEmpty()
    }
}
