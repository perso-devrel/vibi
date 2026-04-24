package com.example.dubcast.data.repository

import com.example.dubcast.data.remote.api.BffApiService
import com.example.dubcast.data.remote.dto.SubtitleSpec
import com.example.dubcast.domain.repository.AutoSubtitleRepository
import com.example.dubcast.domain.repository.AutoSubtitleStatus
import com.squareup.moshi.Moshi
import retrofit2.HttpException
import javax.inject.Inject

class AutoSubtitleRepositoryImpl @Inject constructor(
    private val apiService: BffApiService,
    moshi: Moshi,
    private val uploader: MediaJobUploader
) : AutoSubtitleRepository {

    private val specAdapter = moshi.adapter(SubtitleSpec::class.java)

    override suspend fun submit(
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        targetLanguageCode: String?,
        numberOfSpeakers: Int
    ): Result<String> = runCatching {
        val specJson = specAdapter.toJson(
            SubtitleSpec(
                mediaType = mediaType,
                sourceLanguageCode = sourceLanguageCode,
                targetLanguageCode = targetLanguageCode,
                numberOfSpeakers = numberOfSpeakers
            )
        )
        uploader.upload(
            sourceUri = sourceUri,
            mediaType = mediaType,
            prefix = "subtitle",
            specJson = specJson
        ) { filePart, specBody ->
            apiService.submitSubtitleJob(filePart, specBody).jobId
        }
    }

    override suspend fun pollStatus(jobId: String): Result<AutoSubtitleStatus> = runCatching {
        val response = apiService.getSubtitleStatus(jobId)
        when {
            response.status == STATUS_FAILED ->
                AutoSubtitleStatus.Failed(response.jobId, response.error ?: response.progressReason)

            response.status == STATUS_READY && response.originalSrtUrl != null ->
                AutoSubtitleStatus.Ready(
                    jobId = response.jobId,
                    originalSrtUrl = response.originalSrtUrl,
                    translatedSrtUrl = response.translatedSrtUrl
                )

            else -> AutoSubtitleStatus.Processing(
                jobId = response.jobId,
                progress = response.progress,
                progressReason = response.progressReason
            )
        }
    }

    override suspend fun fetchSrt(srtUrl: String): Result<String> = runCatching {
        val body = try {
            apiService.downloadSrt(srtUrl)
        } catch (e: HttpException) {
            // Token-expired refresh is the caller's job (it knows the jobId);
            // surface the 403 so the use-case can re-poll for a new URL.
            throw e
        }
        body.byteStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private companion object {
        const val STATUS_READY = "READY"
        const val STATUS_FAILED = "FAILED"
    }
}
