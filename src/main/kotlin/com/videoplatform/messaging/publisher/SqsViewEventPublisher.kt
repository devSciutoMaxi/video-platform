package com.videoplatform.messaging.publisher

import com.fasterxml.jackson.databind.ObjectMapper
import com.videoplatform.config.AwsResources
import com.videoplatform.messaging.event.ViewedEvent
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient

@Component
class SqsViewEventPublisher(
    private val sqs: SqsClient,
    private val aws: AwsResources,
    private val mapper: ObjectMapper,
) : ViewEventPublisher {

    override fun publish(event: ViewedEvent) {
        sqs.sendMessage {
            it.queueUrl(aws.viewedQueueUrl).messageBody(mapper.writeValueAsString(event))
        }
    }
}
