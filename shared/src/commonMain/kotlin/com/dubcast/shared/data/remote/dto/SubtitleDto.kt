package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubtitleSpec(
    val mediaType: String,
    val sourceLanguageCode: String,
    /** 번역해야 할 언어들 — 빈 리스트면 STT 만 (originalSrt). */
    val targetLanguageCodes: List<String> = emptyList(),
    val numberOfSpeakers: Int = 1,
    /**
     * non-null 이면 BFF 가 본 jobId 의 render output 을 source 로 사용하고 multipart `file` 은 무시.
     * 클라이언트는 file part 자체를 보내지 않는 것을 권장. 보내도 디스크에 저장되지 않고 silently 폐기.
     * BFF 는 lastAccessedAt 을 자동 갱신해 TTL (2시간 sliding window) 을 연장.
     */
    val editedRenderJobId: String? = null
)

@Serializable
data class SubtitleJobResponse(
    val jobId: String
)

@Serializable
data class SubtitleStatusResponse(
    val jobId: String,
    val status: String,
    val progress: Int = 0,
    val progressReason: String? = null,
    val error: String? = null,
    val originalSrtUrl: String? = null,
    /** 언어 코드 → 번역된 SRT URL. */
    val translatedSrtUrlsByLang: Map<String, String> = emptyMap(),
    /** legacy 단일 필드 (첫 번역) — 호환용. */
    val translatedSrtUrl: String? = null
)
