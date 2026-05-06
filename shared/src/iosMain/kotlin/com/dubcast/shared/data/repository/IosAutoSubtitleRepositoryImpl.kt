package com.dubcast.shared.data.repository

import com.dubcast.shared.data.remote.api.BffApi
import com.dubcast.shared.data.remote.api.BinaryPart
import com.dubcast.shared.data.remote.dto.SubtitleSpec
import com.dubcast.shared.domain.repository.AutoSubtitleRepository
import com.dubcast.shared.domain.repository.AutoSubtitleStatus

class IosAutoSubtitleRepositoryImpl(
    private val api: BffApi,
    private val uploader: IosMediaJobUploader
) : AutoSubtitleRepository {

    override suspend fun submit(
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        targetLanguageCodes: List<String>,
        numberOfSpeakers: Int,
        editedRenderJobId: String?,
    ): Result<String> = runCatching {
        // editedRenderJobId 가 있으면 BFF 가 render output 을 source 로 — file 업로드 자체 생략.
        val part = if (editedRenderJobId == null) {
            uploader.loadAsBinaryPart(sourceUri, mediaType, "subtitle")
        } else null
        val spec = SubtitleSpec(
            mediaType = mediaType,
            sourceLanguageCode = sourceLanguageCode,
            targetLanguageCodes = targetLanguageCodes,
            numberOfSpeakers = numberOfSpeakers,
            editedRenderJobId = editedRenderJobId,
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
            mediaType = "AUDIO",
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
