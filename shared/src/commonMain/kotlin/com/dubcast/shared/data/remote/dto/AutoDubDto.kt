package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AutoDubSpec(
    val mediaType: String,
    val sourceLanguageCode: String,
    val targetLanguageCode: String,
    val numberOfSpeakers: Int = 1,
    val ttsModel: String? = null,
    /**
     * non-null 이면 BFF 가 본 jobId 의 render output 을 source 로 사용하고 multipart `file` 은 무시.
     * 클라이언트는 file part 자체를 보내지 않는 것을 권장. BFF 는 lastAccessedAt 을 자동 갱신해
     * TTL (2시간 sliding window) 을 연장.
     */
    val editedRenderJobId: String? = null
)

@Serializable
data class AutoDubJobResponse(
    val jobId: String
)

@Serializable
data class AutoDubStatusResponse(
    val jobId: String,
    val status: String,
    val progress: Int = 0,
    val progressReason: String? = null,
    val error: String? = null,
    val dubbedAudioUrl: String? = null,
    /** 영상+더빙 audio 가 ffmpeg mux 된 mp4 의 signed download URL — 미리보기용. */
    val dubbedVideoUrl: String? = null
)
