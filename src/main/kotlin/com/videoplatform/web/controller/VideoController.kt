package com.videoplatform.web.controller

import com.videoplatform.domain.model.VideoSummary
import com.videoplatform.service.SearchService
import com.videoplatform.service.VideoService
import com.videoplatform.service.ViewService
import com.videoplatform.service.command.CreateVideoCommand
import com.videoplatform.web.request.CreateVideoRequest
import com.videoplatform.web.response.CreateVideoResponse
import com.videoplatform.web.response.UploadInstructions
import com.videoplatform.web.response.VideoDetailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Videos")
@RestController
@RequestMapping("/videos")
class VideoController(
    private val videos: VideoService,
    private val searchService: SearchService,
    private val views: ViewService,
) {

    @Operation(
        summary = "1. Crear video y obtener la URL de subida",
        description = """Crea el registro del video en estado `PENDING` y devuelve una **URL prefirmada de S3**.

**Después de este paso hay que subir el archivo con un `PUT` directo a `upload.url`** (Swagger no puede hacerlo):
`curl -X PUT '<upload.url>' -H 'Content-Type: video/mp4' --data-binary @archivo.mp4`.
El archivo debe pesar exactamente `sizeBytes` y tener el `contentType` declarado.

**Idempotencia:** repetir la request con el mismo `Idempotency-Key` y el mismo payload devuelve el mismo video
(`200`). Reusar la clave con otro payload es un `409`. La clave es por usuario.""",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Video creado. Hay que subir el archivo a upload.url", content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "Video creado", value = ApiExamples.CREATE_201)])]),
        ApiResponse(responseCode = "200", description = "Reintento con la misma Idempotency-Key: devuelve el mismo video", content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "Mismo video", value = ApiExamples.CREATE_201)])]),
        ApiResponse(responseCode = "400", description = "Request inválida o falta un header", content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "Falta el header", value = ApiExamples.ERR_MISSING_HEADER), ExampleObject(name = "Campo inválido", value = ApiExamples.ERR_VALIDATION)])]),
        ApiResponse(responseCode = "409", description = "La Idempotency-Key ya se usó con otro payload", content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "Conflicto", value = ApiExamples.ERR_CONFLICT)])]),
        ApiResponse(responseCode = "422", description = "Content-type no permitido o tamaño fuera de rango", content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "Content-type no permitido", value = ApiExamples.ERR_UNPROCESSABLE)])])
    )
    @PostMapping
    fun create(
        @Parameter(description = "Usuario que hace la request (simula la autenticación).", example = "user-1")
        @RequestHeader("X-User-Id") userId: String,
        @Parameter(
            description = "Clave de idempotencia elegida por el cliente (un UUID por intento de crear). " +
                "Reusarla permite reintentar sin duplicar el video.",
            example = "6f1c0a52-3c1e-4f5e-9a55-0c2f0f6f9a10",
        )
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreateVideoRequest,
    ): ResponseEntity<CreateVideoResponse> {
        val result = videos.createUpload(
            userId = requireUserId(userId),
            idempotencyKey = idempotencyKey.trim(),
            command = CreateVideoCommand(
                title = request.title!!,
                description = request.description,
                fileName = request.fileName!!,
                contentType = request.contentType!!,
                sizeBytes = request.sizeBytes!!,
            ),
        )
        val body = CreateVideoResponse(
            videoId = result.video.id,
            status = result.video.status,
            upload = result.upload?.let { UploadInstructions.from(it) },
        )
        val status = if (result.replayed) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(body)
    }

    @Operation(
        summary = "3. Buscar videos por título",
        description = "El título contiene el texto, sin distinguir mayúsculas. Solo videos `READY`, del más " +
            "reciente al más antiguo. Paginación estándar de Spring: `page` (desde 0) y `size` (máx. 50). " +
            "El parámetro `sort` se ignora.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Página de resultados (puede estar vacía)", content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "Resultados", value = ApiExamples.SEARCH_200)])]),
        ApiResponse(responseCode = "400", description = "Consulta vacía o falta un parámetro", content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "Consulta vacía", value = ApiExamples.ERR_BLANK_QUERY)])])
    )
    @GetMapping("/search")
    fun search(
        @Parameter(description = "Usuario que hace la request (simula la autenticación).", example = "user-1")
        @RequestHeader("X-User-Id") userId: String,
        @Parameter(description = "Texto a buscar en el título.", example = "primer")
        @RequestParam("q") query: String,
        @ParameterObject pageable: Pageable,
    ): PagedModel<VideoSummary> {
        requireUserId(userId)
        return PagedModel(searchService.search(query, pageable))
    }

    @Operation(
        summary = "2. Detalle del video",
        description = "Sirve para ver el estado de la subida: `PENDING` (esperando el archivo o la validación), " +
            "`READY` (con `playbackUrl`) o `FAILED` (con `failureReason`). Un video que no está `READY` solo lo ve " +
            "su dueño; para cualquier otro usuario es `404`.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Detalle del video", content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "READY", value = ApiExamples.DETAIL_READY), ExampleObject(name = "PENDING", value = ApiExamples.DETAIL_PENDING), ExampleObject(name = "FAILED (solo el dueño)", value = ApiExamples.DETAIL_FAILED)])]),
        ApiResponse(responseCode = "404", description = "No existe, o no está READY y quien consulta no es el dueño", content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "No encontrado", value = ApiExamples.ERR_NOT_FOUND)])])
    )
    @GetMapping("/{id}")
    fun get(
        @Parameter(description = "Usuario que hace la request (simula la autenticación).", example = "user-1")
        @RequestHeader("X-User-Id") userId: String,
        @Parameter(description = "Id del video (el videoId devuelto al crearlo).", example = "3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11")
        @PathVariable id: UUID,
    ): VideoDetailResponse {
        val user = requireUserId(userId)
        val detail = videos.getDetail(id, user)
        return VideoDetailResponse.from(detail.video, detail.favorite, detail.isOwner)
    }

    @Operation(
        summary = "4. Registrar una reproducción",
        description = "Publica un evento en SQS y responde enseguida (`202`). El contador `views` se actualiza " +
            "unos segundos después, cuando lo procesa el consumer (write-behind): es eventualmente consistente. " +
            "Solo videos `READY`.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "Evento publicado; el contador se actualiza async"),
        ApiResponse(responseCode = "404", description = "El video no existe o no está READY", content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "No encontrado", value = ApiExamples.ERR_NOT_FOUND)])])
    )
    @PostMapping("/{id}/views")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun recordView(
        @Parameter(description = "Usuario que hace la request (simula la autenticación).", example = "user-1")
        @RequestHeader("X-User-Id") userId: String,
        @Parameter(description = "Id del video.", example = "3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11")
        @PathVariable id: UUID,
    ) {
        views.recordView(id, requireUserId(userId))
    }
}
