package com.videoplatform.service

import com.videoplatform.domain.entity.toSummary
import com.videoplatform.domain.exception.InvalidRequestException
import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.domain.model.VideoSummary
import com.videoplatform.persistence.VideoRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class SearchService(private val videos: VideoRepository) {

    fun search(query: String, pageable: Pageable): Page<VideoSummary> {
        val text = query.trim()
        if (text.isEmpty()) throw InvalidRequestException("q must not be blank")

        val page = PageRequest.of(pageable.pageNumber, pageable.pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        return videos.findByStatusAndTitleContainingIgnoreCase(VideoStatus.READY, text, page)
            .map { it.toSummary() }
    }
}
