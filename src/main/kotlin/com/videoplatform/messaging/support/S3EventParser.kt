package com.videoplatform.messaging.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.videoplatform.messaging.event.S3ObjectCreated
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object S3EventParser {

    // Ignora s3:TestEvent y otros eventos; un cuerpo inválido lanza para que termine en la DLQ.
    fun parse(mapper: ObjectMapper, body: String): List<S3ObjectCreated> {
        val root = mapper.readTree(body)
        if (root.path("Event").asText("") == "s3:TestEvent") return emptyList()

        return root.path("Records").mapNotNull { record ->
            if (!record.path("eventName").asText("").startsWith("ObjectCreated")) return@mapNotNull null
            val obj = record.path("s3").path("object")
            val rawKey = obj.path("key").asText("")
            if (rawKey.isBlank()) return@mapNotNull null

            // S3 codifica la key como URL (los espacios llegan como '+').
            val key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8)
            val discriminator = obj.path("sequencer").asText("").ifBlank { obj.path("eTag").asText("") }
            S3ObjectCreated(key = key, eventId = "$key#$discriminator")
        }
    }
}
