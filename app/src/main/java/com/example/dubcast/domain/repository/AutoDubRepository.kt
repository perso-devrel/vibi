package com.example.dubcast.domain.repository

sealed class AutoDubJobStatus {
    abstract val jobId: String

    data class Processing(
        override val jobId: String,
        val progress: Int,
        val progressReason: String?
    ) : AutoDubJobStatus()

    data class Ready(
        override val jobId: String,
        val dubbedAudioUrl: String
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

    /** Streams the dubbed audio file to local storage and returns the absolute path. */
    suspend fun downloadDubbedAudio(audioUrl: String, outputFileName: String): Result<String>
}
