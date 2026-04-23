package com.example.dubcast.domain.repository

import com.example.dubcast.domain.model.SeparationMediaType
import com.example.dubcast.domain.model.Stem

sealed class SeparationStatus {
    abstract val jobId: String

    data class Processing(
        override val jobId: String,
        val progress: Int,
        val progressReason: String?
    ) : SeparationStatus()

    data class Ready(
        override val jobId: String,
        val stems: List<Stem>
    ) : SeparationStatus()

    data class Consumed(
        override val jobId: String,
        val mixJobId: String
    ) : SeparationStatus()

    data class Failed(
        override val jobId: String,
        val progressReason: String?
    ) : SeparationStatus()
}

sealed class MixStatus {
    abstract val mixJobId: String

    data class Processing(
        override val mixJobId: String,
        val progress: Int
    ) : MixStatus()

    data class Completed(
        override val mixJobId: String,
        val downloadUrl: String
    ) : MixStatus()

    data class Failed(
        override val mixJobId: String
    ) : MixStatus()
}

data class StemSelection(
    val stemId: String,
    val volume: Float = 1.0f
)

interface AudioSeparationRepository {
    suspend fun startSeparation(
        sourceUri: String,
        mediaType: SeparationMediaType,
        numberOfSpeakers: Int,
        sourceLanguageCode: String = "auto",
        trimStartMs: Long? = null,
        trimEndMs: Long? = null
    ): Result<String>

    suspend fun pollStatus(jobId: String): Result<SeparationStatus>

    suspend fun downloadStem(stemUrl: String, outputFileName: String): Result<String>

    suspend fun requestMix(jobId: String, selections: List<StemSelection>): Result<String>

    suspend fun pollMixStatus(mixJobId: String): Result<MixStatus>

    /**
     * Download a finished mix. If [downloadUrl] has been signed-URL-expired
     * (HTTP 403), the repository re-polls [mixJobId] to obtain a fresh URL and
     * retries the download once.
     */
    suspend fun downloadMix(
        mixJobId: String,
        downloadUrl: String,
        outputFileName: String
    ): Result<String>
}
