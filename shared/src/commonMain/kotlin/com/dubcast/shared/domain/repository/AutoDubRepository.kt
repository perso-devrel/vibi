package com.dubcast.shared.domain.repository

sealed class AutoDubJobStatus {
    abstract val jobId: String

    data class Processing(
        override val jobId: String,
        val progress: Int,
        val progressReason: String?
    ) : AutoDubJobStatus()

    data class Ready(
        override val jobId: String,
        val dubbedAudioUrl: String,
        /** 영상+더빙 audio mux 된 mp4 URL — null 이면 BFF mux 실패 또는 audio-only. */
        val dubbedVideoUrl: String? = null
    ) : AutoDubJobStatus()

    data class Failed(
        override val jobId: String,
        val reason: String?
    ) : AutoDubJobStatus()
}

interface AutoDubRepository {
    suspend fun submit(
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        targetLanguageCode: String,
        numberOfSpeakers: Int = 1,
        ttsModel: String? = null
    ): Result<String>

    suspend fun pollStatus(jobId: String): Result<AutoDubJobStatus>

    suspend fun downloadDubbedAudio(audioUrl: String, outputFileName: String): Result<String>

    /** dubbed video mp4 다운로드 — local mp4 path 반환. */
    suspend fun downloadDubbedVideo(videoUrl: String, outputFileName: String): Result<String>
}
