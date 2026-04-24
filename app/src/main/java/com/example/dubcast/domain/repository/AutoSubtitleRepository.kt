package com.example.dubcast.domain.repository

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
        val translatedSrtUrl: String?
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
        targetLanguageCode: String?,
        numberOfSpeakers: Int = 1
    ): Result<String>

    suspend fun pollStatus(jobId: String): Result<AutoSubtitleStatus>

    /** Downloads SRT body as a UTF-8 string. */
    suspend fun fetchSrt(srtUrl: String): Result<String>
}
