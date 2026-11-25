package com.videoplatform.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.videoplatform.config.AppProperties
import com.videoplatform.config.AwsResources
import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.messaging.consumer.UploadedEventHandler
import com.videoplatform.persistence.VideoRepository
import com.videoplatform.service.MaintenanceJobs
import com.videoplatform.testVideo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PostgresContainer : PostgreSQLContainer<PostgresContainer>("postgres:16-alpine")

/**
 * Tests de integración de punta a punta contra Postgres y LocalStack (S3 + SQS) reales.
 * Requieren Docker: `./gradlew integrationTest`.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VideoPlatformIT {

    companion object {
        private val postgres = PostgresContainer()
        private val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.1"))
            .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.SQS)

        init {
            postgres.start()
            localstack.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }

            registry.add("app.aws.endpoint") { localstack.endpoint.toString() }
            registry.add("app.aws.access-key") { localstack.accessKey }
            registry.add("app.aws.secret-key") { localstack.secretKey }
            registry.add("app.aws.region") { localstack.region }
            registry.add("app.aws.provision") { "true" }
            registry.add("app.cdn-base-url") { "${localstack.endpoint}/video-bucket" }

            // Tiempos cortos para que los reintentos y la DLQ se puedan verificar rápido.
            registry.add("app.queues.visibility-timeout-seconds") { "2" }
            registry.add("app.messaging.poll-interval-ms") { "100" }
            registry.add("app.messaging.wait-time-seconds") { "1" }
        }

        private val BYTES = ByteArray(1024) { 'x'.code.toByte() }
    }

    @Autowired lateinit var rest: TestRestTemplate
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var s3: S3Client
    @Autowired lateinit var sqs: SqsClient
    @Autowired lateinit var aws: AwsResources
    @Autowired lateinit var props: AppProperties
    @Autowired lateinit var videos: VideoRepository
    @Autowired lateinit var uploadedHandler: UploadedEventHandler
    @Autowired lateinit var maintenance: MaintenanceJobs

    @Test
    fun `crear un video es idempotente con la misma Idempotency-Key`() {
        val key = UUID.randomUUID().toString()

        val first = createVideo("user-1", key)
        assertThat(first.statusCode.value()).isEqualTo(201)
        assertThat(json(first)["status"].asText()).isEqualTo("PENDING")
        assertThat(json(first)["upload"]["url"].asText()).isNotBlank()

        val second = createVideo("user-1", key)
        assertThat(second.statusCode.value()).isEqualTo(200)
        assertThat(json(second)["videoId"].asText()).isEqualTo(json(first)["videoId"].asText())
        assertThat(json(second)["upload"]["url"].asText()).isNotBlank()

        val rows = jdbc.queryForObject(
            "SELECT count(*) FROM idempotency_keys WHERE user_id = ? AND idem_key = ?",
            Long::class.javaObjectType, "user-1", key,
        )
        assertThat(rows).isEqualTo(1L)
    }

    @Test
    fun `reusar la clave con otro payload es un conflicto y otro usuario tiene su propio espacio de claves`() {
        val key = UUID.randomUUID().toString()
        createVideo("user-1", key)

        val conflict = createVideo("user-1", key, title = "Otro titulo")
        assertThat(conflict.statusCode.value()).isEqualTo(409)

        val otherUser = createVideo("user-2", key, title = "Otro titulo")
        assertThat(otherUser.statusCode.value()).isEqualTo(201)
    }

    @Test
    fun `requests concurrentes con la misma clave crean un solo video`() {
        val key = UUID.randomUUID().toString()
        val results = (1..6).map {
            java.util.concurrent.CompletableFuture.supplyAsync { createVideo("user-1", key) }
        }.map { it.join() }

        assertThat(results.map { it.statusCode.value() }).allMatch { it == 200 || it == 201 }
        assertThat(results.map { json(it)["videoId"].asText() }.toSet()).hasSize(1)
        assertThat(results.count { it.statusCode.value() == 201 }).isEqualTo(1)
    }

    @Test
    fun `faltan headers obligatorios`() {
        val noKey = call(HttpMethod.POST, "/videos", "user-1", videoBody())
        assertThat(noKey.statusCode.value()).isEqualTo(400)

        val noUser = call(HttpMethod.GET, "/videos/${UUID.randomUUID()}", null)
        assertThat(noUser.statusCode.value()).isEqualTo(400)
    }

    @Test
    fun `validaciones de la request`() {
        assertThat(createVideo("user-1", contentType = "application/pdf").statusCode.value()).isEqualTo(422)
        assertThat(createVideo("user-1", size = 5_000_000_000L).statusCode.value()).isEqualTo(422)
        assertThat(createVideo("user-1", title = "").statusCode.value()).isEqualTo(400)
    }

    @Test
    fun `subir el archivo a S3 dispara el consumer y deja el video READY`() {
        val id = createReadyVideo("user-1", title = "Mi video")

        val detail = json(call(HttpMethod.GET, "/videos/$id", "user-2"))
        assertThat(detail["status"].asText()).isEqualTo("READY")
        assertThat(detail["playbackUrl"].asText()).startsWith(props.cdnBaseUrl).contains(id)
        assertThat(detail["views"].asLong()).isEqualTo(0)
        assertThat(detail["favorite"].asBoolean()).isFalse()
    }

    @Test
    fun `un archivo con content-type distinto al declarado queda FAILED`() {
        val created = json(createVideo("user-1"))
        val id = created["videoId"].asText()
        val s3Key = jdbc.queryForObject("SELECT s3_key FROM videos WHERE id = ?", String::class.java, UUID.fromString(id))

        // Se sube directo a S3 con otro content-type, saltándose la URL prefirmada.
        s3.putObject(
            PutObjectRequest.builder().bucket(props.aws.bucket).key(s3Key).contentType("video/webm").build(),
            RequestBody.fromBytes(BYTES),
        )

        val detail = awaitStatus(id, "user-1", "FAILED")
        assertThat(detail["failureReason"].asText()).isEqualTo("CONTENT_TYPE_MISMATCH")
        assertThat(detail["playbackUrl"].isNull).isTrue()
    }

    @Test
    fun `un video que no esta READY solo lo ve su dueno`() {
        val id = json(createVideo("user-1"))["videoId"].asText()

        assertThat(call(HttpMethod.GET, "/videos/$id", "user-2").statusCode.value()).isEqualTo(404)

        val owner = call(HttpMethod.GET, "/videos/$id", "user-1")
        assertThat(owner.statusCode.value()).isEqualTo(200)
        assertThat(json(owner)["status"].asText()).isEqualTo("PENDING")
        assertThat(json(owner)["playbackUrl"].isNull).isTrue()
    }

    @Test
    fun `el handler es idempotente ante un mensaje duplicado`() {
        // La key no empieza con "videos/", así que S3 no dispara una notificación real:
        // el test controla exactamente cuántas veces se entrega el evento.
        val id = UUID.randomUUID()
        val key = "manual/$id/clip.mp4"
        videos.saveAndFlush(
            testVideo(id = id, sizeBytes = BYTES.size.toLong(), s3Key = key, cdnUrl = "${props.cdnBaseUrl}/$key")
        )
        s3.putObject(
            PutObjectRequest.builder().bucket(props.aws.bucket).key(key).contentType("video/mp4").build(),
            RequestBody.fromBytes(BYTES),
        )

        val body = s3Event(key, sequencer = "SEQ-1")
        uploadedHandler.handle(body)
        uploadedHandler.handle(body) // entrega duplicada (at-least-once)

        assertThat(videos.findByIdOrNull(id)!!.status).isEqualTo(VideoStatus.READY)
        val processed = jdbc.queryForObject(
            "SELECT count(*) FROM processed_events WHERE consumer = 'video-uploaded' AND event_id = ?",
            Long::class.javaObjectType, "$key#SEQ-1",
        )
        assertThat(processed).isEqualTo(1L)

        // Un evento tardío (otro sequencer) tampoco hace retroceder el estado.
        uploadedHandler.handle(s3Event(key, sequencer = "SEQ-2"))
        assertThat(videos.findByIdOrNull(id)!!.status).isEqualTo(VideoStatus.READY)
    }

    @Test
    fun `un mensaje que siempre falla se reintenta y termina en la DLQ`() {
        val poison = "not-json-${UUID.randomUUID()}{{{"
        sqs.sendMessage { it.queueUrl(aws.uploadedQueueUrl).messageBody(poison) }

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500)).untilAsserted {
            val messages = sqs.receiveMessage {
                it.queueUrl(aws.uploadedDlqUrl).maxNumberOfMessages(10).waitTimeSeconds(1)
            }.messages()
            assertThat(messages.map { it.body() }).contains(poison)
        }
    }

    @Test
    fun `los videos PENDING sin archivo expiran`() {
        val id = json(createVideo("user-1"))["videoId"].asText()

        val expired = maintenance.expirePending(Instant.now().plus(Duration.ofDays(2)))
        assertThat(expired).isGreaterThanOrEqualTo(1)

        val detail = json(call(HttpMethod.GET, "/videos/$id", "user-1"))
        assertThat(detail["status"].asText()).isEqualTo("FAILED")
        assertThat(detail["failureReason"].asText()).isEqualTo("UPLOAD_EXPIRED")
    }

    @Test
    fun `favoritos agregar y quitar son idempotentes`() {
        val id = createReadyVideo("user-1")
        val path = "/users/me/favorites/$id"

        assertThat(call(HttpMethod.PUT, path, "fan").statusCode.value()).isEqualTo(204)
        assertThat(call(HttpMethod.PUT, path, "fan").statusCode.value()).isEqualTo(204)

        val list = json(call(HttpMethod.GET, "/users/me/favorites", "fan"))
        assertThat(list["content"]).hasSize(1)
        assertThat(list["content"][0]["video"]["id"].asText()).isEqualTo(id)
        assertThat(json(call(HttpMethod.GET, "/videos/$id", "fan"))["favorite"].asBoolean()).isTrue()
        // Los favoritos son por usuario.
        assertThat(json(call(HttpMethod.GET, "/users/me/favorites", "otro"))["content"]).isEmpty()

        assertThat(call(HttpMethod.DELETE, path, "fan").statusCode.value()).isEqualTo(204)
        assertThat(call(HttpMethod.DELETE, path, "fan").statusCode.value()).isEqualTo(204)
        assertThat(json(call(HttpMethod.GET, "/users/me/favorites", "fan"))["content"]).isEmpty()
    }

    @Test
    fun `no se puede marcar como favorito un video que no esta READY`() {
        val pending = json(createVideo("user-1"))["videoId"].asText()
        assertThat(call(HttpMethod.PUT, "/users/me/favorites/$pending", "fan").statusCode.value()).isEqualTo(404)
        assertThat(call(HttpMethod.PUT, "/users/me/favorites/${UUID.randomUUID()}", "fan").statusCode.value())
            .isEqualTo(404)
    }

    @Test
    fun `la lista de favoritos se pagina con page y size`() {
        val user = "fan-${UUID.randomUUID()}"
        val ids = (1..3).map { createReadyVideo("owner", title = "Favorito $it") }
        ids.forEach { assertThat(call(HttpMethod.PUT, "/users/me/favorites/$it", user).statusCode.value()).isEqualTo(204) }

        val page0 = json(call(HttpMethod.GET, "/users/me/favorites?page=0&size=2", user))
        val page1 = json(call(HttpMethod.GET, "/users/me/favorites?page=1&size=2", user))

        assertThat(page0["content"]).hasSize(2)
        assertThat(page1["content"]).hasSize(1)
        assertThat(page0["page"]["totalElements"].asLong()).isEqualTo(3L)
        assertThat(page0["page"]["totalPages"].asInt()).isEqualTo(2)

        // Sin repetidos ni faltantes entre páginas, del más reciente al más antiguo.
        val seen = listOf(page0, page1).flatMap { page -> page["content"].map { it["video"]["id"].asText() } }
        assertThat(seen).containsExactlyElementsOf(ids.reversed())
    }

    @Test
    fun `el buscador encuentra solo videos READY y pagina con page y size`() {
        val token = "tk" + UUID.randomUUID().toString().replace("-", "")
        val first = createReadyVideo("user-1", title = "$token alpha")
        val second = createReadyVideo("user-1", title = "beta $token")
        createVideo("user-1", title = "$token pendiente") // PENDING: no debe aparecer

        val page0 = json(call(HttpMethod.GET, "/videos/search?q=$token&page=0&size=1", "user-2"))
        assertThat(page0["content"]).hasSize(1)
        // Solo los 2 videos READY cuentan: el PENDING no aparece.
        assertThat(page0["page"]["totalElements"].asLong()).isEqualTo(2L)
        assertThat(page0["page"]["totalPages"].asInt()).isEqualTo(2)

        val page1 = json(call(HttpMethod.GET, "/videos/search?q=$token&page=1&size=1", "user-2"))
        assertThat(page1["content"]).hasSize(1)

        // Más recientes primero, y sin repetidos entre páginas.
        val found = listOf(page0, page1).map { it["content"][0]["id"].asText() }
        assertThat(found).containsExactly(second, first)
        assertThat(page0["content"][0]["playbackUrl"].asText()).isNotBlank()

        // La búsqueda no distingue mayúsculas.
        val upper = json(call(HttpMethod.GET, "/videos/search?q=${token.uppercase()}", "user-2"))
        assertThat(upper["page"]["totalElements"].asLong()).isEqualTo(2L)

        val none = json(call(HttpMethod.GET, "/videos/search?q=zz${UUID.randomUUID().toString().replace("-", "")}", "user-2"))
        assertThat(none["content"]).isEmpty()
        assertThat(none["page"]["totalElements"].asLong()).isEqualTo(0L)
    }

    @Test
    fun `el buscador rechaza consultas vacias y trata los comodines como texto`() {
        assertThat(searchRaw(" ", "user-1").statusCode.value()).isEqualTo(400)

        // '%' se busca literalmente, no como comodín de LIKE (ningún video de prueba lo contiene).
        val wildcard = json(searchRaw("%", "user-1"))
        assertThat(wildcard["page"]["totalElements"].asLong()).isEqualTo(0L)
    }

    /** Pasa `q` como variable de plantilla: así RestTemplate lo codifica una sola vez (no se doble-codifica). */
    private fun searchRaw(q: String, user: String): ResponseEntity<String> =
        rest.exchange(
            "/videos/search?q={q}", HttpMethod.GET, HttpEntity<Any>(null, headers(user, emptyMap())),
            String::class.java, q,
        )

    @Test
    fun `las vistas se cuentan de forma asincrona y los duplicados no suman`() {
        val id = createReadyVideo("user-1")

        repeat(3) {
            assertThat(call(HttpMethod.POST, "/videos/$id/views", "viewer").statusCode.value()).isEqualTo(202)
        }
        awaitViews(id, 3)

        // El mismo evento entregado dos veces por la cola se cuenta una sola vez.
        val duplicated = """{"eventId":"dup-${UUID.randomUUID()}","videoId":"$id","userId":"viewer","occurredAt":"2026-01-01T00:00:00Z"}"""
        repeat(2) { sqs.sendMessage { it.queueUrl(aws.viewedQueueUrl).messageBody(duplicated) } }
        awaitViews(id, 4)

        Thread.sleep(3_000)
        assertThat(views(id)).isEqualTo(4L)
    }

    @Test
    fun `no se pueden registrar vistas de un video inexistente o no READY`() {
        val pending = json(createVideo("user-1"))["videoId"].asText()
        assertThat(call(HttpMethod.POST, "/videos/$pending/views", "viewer").statusCode.value()).isEqualTo(404)
        assertThat(call(HttpMethod.POST, "/videos/${UUID.randomUUID()}/views", "viewer").statusCode.value())
            .isEqualTo(404)
    }

    private fun headers(user: String?, extra: Map<String, String>): HttpHeaders {
        val headers = HttpHeaders()
        user?.let { headers.set("X-User-Id", it) }
        extra.forEach { (name, value) -> headers.set(name, value) }
        headers.contentType = MediaType.APPLICATION_JSON
        return headers
    }

    private fun call(
        method: HttpMethod,
        path: String,
        user: String?,
        body: Any? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ResponseEntity<String> =
        rest.exchange(path, method, HttpEntity(body, headers(user, extraHeaders)), String::class.java)

    private fun json(response: ResponseEntity<String>): JsonNode = mapper.readTree(response.body)

    private fun videoBody(
        title: String = "Video",
        description: String? = "descripcion",
        contentType: String = "video/mp4",
        size: Long = BYTES.size.toLong(),
    ): Map<String, Any?> = mapOf(
        "title" to title,
        "description" to description,
        "fileName" to "clip.mp4",
        "contentType" to contentType,
        "sizeBytes" to size,
    )

    private fun createVideo(
        user: String,
        idempotencyKey: String = UUID.randomUUID().toString(),
        title: String = "Video",
        description: String? = "descripcion",
        contentType: String = "video/mp4",
        size: Long = BYTES.size.toLong(),
    ): ResponseEntity<String> = call(
        HttpMethod.POST, "/videos", user,
        videoBody(title, description, contentType, size),
        mapOf("Idempotency-Key" to idempotencyKey),
    )

    /** Sube el archivo con la URL prefirmada, tal como lo haría un cliente real. */
    private fun uploadTo(upload: JsonNode, bytes: ByteArray) {
        val builder = HttpRequest.newBuilder(URI.create(upload["url"].asText()))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
        upload["headers"].fields().forEach { (name, value) -> builder.header(name, value.asText()) }
        val response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.discarding())
        assertThat(response.statusCode()).isEqualTo(200)
    }

    private fun createReadyVideo(user: String, title: String = "Video", description: String? = "descripcion"): String {
        val created = createVideo(user, title = title, description = description)
        assertThat(created.statusCode.value()).isEqualTo(201)
        val body = json(created)
        val id = body["videoId"].asText()
        uploadTo(body["upload"], BYTES)
        awaitStatus(id, user, "READY")
        return id
    }

    private fun awaitStatus(id: String, user: String, expected: String): JsonNode {
        var last: JsonNode? = null
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted {
            val response = call(HttpMethod.GET, "/videos/$id", user)
            assertThat(response.statusCode.value()).isEqualTo(200)
            last = json(response)
            assertThat(last!!["status"].asText()).isEqualTo(expected)
        }
        return last!!
    }

    private fun views(id: String): Long =
        json(call(HttpMethod.GET, "/videos/$id", "viewer"))["views"].asLong()

    private fun awaitViews(id: String, expected: Long) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted {
            assertThat(views(id)).isEqualTo(expected)
        }
    }

    private fun s3Event(key: String, sequencer: String): String =
        """{"Records":[{"eventName":"ObjectCreated:Put","s3":{"bucket":{"name":"${props.aws.bucket}"},""" +
            """"object":{"key":"$key","size":${BYTES.size},"sequencer":"$sequencer"}}}]}"""
}
