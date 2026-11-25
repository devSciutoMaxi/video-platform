package com.videoplatform.service

import com.videoplatform.config.AppProperties
import com.videoplatform.domain.entity.IdempotencyKey
import com.videoplatform.domain.entity.IdempotencyKeyId
import com.videoplatform.domain.entity.Video
import com.videoplatform.domain.exception.ConflictException
import com.videoplatform.domain.exception.InvalidRequestException
import com.videoplatform.domain.exception.NotFoundException
import com.videoplatform.domain.exception.UnprocessableException
import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.domain.util.RequestHasher
import com.videoplatform.persistence.FavoriteRepository
import com.videoplatform.persistence.IdempotencyRepository
import com.videoplatform.persistence.VideoRepository
import com.videoplatform.service.command.CreateVideoCommand
import com.videoplatform.service.result.CreateVideoResult
import com.videoplatform.service.result.VideoDetail
import com.videoplatform.storage.VideoStorage
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@Service
class VideoService(
    private val videos: VideoRepository,
    private val idempotency: IdempotencyRepository,
    private val favorites: FavoriteRepository,
    private val storage: VideoStorage,
    private val props: AppProperties,
    private val tx: TransactionTemplate,
) {

    fun createUpload(userId: String, idempotencyKey: String, command: CreateVideoCommand): CreateVideoResult {
        if (idempotencyKey.isBlank() || idempotencyKey.length > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw InvalidRequestException("Idempotency-Key must have between 1 and $MAX_IDEMPOTENCY_KEY_LENGTH characters")
        }
        val normalized = validate(command)
        val hash = RequestHasher.hash(
            normalized.title, normalized.description, normalized.fileName,
            normalized.contentType, normalized.sizeBytes.toString(),
        )

        findIdempotency(userId, idempotencyKey)?.let { return replay(it, hash) }

        return try {
            val video = tx.execute { insertNew(userId, idempotencyKey, hash, normalized) }!!
            CreateVideoResult(
                video = video,
                upload = storage.presignUpload(video.s3Key, video.contentType, video.sizeBytes),
                replayed = false,
            )
        } catch (e: DataIntegrityViolationException) {
            // Carrera: otra request con la misma clave ganó entre el find y el insert. Se devuelve su resultado.
            val existing = findIdempotency(userId, idempotencyKey) ?: throw e
            replay(existing, hash)
        }
    }

    fun getDetail(videoId: UUID, userId: String): VideoDetail {
        val video = videos.findByIdOrNull(videoId) ?: throw NotFoundException("Video not found")
        val isOwner = video.ownerId == userId
        // Un video que no está READY solo lo ve su dueño; para el resto es como si no existiera.
        if (video.status != VideoStatus.READY && !isOwner) throw NotFoundException("Video not found")
        return VideoDetail(video, favorites.existsByUserIdAndVideoId(userId, videoId), isOwner)
    }

    private fun findIdempotency(userId: String, key: String): IdempotencyKey? =
        idempotency.findByIdOrNull(IdempotencyKeyId(userId, key))

    private fun replay(record: IdempotencyKey, hash: String): CreateVideoResult {
        if (record.requestHash != hash) {
            throw ConflictException("Idempotency-Key was already used with a different request payload")
        }
        val video = videos.findByIdOrNull(record.videoId) ?: throw NotFoundException("Video not found")
        val upload = if (video.status == VideoStatus.PENDING) {
            storage.presignUpload(video.s3Key, video.contentType, video.sizeBytes)
        } else {
            null
        }
        return CreateVideoResult(video, upload, replayed = true)
    }

    private fun insertNew(userId: String, idempotencyKey: String, hash: String, command: CreateVideoCommand): Video {
        val id = UUID.randomUUID()
        val s3Key = "videos/$id/${sanitizeFileName(command.fileName)}"
        val now = Instant.now()
        val video = Video(
            id = id,
            ownerId = userId,
            title = command.title,
            description = command.description,
            fileName = command.fileName,
            contentType = command.contentType,
            sizeBytes = command.sizeBytes,
            s3Key = s3Key,
            cdnUrl = "${props.cdnBaseUrl.trimEnd('/')}/$s3Key",
            status = VideoStatus.PENDING,
            failureReason = null,
            views = 0,
            createdAt = now,
            updatedAt = now,
        )
        // saveAndFlush: el INSERT debe ejecutarse ya, porque idempotency_keys referencia a videos (FK)
        videos.saveAndFlush(video)
        idempotency.insert(userId, idempotencyKey, hash, id)
        return video
    }

    private fun validate(command: CreateVideoCommand): CreateVideoCommand {
        val contentType = command.contentType.trim().lowercase()
        if (contentType !in props.allowedContentTypes) {
            throw UnprocessableException(
                "Unsupported content type '$contentType'. Allowed: ${props.allowedContentTypes.sorted().joinToString()}"
            )
        }
        if (command.sizeBytes <= 0 || command.sizeBytes > props.maxVideoSizeBytes) {
            throw UnprocessableException("sizeBytes must be between 1 and ${props.maxVideoSizeBytes}")
        }
        return command.copy(
            title = command.title.trim(),
            description = command.description?.trim()?.ifBlank { null },
            contentType = contentType,
        )
    }

    companion object {
        const val MAX_IDEMPOTENCY_KEY_LENGTH = 200

        fun sanitizeFileName(fileName: String): String =
            fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(100)
    }
}
