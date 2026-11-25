package com.videoplatform.messaging.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.videoplatform.config.AppProperties
import com.videoplatform.config.AwsResources
import com.videoplatform.messaging.event.ViewedEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient

// El mensaje se borra solo si se procesó bien; si falla vuelve tras el visibility timeout y luego va a la DLQ.
@Component
@ConditionalOnProperty(prefix = "app.messaging", name = ["pollers-enabled"], havingValue = "true", matchIfMissing = true)
class QueuePollers(
    private val sqs: SqsClient,
    private val aws: AwsResources,
    private val uploadedHandler: UploadedEventHandler,
    private val viewedHandler: ViewedEventsHandler,
    private val mapper: ObjectMapper,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.messaging.poll-interval-ms}")
    fun pollUploaded() = poll(aws.uploadedQueueUrl) { body -> uploadedHandler.handle(body) }

    @Scheduled(fixedDelayString = "\${app.messaging.poll-interval-ms}")
    fun pollViewed() = poll(aws.viewedQueueUrl) { body ->
        viewedHandler.handle(mapper.readValue(body, ViewedEvent::class.java))
    }

    private fun poll(queueUrl: String, process: (String) -> Unit) {
        val messages = try {
            sqs.receiveMessage {
                it.queueUrl(queueUrl).maxNumberOfMessages(10).waitTimeSeconds(props.messaging.waitTimeSeconds)
            }.messages()
        } catch (e: Exception) {
            log.warn("No se pudo leer de la cola {}", queueUrl, e)
            return
        }

        for (message in messages) {
            try {
                process(message.body())
                sqs.deleteMessage { it.queueUrl(queueUrl).receiptHandle(message.receiptHandle()) }
            } catch (e: Exception) {
                log.warn("Falló el mensaje {}, volverá a entregarse al vencer su visibility timeout", message.messageId(), e)
            }
        }
    }
}
