package com.dubcast.shared.data.repository

import android.content.Context
import com.dubcast.shared.data.remote.api.BffApi
import com.dubcast.shared.data.remote.dto.AutoDubSpec
import com.dubcast.shared.domain.repository.AutoDubJobStatus
import com.dubcast.shared.domain.repository.AutoDubRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoDubRepositoryImpl(
    private val api: BffApi,
    private val uploader: MediaJobUploader,
    private val context: Context
) : AutoDubRepository {

    override suspend fun submit(
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        targetLanguageCode: String,
        numberOfSpeakers: Int,
        ttsModel: String?
    ): Result<String> = runCatching {
        val part = uploader.loadAsBinaryPart(sourceUri, mediaType, "autodub")
        val spec = AutoDubSpec(
            mediaType = mediaType,
            sourceLanguageCode = sourceLanguageCode,
            targetLanguageCode = targetLanguageCode,
            numberOfSpeakers = numberOfSpeakers,
            ttsModel = ttsModel
        )
        api.submitAutoDubJob(part, spec).jobId
    }

    override suspend fun pollStatus(jobId: String): Result<AutoDubJobStatus> = runCatching {
        val response = api.getAutoDubStatus(jobId)
        when {
            response.status == STATUS_FAILED ->
                AutoDubJobStatus.Failed(response.jobId, response.error ?: response.progressReason)

            response.status == STATUS_READY && response.dubbedAudioUrl != null ->
                AutoDubJobStatus.Ready(
                    jobId = response.jobId,
                    dubbedAudioUrl = response.dubbedAudioUrl,
                    dubbedVideoUrl = response.dubbedVideoUrl
                )

            else -> AutoDubJobStatus.Processing(
                jobId = response.jobId,
                progress = response.progress,
                progressReason = response.progressReason
            )
        }
    }

    override suspend fun downloadDubbedAudio(
        audioUrl: String,
        outputFileName: String
    ): Result<String> = downloadToFiles(audioUrl, outputFileName) { api.downloadDubbedAudio(it) }

    override suspend fun downloadDubbedVideo(
        videoUrl: String,
        outputFileName: String
    ): Result<String> = downloadToFiles(videoUrl, outputFileName) { api.downloadDubbedVideo(it) }

    private suspend fun downloadToFiles(
        url: String,
        outputFileName: String,
        useApi: suspend (String) -> ByteArray
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val bytes = useApi(url)
            val outputDir = File(context.filesDir, "auto_dubs").apply { if (!exists()) mkdirs() }
            val outputFile = File(outputDir, outputFileName)
            outputFile.outputStream().use { it.write(bytes) }
            outputFile.absolutePath
        }
    }

    private companion object {
        const val STATUS_READY = "READY"
        const val STATUS_FAILED = "FAILED"
    }
}
