package com.dubcast.shared.data.repository

import com.dubcast.shared.data.remote.api.BffApi
import com.dubcast.shared.data.remote.api.BinaryPart
import com.dubcast.shared.data.remote.dto.SubtitleSpec
import com.dubcast.shared.domain.repository.AutoSubtitleRepository
import com.dubcast.shared.domain.repository.AutoSubtitleStatus

class AutoSubtitleRepositoryImpl(
    private val api: BffApi,
    private val uploader: MediaJobUploader
) : AutoSubtitleRepository {

    override suspend fun submit(
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        targetLanguageCodes: List<String>,
        numberOfSpeakers: Int
    ): Result<String> = runCatching {
        val part = uploader.loadAsBinaryPart(sourceUri, mediaType, "subtitle")
        val spec = SubtitleSpec(
            mediaType = mediaType,
            sourceLanguageCode = sourceLanguageCode,
            targetLanguageCodes = targetLanguageCodes,
            numberOfSpeakers = numberOfSpeakers
        )
        api.submitSubtitleJob(part, spec).jobId
    }

    override suspend fun regenerate(
        srtBytes: ByteArray,
        sourceLanguageCode: String,
        targetLanguageCodes: List<String>
    ): Result<String> = runCatching {
        val part = BinaryPart(
            fieldName = "file",
            filename = "edited.srt",
            bytes = srtBytes,
            contentType = "application/x-subrip"
        )
        val spec = SubtitleSpec(
            mediaType = "AUDIO", // 무시되지만 SubtitleSpec 필수 필드.
            // BFF SubtitleSpec.init 가 isNotBlank 요구. regenerate 흐름에서 sourceLang 미상이면 "auto" 송신.
            sourceLanguageCode = sourceLanguageCode.ifBlank { "auto" },
            targetLanguageCodes = targetLanguageCodes,
            numberOfSpeakers = 1
        )
        api.regenerateSubtitleJob(part, spec).jobId
    }

    override suspend fun pollStatus(jobId: String): Result<AutoSubtitleStatus> = runCatching {
        val response = api.getSubtitleStatus(jobId)
        when {
            response.status == STATUS_FAILED ->
                AutoSubtitleStatus.Failed(response.jobId, response.error ?: response.progressReason)

            response.status == STATUS_READY && response.originalSrtUrl != null ->
                AutoSubtitleStatus.Ready(
                    jobId = response.jobId,
                    originalSrtUrl = response.originalSrtUrl,
                    translatedSrtUrlsByLang = response.translatedSrtUrlsByLang
                )

            else -> AutoSubtitleStatus.Processing(
                jobId = response.jobId,
                progress = response.progress,
                progressReason = response.progressReason
            )
        }
    }

    override suspend fun fetchSrt(srtUrl: String): Result<String> = runCatching {
        api.downloadSrt(srtUrl).decodeToString()
    }

    private companion object {
        const val STATUS_READY = "READY"
        const val STATUS_FAILED = "FAILED"
    }
}
