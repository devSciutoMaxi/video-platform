package com.videoplatform.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Video Platform API")
                .version("1.0")
                .description(DESCRIPTION),
        )
        .addTagsItem(Tag().name("Videos").description("Subir, consultar, buscar y reproducir videos"))
        .addTagsItem(Tag().name("Favoritos").description("Videos favoritos del usuario"))

    private companion object {
        const val DESCRIPTION = """
Challenge de system design: una versión simplificada de YouTube (sin transcodificación).

## Cómo probarla (en este orden)

1. **`POST /videos`**: crea el video y devuelve `videoId` + una URL prefirmada de S3 (`upload.url`).
2. **Subir el archivo** con un `PUT` directo a esa URL (no se puede desde Swagger):
   `curl -X PUT '<upload.url>' -H 'Content-Type: video/mp4' --data-binary @archivo.mp4`.
   El archivo debe pesar exactamente `sizeBytes`.
3. **`GET /videos/{id}`** hasta que `status` pase de `PENDING` a `READY` (el consumer valida el archivo).
   Si no coincide tamaño o tipo, queda `FAILED` con el motivo en `failureReason`.
4. **`GET /videos/search?q=`**, **`POST /videos/{id}/views`** y los **favoritos**.

## Convenciones

- **Autenticación simulada:** todos los endpoints llevan el header `X-User-Id` (en producción sería un JWT).
- **Idempotencia:** `POST /videos` exige `Idempotency-Key`. Mismo key + mismo payload = mismo video (`200`);
  mismo key + otro payload = `409`. Favoritos (`PUT`/`DELETE`) son idempotentes por definición.
- **Paginación:** `page` (desde 0) y `size` (máx. 50). El orden es fijo (más reciente primero); `sort` se ignora.
- **Errores:** siempre `{ code, message, details }`. `400` request inválida, `404` no existe o no es visible,
  `409` conflicto de idempotencia, `422` regla de negocio (tipo o tamaño no permitido).
- **Vistas:** `POST /videos/{id}/views` responde `202` y el contador se actualiza unos segundos después (cola SQS).
"""
    }
}
