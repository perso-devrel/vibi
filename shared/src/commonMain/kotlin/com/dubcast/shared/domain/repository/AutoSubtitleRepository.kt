package com.dubcast.shared.domain.repository

sealed class AutoSubtitleStatus {
    abstract val jobId: String

    data class Processing(
        override val jobId: String,
        val progress: Int,
        val progressReason: String?
    ) : AutoSubtitleStatus()

    data class Ready(
        override val jobId: String,
        val originalSrtUrl: String,
        /** 언어 코드 → 번역된 SRT URL. 1 STT + N 번역 결과. */
        val translatedSrtUrlsByLang: Map<String, String> = emptyMap()
    ) : AutoSubtitleStatus()

    data class Failed(
        override val jobId: String,
        val reason: String?
    ) : AutoSubtitleStatus()
}

interface AutoSubtitleRepository {
    suspend fun submit(
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        /** 빈 리스트면 STT 만 (originalSrt). N개 lang 이면 STT 1회 + Gemini 번역 N회. */
        targetLanguageCodes: List<String>,
        numberOfSpeakers: Int = 1
    ): Result<String>

    /**
     * 사용자가 수정한 SRT 를 source 로 다른 언어 자막 재생성. 영상 업로드 없이 Gemini 만.
     * @param srtBytes 수정된 SRT 본문 (UTF-8 바이트).
     * @param sourceLanguageCode 수정된 SRT 의 언어 코드 (ko/en/...). target 과 동일하면 번역 스킵.
     * @param targetLanguageCodes 재생성할 언어 코드들.
     * @return jobId — 기존 pollStatus / fetchSrt 로 진행 상태 추적·결과 다운로드.
     */
    suspend fun regenerate(
        srtBytes: ByteArray,
        sourceLanguageCode: String,
        targetLanguageCodes: List<String>
    ): Result<String>

    suspend fun pollStatus(jobId: String): Result<AutoSubtitleStatus>

    suspend fun fetchSrt(srtUrl: String): Result<String>
}
