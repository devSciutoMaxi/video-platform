package com.videoplatform.storage

import com.videoplatform.domain.model.PresignedUpload
import com.videoplatform.domain.model.StoredObject

interface VideoStorage {
    fun presignUpload(key: String, contentType: String, sizeBytes: Long): PresignedUpload

    fun head(key: String): StoredObject?
}
