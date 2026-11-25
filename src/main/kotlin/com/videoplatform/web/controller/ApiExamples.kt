package com.videoplatform.web.controller

/** Ejemplos de respuestas que se muestran en Swagger. Solo documentación: no se usan en runtime. */
internal object ApiExamples {

    const val CREATE_201 = """{
  "videoId": "3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11",
  "status": "PENDING",
  "upload": {
    "url": "http://localhost:4566/video-bucket/videos/3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11/demo.mp4?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=...",
    "method": "PUT",
    "headers": { "Content-Type": "video/mp4" },
    "expiresAt": "2026-11-23T21:45:00Z"
  }
}"""

    const val DETAIL_READY = """{
  "id": "3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11",
  "title": "Mi primer video",
  "description": "Demo del challenge",
  "status": "READY",
  "playbackUrl": "http://localhost:4566/video-bucket/videos/3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11/demo.mp4",
  "views": 42,
  "sizeBytes": 1048576,
  "contentType": "video/mp4",
  "ownerId": "user-1",
  "createdAt": "2026-11-23T21:30:00Z",
  "favorite": false,
  "failureReason": null
}"""

    const val DETAIL_PENDING = """{
  "id": "3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11",
  "title": "Mi primer video",
  "status": "PENDING",
  "playbackUrl": null,
  "views": 0,
  "ownerId": "user-1",
  "favorite": false,
  "failureReason": null
}"""

    const val DETAIL_FAILED = """{
  "id": "3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11",
  "status": "FAILED",
  "playbackUrl": null,
  "failureReason": "SIZE_MISMATCH"
}"""

    const val SEARCH_200 = """{
  "content": [
    {
      "id": "3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11",
      "title": "Mi primer video",
      "description": "Demo del challenge",
      "views": 42,
      "playbackUrl": "http://localhost:4566/video-bucket/videos/3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11/demo.mp4",
      "createdAt": "2026-11-23T21:30:00Z"
    }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}"""

    const val FAVORITES_200 = """{
  "content": [
    {
      "video": {
        "id": "3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11",
        "title": "Mi primer video",
        "description": "Demo del challenge",
        "views": 42,
        "playbackUrl": "http://localhost:4566/video-bucket/videos/3f0c2f0e-6d0b-4b8e-9d2e-1f6f3f7a1c11/demo.mp4",
        "createdAt": "2026-11-23T21:30:00Z"
      },
      "favoritedAt": "2026-11-23T21:50:00Z"
    }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}"""

    const val ERR_VALIDATION = """{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": ["title: must not be blank"]
}"""

    const val ERR_MISSING_HEADER = """{
  "code": "MISSING_HEADER",
  "message": "Missing required header: Idempotency-Key",
  "details": []
}"""

    const val ERR_UNPROCESSABLE = """{
  "code": "UNPROCESSABLE",
  "message": "Unsupported content type 'application/pdf'. Allowed: video/mp4, video/quicktime, video/webm",
  "details": []
}"""

    const val ERR_CONFLICT = """{
  "code": "CONFLICT",
  "message": "Idempotency-Key was already used with a different request payload",
  "details": []
}"""

    const val ERR_NOT_FOUND = """{
  "code": "NOT_FOUND",
  "message": "Video not found",
  "details": []
}"""

    const val ERR_BLANK_QUERY = """{
  "code": "INVALID_REQUEST",
  "message": "q must not be blank",
  "details": []
}"""
}
