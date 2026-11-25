package com.videoplatform.storage

import com.videoplatform.config.AppProperties
import com.videoplatform.domain.model.PresignedUpload
import com.videoplatform.domain.model.StoredObject
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Component
class S3VideoStorage(
    private val s3: S3Client,
    private val presigner: S3Presigner,
    private val props: AppProperties,
) : VideoStorage {

    override fun presignUpload(key: String, contentType: String, sizeBytes: Long): PresignedUpload {
        val putRequest = PutObjectRequest.builder()
            .bucket(props.aws.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(sizeBytes) // la firma obliga al cliente a subir exactamente este tamaño
            .build()

        val presigned = presigner.presignPutObject {
            it.signatureDuration(props.presignExpiry).putObjectRequest(putRequest)
        }

        // Headers que el cliente debe enviar tal cual. `host` y `content-length` los fija el cliente HTTP.
        val headers = linkedMapOf<String, String>()
        presigned.signedHeaders().forEach { (name, values) ->
            if (!name.equals("host", ignoreCase = true) && !name.equals("content-length", ignoreCase = true)) {
                headers[name] = values.joinToString(",")
            }
        }
        if (headers.keys.none { it.equals("content-type", ignoreCase = true) }) {
            headers["Content-Type"] = contentType
        }

        return PresignedUpload(
            url = presigned.url().toString(),
            method = "PUT",
            headers = headers,
            expiresAt = presigned.expiration(),
        )
    }

    override fun head(key: String): StoredObject? =
        try {
            val response = s3.headObject { it.bucket(props.aws.bucket).key(key) }
            StoredObject(sizeBytes = response.contentLength(), contentType = response.contentType())
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) null else throw e
        }
}
