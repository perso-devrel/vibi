package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubtitleSpec(
    val mediaType: String,
    val sourceLanguageCode: String,
    /** 번역해야 할 언어들 — 빈 리스트면 STT 만 (originalSrt). */
    val targetLanguageCodes: List<String> = emptyList(),
    val numberOfSpeakers: Int = 1
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
