package com.videoplatform.service

import com.videoplatform.domain.entity.toSummary
import com.videoplatform.domain.exception.NotFoundException
import com.videoplatform.domain.model.FavoriteItem
import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.persistence.FavoriteRepository
import com.videoplatform.persistence.VideoRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FavoriteService(
    private val favorites: FavoriteRepository,
    private val videos: VideoRepository,
) {

    fun add(userId: String, videoId: UUID) {
        if (!videos.existsByIdAndStatus(videoId, VideoStatus.READY)) throw NotFoundException("Video not found")
        favorites.addIfAbsent(userId, videoId)
    }

    fun remove(userId: String, videoId: UUID) {
        favorites.removeIfPresent(userId, videoId)
    }

    fun list(userId: String, pageable: Pageable): Page<FavoriteItem> {
        val page = PageRequest.of(pageable.pageNumber, pageable.pageSize, NEWEST_FIRST)
        return favorites.findByUserIdAndVideoStatus(userId, VideoStatus.READY, page)
            .map { FavoriteItem(video = it.video.toSummary(), favoritedAt = it.createdAt) }
    }

    private companion object {
        val NEWEST_FIRST: Sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("videoId"))
    }
}
