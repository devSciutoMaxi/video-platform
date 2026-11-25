package com.videoplatform.messaging.publisher

import com.videoplatform.messaging.event.ViewedEvent

interface ViewEventPublisher {
    fun publish(event: ViewedEvent)
}
