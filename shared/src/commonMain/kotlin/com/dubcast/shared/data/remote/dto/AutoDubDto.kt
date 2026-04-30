package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AutoDubSpec(
    val mediaType: String,
    val sourceLanguageCode: String,
    val targetLanguageCode: String,
    val numberOfSpeakers: Int = 1,
    val ttsModel: String? = null
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
