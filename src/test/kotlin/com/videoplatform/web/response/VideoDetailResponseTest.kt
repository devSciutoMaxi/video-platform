package com.videoplatform.web.response

import com.videoplatform.domain.model.VideoStatus
import com.videoplatform.testVideo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VideoDetailResponseTest {

    private fun failed() = testVideo(status = VideoStatus.FAILED, failureReason = "SIZE_MISMATCH")

    @Test
    fun `la playbackUrl solo aparece cuando el video esta READY`() {
        val ready = VideoDetailResponse.from(testVideo(status = VideoStatus.READY), favorite = false, isOwner = false)
        val pending = VideoDetailResponse.from(testVideo(status = VideoStatus.PENDING), favorite = false, isOwner = true)

        assertThat(ready.playbackUrl).isNotNull()
        assertThat(pending.playbackUrl).isNull()
    }

    @Test
    fun `el motivo del fallo solo lo ve el dueño`() {
        assertThat(VideoDetailResponse.from(failed(), favorite = false, isOwner = true).failureReason)
            .isEqualTo("SIZE_MISMATCH")
        assertThat(VideoDetailResponse.from(failed(), favorite = false, isOwner = false).failureReason).isNull()
    }

    @Test
    fun `refleja si el usuario lo tiene en favoritos`() {
        val video = testVideo(status = VideoStatus.READY)

        assertThat(VideoDetailResponse.from(video, favorite = true, isOwner = false).favorite).isTrue()
        assertThat(VideoDetailResponse.from(video, favorite = false, isOwner = false).favorite).isFalse()
    }
}
