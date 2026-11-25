package com.videoplatform.web.controller

import com.videoplatform.domain.model.FavoriteItem
import com.videoplatform.service.FavoriteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Favoritos")
@RestController
@RequestMapping("/users/me/favorites")
class FavoriteController(private val favorites: FavoriteService) {

    @Operation(
        summary = "Agregar a favoritos",
        description = "Idempotente: repetirlo deja el mismo estado y responde igual. Solo videos `READY`.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "El video quedó en favoritos (también si ya lo estaba)"),
        ApiResponse(responseCode = "404", description = "El video no existe o no está READY", content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "No encontrado", value = ApiExamples.ERR_NOT_FOUND)])]),
    )
    @PutMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun add(
        @Parameter(description = "Usuario que hace la request (simula la autenticación).", example = "user-1")
        @RequestHeader("X-User-Id") userId: String,
        @Parameter(description = "Id del video.", example = "3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11")
        @PathVariable videoId: UUID,
    ) {
        favorites.add(requireUserId(userId), videoId)
    }

    @Operation(
        summary = "Quitar de favoritos",
        description = "Idempotente: quitar algo que no es favorito no es un error.",
    )
    @ApiResponses(ApiResponse(responseCode = "204", description = "El video ya no está en favoritos"))
    @DeleteMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @Parameter(description = "Usuario que hace la request (simula la autenticación).", example = "user-1")
        @RequestHeader("X-User-Id") userId: String,
        @Parameter(description = "Id del video.", example = "3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11")
        @PathVariable videoId: UUID,
    ) {
        favorites.remove(requireUserId(userId), videoId)
    }

    @Operation(
        summary = "Listar mis favoritos",
        description = "Del favorito más reciente al más antiguo. Solo incluye videos `READY`. Paginación estándar: " +
            "`page` (desde 0) y `size` (máx. 50).",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Página de favoritos (puede estar vacía)", content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "Favoritos", value = ApiExamples.FAVORITES_200)])]),
    )
    @GetMapping
    fun list(
        @Parameter(description = "Usuario que hace la request (simula la autenticación).", example = "user-1")
        @RequestHeader("X-User-Id") userId: String,
        @ParameterObject pageable: Pageable,
    ): PagedModel<FavoriteItem> = PagedModel(favorites.list(requireUserId(userId), pageable))
}
