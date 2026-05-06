package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SeparationSpec(
    val mediaType: String,
    val numberOfSpeakers: Int,
    val sourceLanguageCode: String,
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null,
    /**
     * non-null 이면 BFF 가 본 jobId 의 render output 을 source 로 사용하고 multipart `file` 은 무시.
     * 클라이언트는 file part 자체를 보내지 않는 것을 권장. BFF 는 lastAccessedAt 을 자동 갱신해
     * TTL (2시간 sliding window) 을 연장.
     */
    val editedRenderJobId: String? = null
)

@Serializable
data class SeparationJobResponse(
    val jobId: String
)

@Serializable
data class StemDto(
    val stemId: String,
    val label: String,
    val url: String
)

@Serializable
data class SeparationStatusResponse(
    val jobId: String,
    val status: String,
    val progress: Int = 0,
    val progressReason: String? = null,
    val stems: List<StemDto> = emptyList(),
    val mixJobId: String? = null,
    /** status=FAILED 시 reason. UI 에서 그대로 표시. */
    val error: String? = null
)

@Serializable
data class MixStemRequest(
    val stemId: String,
    val volume: Float
)

@Serializable
data class MixRequest(
    val stems: List<MixStemRequest>
)

@Serializable
data class MixJobResponse(
    val mixJobId: String
)

@Serializable
data class MixStatusResponse(
    val mixJobId: String,
    val status: String,
    val progress: Int = 0,
    val downloadUrl: String? = null,
    /** status=FAILED 시 reason. UI 에서 그대로 표시. */
    val error: String? = null
)
