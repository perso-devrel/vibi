package com.vibi.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * BFF `/api/v2/separate` multipart `spec` 필드. 클라가 trim + audio extract 까지 끝낸 audio 를
 * file 파트로 보내고, spec 은 메타 (현재는 source language 만) 만 담는다. mediaType / trim window /
 * editedRenderJobId 등 옛 필드는 surface 에서 제거.
 */
@Serializable
data class SeparationSpec(
    val sourceLanguageCode: String = "auto",
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
    val error: String? = null,
    /**
     * READY 시 BFF 가 ffprobe 로 잰 stem FLAC 의 실측 길이(ms). TimelineViewModel 이
     * SeparationDirective.rangeEndMs 를 사용자 선택값이 아닌 실제 stem 길이로 보정하기 위해 사용.
     * 누락(서버 측정 실패) 시 클라이언트가 사용자 선택값을 그대로 사용 — 기존 동작 fallback.
     */
    val actualDurationMs: Long? = null
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
